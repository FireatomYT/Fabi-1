package dev.fireatom.FABI.commands.moderation;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

public class ModReportCmd extends SlashCommand {

	public ModReportCmd() {
		this.name = "modreport";
		this.path = "bot.moderation.modreport";
		this.children = new SlashCommand[]{
			new Setup(), new Delete()
		};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Setup extends SlashCommand {
		public Setup() {
			this.name = "setup";
			this.path = "bot.moderation.modreport.setup";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT),
				new OptionData(OptionType.INTEGER, "interval", lu.getText(path+".interval.help"), true)
					.setRequiredRange(5, 30)
					.addChoices(
						new Command.Choice(lu.getText(path+".weekly"), 7),
						new Command.Choice(lu.getText(path+".biweekly"), 14),
						new Command.Choice(lu.getText(path+".monthly"), 30)
					),
				new OptionData(OptionType.STRING, "roles", lu.getText(path+".roles.help"), true)
					.setMaxLength(200),
				new OptionData(OptionType.STRING, "first_report", lu.getText(path+".first_report.help"))
					.setRequiredLength(10, 16)
			);
		}

		// If time is not included - default to 3:00
		private final DateTimeFormatter DATE_TIME_FORMAT = new DateTimeFormatterBuilder()
			.appendPattern("dd/MM/yyyy")
			.optionalStart()
			.appendPattern(" HH:mm")
			.optionalEnd()
			.parseDefaulting(ChronoField.HOUR_OF_DAY, 3)
			.parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
			.toFormatter();

		@Override
		protected void execute(SlashCommandEvent event) {
			List<Role> roles = event.optMentions("roles").getRoles();
			if (roles.isEmpty() || roles.size()>4) {
				editError(event, path+".no_roles");
				return;
			}

			int interval = event.optInteger("interval", 7);

			LocalDateTime firstReport;
			if (event.hasOption("first_report")) {
				String input = event.optString("first_report");
				try {
					firstReport = LocalDateTime.parse(input, DATE_TIME_FORMAT);
				} catch (DateTimeParseException ex) {
					editError(event, path+".failed_parse", ex.getMessage());
					return;
				}
			} else {
				// Next monday OR first month day at 3:00 (server time)
				if (interval == 30)
					firstReport = LocalDateTime.now().with(TemporalAdjusters.firstDayOfNextMonth()).withHour(3);
				else
					firstReport = LocalDateTime.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).withHour(3);
			}
			if (firstReport.isBefore(LocalDateTime.now())) {
				editError(event, path+".wrong_date");
				return;
			}

			GuildChannel channel = event.optGuildChannel("channel");

			String roleIds = roles.stream()
				.map(Role::getId)
				.collect(Collectors.joining(";"));

			// Add to DB
			try {
				bot.getDBUtil().modReport.setup(
					event.getGuild().getIdLong(), channel.getIdLong(), roleIds,
					firstReport, interval
				);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "setup mod report");
				return;
			}
			// Reply
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done",
					TimeFormat.DATE_TIME_SHORT.format(firstReport), channel.getAsMention(),
					interval, roles.stream().map(Role::getAsMention).collect(Collectors.joining(", "))
				))
				.build());
		}
	}

	private class Delete extends SlashCommand {
		public Delete() {
			this.name = "delete";
			this.path = "bot.moderation.modreport.delete";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			try {
				bot.getDBUtil().modReport.removeGuild(event.getGuild().getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove mod report");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build());
		}
	}

}
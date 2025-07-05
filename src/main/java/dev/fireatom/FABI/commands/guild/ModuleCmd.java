package dev.fireatom.FABI.commands.guild;

import java.sql.SQLException;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.base.waiter.EventWaiter;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.Emote;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.jetbrains.annotations.NotNull;

public class ModuleCmd extends SlashCommand {
	
	private final EventWaiter waiter;
	
	public ModuleCmd() {
		this.name = "module";
		this.path = "bot.guild.module";
		this.children = new SlashCommand[]{
			new Show(), new Disable(), new Enable()
		};
		this.category = CmdCategory.GUILD;
		this.accessLevel = CmdAccessLevel.OWNER;
		this.waiter = App.getInstance().getEventWaiter();
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Show extends SlashCommand {
		public Show() {
			this.name = "show";
			this.path = "bot.guild.module.show";
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long guildId = event.getGuild().getIdLong();

			StringBuilder builder = new StringBuilder();
			EnumSet<CmdModule> disabled = getModules(guildId, false);
			for (CmdModule sModule : CmdModule.values()) {
				builder.append(format(lu.getGuildText(event, sModule.getPath()), disabled.contains(sModule)))
					.append("\n");
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".embed.title"))
				.setDescription(lu.getGuildText(event, path+".embed.value"))
				.addField(lu.getGuildText(event, path+".embed.field"), builder.toString(), false)
				.build());
		}

		@NotNull
		private String format(String sModule, boolean check) {
			return (check ? Emote.CROSS_C : Emote.CHECK_C).getEmote() + " | " + sModule;
		}
	}

	private class Disable extends SlashCommand {
		public Disable() {
			this.name = "disable";
			this.path = "bot.guild.module.disable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			InteractionHook hook = event.getHook();

			long guildId = event.getGuild().getIdLong();

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".embed_title"));

			EnumSet<CmdModule> enabled = getModules(guildId, true);
			if (enabled.isEmpty()) {
				embed.setDescription(lu.getGuildText(event, path+".none"))
					.setColor(Constants.COLOR_FAILURE);
				editEmbed(event, embed.build());
				return;
			}

			embed.setDescription(lu.getGuildText(event, path+".embed_value"));
			StringSelectMenu menu = StringSelectMenu.create("disable-module")
				.setPlaceholder(lu.getGuildText(event, path+".select"))
				.setRequiredRange(1, 1)
				.addOptions(enabled.stream()
					.map(sModule -> SelectOption.of(lu.getGuildText(event, sModule.getPath()), sModule.toString()))
					.toList()
				)
				.build();

			hook.editOriginalEmbeds(embed.build()).setActionRow(menu).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getComponentId().equals("disable-module") && e.getMessageId().equals(msg.getId()) && event.getUser().getIdLong() == e.getUser().getIdLong(),
				actionEvent -> {
					actionEvent.deferEdit().queue();
					CmdModule sModule = CmdModule.valueOf(actionEvent.getSelectedOptions().getFirst().getValue());
					if (bot.getDBUtil().getGuildSettings(guildId).isDisabled(sModule)) {
						hook.editOriginalEmbeds(bot.getEmbedUtil().getError(event, path+".already")).setComponents().queue();
						return;
					}
					// set new data
					final int newData = bot.getDBUtil().getGuildSettings(guildId).getModulesOff() + sModule.getValue();
					try {
						bot.getDBUtil().guildSettings.setModuleDisabled(guildId, newData);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "set disabled modules");
						return;
					}
					// Send reply
					hook.editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setTitle(lu.getGuildText(event, path+".done", lu.getGuildText(event, sModule.getPath())))
						.build()
					).setComponents().queue();
					// Log
					bot.getGuildLogger().botLogs.onModuleDisabled(event.getGuild(), event.getUser(), sModule);
				},
				30,
				TimeUnit.SECONDS,
				() -> hook.editOriginalComponents(
					ActionRow.of(menu.createCopy().setPlaceholder(lu.getGuildText(event, "errors.timed_out")).setDisabled(true).build())
				).queue()
			));
		}
	}

	private class Enable extends SlashCommand {
		public Enable() {
			this.name = "enable";
			this.path = "bot.guild.module.enable";
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			InteractionHook hook = event.getHook();

			long guildId = event.getGuild().getIdLong();

			EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".embed_title"));

			EnumSet<CmdModule> disabled = getModules(guildId, false);
			if (disabled.isEmpty()) {
				embed.setDescription(lu.getGuildText(event, path+".none"))
					.setColor(Constants.COLOR_FAILURE);
				editEmbed(event, embed.build());
				return;
			}

			embed.setDescription(lu.getGuildText(event, path+".embed_value"));
			StringSelectMenu menu = StringSelectMenu.create("enable-module")
				.setPlaceholder(lu.getGuildText(event, path+".select"))
				.setRequiredRange(1, 1)
				.addOptions(disabled.stream()
					.map(sModule -> SelectOption.of(lu.getGuildText(event, sModule.getPath()), sModule.toString()))
					.toList())
				.build();

			hook.editOriginalEmbeds(embed.build()).setActionRow(menu).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getComponentId().equals("enable-module") && e.getMessageId().equals(msg.getId()) && event.getUser().getIdLong() == e.getUser().getIdLong(),
				actionEvent -> actionEvent.deferEdit().queue(
					actionHook -> {
						CmdModule sModule = CmdModule.valueOf(actionEvent.getSelectedOptions().getFirst().getValue());
						if (!bot.getDBUtil().getGuildSettings(guildId).isDisabled(sModule)) {
							hook.editOriginalEmbeds(bot.getEmbedUtil().getError(event, path+".already")).setComponents().queue();
							return;
						}
						// set new data
						final int newData = bot.getDBUtil().getGuildSettings(guildId).getModulesOff() - sModule.getValue();
						try {
							bot.getDBUtil().guildSettings.setModuleDisabled(guildId, newData);
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "set enable module");
							return;
						}
						// Send reply
						hook.editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
							.setTitle(lu.getGuildText(event, path+".done", lu.getGuildText(event, sModule.getPath())))
							.build()
						).setComponents().queue();
						// Log
						bot.getGuildLogger().botLogs.onModuleEnabled(event.getGuild(), event.getUser(), sModule);
					}
				),
				10,
				TimeUnit.SECONDS,
				() -> hook.editOriginalComponents(
					ActionRow.of(menu.createCopy().setPlaceholder(lu.getGuildText(event, "errors.timed_out")).setDisabled(true).build())
				).queue()
			));
		}
	}

	private EnumSet<CmdModule> getModules(long guildId, boolean on) {
		EnumSet<CmdModule> disabled = bot.getDBUtil().getGuildSettings(guildId).getDisabledModules();
		if (on) {
			EnumSet<CmdModule> modules = EnumSet.allOf(CmdModule.class);
			modules.removeAll(disabled);
			return modules;
		} else
			return disabled;
	}

}

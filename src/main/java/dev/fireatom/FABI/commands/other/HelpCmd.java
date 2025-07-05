package dev.fireatom.FABI.commands.other;

import java.util.*;

import dev.fireatom.FABI.base.command.Category;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.Emote;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import dev.fireatom.FABI.utils.message.MessageUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class HelpCmd extends SlashCommand {

	public HelpCmd() {
		this.name = "help";
		this.path = "bot.help";
		this.options = List.of(
			new OptionData(OptionType.STRING, "category", lu.getText(path+".category.help"))
				.addChoice("Server", "guild")
				.addChoice("Owner", "owner")
				.addChoice("Webhook", "webhook")
				.addChoice("Moderation", "moderation")
				.addChoice("Verification", "verification")
				.addChoice("Ticketing", "ticketing")
				.addChoice("Voice", "voice")
				.addChoice("Roles", "roles")
				.addChoice("Games", "games")
				.addChoice("Leveling", "levels")
				.addChoice("Other", "other"),
			new OptionData(OptionType.STRING, "command", lu.getText(path+".command.help"), false, true)
				.setRequiredLength(3, 20),
			new OptionData(OptionType.BOOLEAN, "show", lu.getText(path+".show.help"))
		);
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		String findCmd = event.optString("command");

		if (findCmd != null) {
			sendCommandHelp(event, findCmd.split(" ")[0].toLowerCase());
		} else {
			String filCat = event.optString("category");
			sendHelp(event, filCat);
		}
	}

	private void sendCommandHelp(SlashCommandEvent event, String findCmd) {
		SlashCommand command = null;
		for (SlashCommand cmd : event.getClient().getSlashCommands()) {
			if (cmd.getName().equals(findCmd)) {
				command = cmd;
				break;
			}
		}

		if (command == null) {
			editError(event, "bot.help.command_info.no_command", "Requested: "+findCmd);
		} else {
			EmbedBuilder builder = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
				.setTitle(lu.getText(event, "bot.help.command_info.title", command.getName()))
				.setDescription(lu.getText(event, "bot.help.command_info.value",
					lu.getText(event, "bot.help.command_menu.categories."+command.getCategory().name()),
					MessageUtil.capitalize(command.getAccessLevel().getName()),
					command.isGuildOnly() ? Emote.CROSS_C.getEmote() : Emote.CHECK_C.getEmote(),
					Optional.ofNullable(command.getModule())
						.map(mod -> lu.getText(event, mod.getPath()))
						.orElse(Constants.NONE)
				))
				.addField(lu.getText(event, "bot.help.command_info.help_title"), lu.getText(event, command.getHelpPath()), false)
				.setFooter(lu.getText(event, "bot.help.command_info.usage_subvalue"));

			List<String> v = getUsageText(lu.getLocale(event), command);
			builder.addField(lu.getText(event, "bot.help.command_info.usage_title"), v.getFirst(), false);
			for (int i = 1; i < v.size(); i++) {
				builder.addField("", v.get(i), false);
			}
			editEmbed(event, builder.build());
		}
	}

	private List<String> getUsageText(DiscordLocale locale, SlashCommand command) {
		List<String> values = new ArrayList<>();
		StringBuilder builder = new StringBuilder();
		if (command.getChildren().length > 0) {
			String base = command.getName();
			for (SlashCommand child : command.getChildren()) {
				String text = lu.getLocalized(locale, "bot.help.command_info.usage_child")
					.formatted(
						base,
						lu.getLocalized(locale, child.getUsagePath()),
						lu.getLocalized(locale, child.getHelpPath())
					);
				if (builder.length() + text.length() > 1020) {
					values.add(builder.toString());
					builder = new StringBuilder(text);
				} else {
					builder.append(text);
				}
				builder.append("\n");
			}
		} else {
			builder.append(lu.getLocalized(locale, "bot.help.command_info.usage_value")
				.formatted(lu.getLocalized(locale, command.getUsagePath()))
			).append("\n");
		}
		values.add(builder.toString());
		return values;
	}

	private void sendHelp(SlashCommandEvent event, String filCat) {
		EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getText(event, "bot.help.command_menu.title"))
			.setDescription(lu.getText(event, "bot.help.command_menu.description.command_value"));

		Category category = null;
		String fieldTitle = "";
		StringBuilder fieldValue = new StringBuilder();
		List<SlashCommand> commands = (filCat == null ?
			event.getClient().getSlashCommands().stream() :
			event.getClient().getSlashCommands().stream().filter(cmd -> cmd.getCategory().name().contentEquals(filCat))
		)
			.sorted(Comparator.comparing(cmd -> cmd.getCategory().name()))
			.toList();

		for (SlashCommand command : commands) {
			if (command.getAccessLevel().isLowerThan(CmdAccessLevel.DEV) || bot.getCheckUtil().isBotOwner(event.getUser())) {
				if (!Objects.equals(category, command.getCategory())) {
					if (category != null) {
						builder.addField(fieldTitle, fieldValue.toString(), false);
					}
					category = command.getCategory();
					fieldTitle = lu.getText(event, "bot.help.command_menu.categories."+category.name());
					fieldValue = new StringBuilder();
				}
				fieldValue.append("`/%s` - %s\n".formatted(command.getName(), command.getDescriptionLocalization().get(lu.getLocale(event))));
			}
		}
		if (category != null) {
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}

		User owner = event.getJDA().getUserById(event.getClient().getOwnerIdLong());

		if (owner != null) {
			fieldTitle = lu.getText(event, "bot.help.command_menu.description.support_title");
			fieldValue = new StringBuilder()
				.append(lu.getText(event, "bot.help.command_menu.description.support_value", "@"+owner.getName()));
			builder.addField(fieldTitle, fieldValue.toString(), false);
		}

		editEmbed(event, builder.build());
	}
}

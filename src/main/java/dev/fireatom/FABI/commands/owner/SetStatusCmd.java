package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Activity.ActivityType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SetStatusCmd extends SlashCommand {
	public SetStatusCmd() {
		this.name = "setstatus";
		this.path = "bot.owner.setstatus";
		this.options = List.of(
			new OptionData(OptionType.STRING, "type", lu.getText(path+".type.help"), true)
				.addChoices(
					new Command.Choice("- Clear -", "clear"),
					new Command.Choice("- Custom -", "custom"),
					new Command.Choice("Playing", "playing"),
					new Command.Choice("Streaming", "streaming"),
					new Command.Choice("Listening", "listening"),
					new Command.Choice("Watching", "watching")
				),
			new OptionData(OptionType.STRING, "text", lu.getText(path+".text.help"), true)
				.setMaxLength(128),
			new OptionData(OptionType.STRING, "url", lu.getText(path+".url.help"))
				.setMaxLength(100)
		);
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		ActivityType type = parseType(event.optString("type"));
		if (type == null) {
			event.getJDA().getPresence().setActivity(null);
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".clear"))
				.build());
			return;
		}
		String text = event.optString("text");

		switch (type) {
			case PLAYING, LISTENING, WATCHING, CUSTOM_STATUS -> {
				event.getJDA().getPresence().setActivity(Activity.of(type, text));
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, path+".set", activityString(type), text))
					.build());
			}
			case STREAMING -> {
				String url = event.optString("url");
				if (!Activity.isValidStreamingUrl(url)) {
					editError(event, path+".invalid_url", url);
					return;
				}
				event.getJDA().getPresence().setActivity(Activity.of(type, text, url));
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, path+".set", activityString(type), text+"\n> URL: "+url ))
					.build());
			}
		}
	}

	private ActivityType parseType(@Nullable String type) {
		return switch (type) {
			case "playing" -> ActivityType.PLAYING;
			case "streaming" -> ActivityType.STREAMING;
			case "listening" -> ActivityType.LISTENING;
			case "watching" -> ActivityType.WATCHING;
			case "custom" -> ActivityType.CUSTOM_STATUS;
			default -> null;
		};
	}

	private String activityString(@Nullable ActivityType type) {
		return switch (type) {
			case PLAYING -> "Playing";
			case STREAMING -> "Streaming";
			case LISTENING -> "Listening";
			case WATCHING -> "Watching";
			case CUSTOM_STATUS -> "Custom Status";
			default -> null;
		};
	}
}

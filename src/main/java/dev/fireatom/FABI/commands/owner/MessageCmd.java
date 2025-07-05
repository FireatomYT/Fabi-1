package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import java.util.List;

public class MessageCmd extends SlashCommand {
	public MessageCmd() {
		this.name = "message";
		this.path = "bot.owner.message";
		this.options = List.of(
			new OptionData(OptionType.STRING, "channel_id", lu.getText(path+".channel_id.help"), true),
			new OptionData(OptionType.STRING, "content", lu.getText(path+".content.help"), true)
		);
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		String channelId = event.optString("channel_id");
		GuildMessageChannel channel = event.getJDA().getChannelById(GuildMessageChannel.class, channelId);
		if (channel == null) {
			editMsg(event, Constants.FAILURE+" Channel not found.");
			return;
		}

		channel.sendMessage(event.optString("content")).queue();
		editMsg(event, Constants.SUCCESS);
	}
}

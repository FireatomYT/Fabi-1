package dev.fireatom.FABI.commands.other;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.constants.CmdCategory;

public class PingCmd extends SlashCommand {
	
	public PingCmd() {
		this.name = "ping";
		this.path = "bot.other.ping";
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		editMsg(event, lu.getGuildText(event, path+".loading"));

		event.getJDA().getRestPing().queue(time -> {
			editMsg(event, lu.getGuildText(event, "bot.other.ping.info_full", event.getJDA().getGatewayPing(), time));
		});	
	}

}

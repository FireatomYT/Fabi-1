package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.ExitCodes;
import dev.fireatom.FABI.objects.constants.CmdCategory;

import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ShutdownCmd extends SlashCommand {
	public ShutdownCmd() {
		this.name = "shutdown";
		this.path = "bot.owner.shutdown";
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.guildOnly = false;
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "now", lu.getText(path+".now.help"))
		);
	}
	
	@Override
	protected void execute(SlashCommandEvent event) {
		ExitCodes exitCode = ExitCodes.fromInt(event.optInteger("status", 0));
		if (event.optBoolean("now", false)) {
			// Reply
			event.getHook().editOriginal("Shutting down...")
				.submit()
				.whenComplete((v,e) -> bot.shutdown(exitCode));
			// Update presence
			event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Shutting down..."));
		} else {
			// Reply
			event.getHook().editOriginal("Shutting down in 3 minutes.")
				.submit()
				.whenComplete((v,e) -> bot.shutdown(exitCode));
			// Update presence
			event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Preparing to shut down"));

			bot.scheduleShutdown(Instant.now().plus(3, ChronoUnit.MINUTES), exitCode);
		}
	}
}

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

public class RestartCmd extends SlashCommand {
	public RestartCmd() {
		this.name = "restart";
		this.path = "bot.owner.restart";
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
			event.getHook().editOriginal("Restarting...")
				.submit()
				.whenComplete((v,e) -> bot.shutdown(exitCode));
			// Update presence
			event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Restarting..."));
		} else {
			// Reply
			event.getHook().editOriginal("Restarting in 3 minutes.")
				.submit()
				.whenComplete((v,e) -> bot.shutdown(exitCode));
			// Update presence
			event.getJDA().getPresence().setPresence(OnlineStatus.IDLE, Activity.competing("Preparing to restart"));

			bot.scheduleShutdown(Instant.now().plus(3, ChronoUnit.MINUTES), exitCode);
		}
	}
}

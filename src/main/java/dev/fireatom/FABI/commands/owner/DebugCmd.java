package dev.fireatom.FABI.commands.owner;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.constants.CmdCategory;

import java.io.File;
import java.util.List;
import java.util.Optional;

public class DebugCmd extends SlashCommand {
	public DebugCmd() {
		this.name = "debug";
		this.path = "bot.owner.debug";
		this.options = List.of(
			new OptionData(OptionType.BOOLEAN, "debug_logs", lu.getText(path+".debug_logs.help")),
			new OptionData(OptionType.STRING, "date", lu.getText(path+".date.help")).setRequiredLength(10,10)
		);
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		if (event.optBoolean("debug_logs", false)) {
			event.getHook().editOriginalAttachments(FileUpload.fromData(new File("./logs/debug.log"))).queue();
		} else {
			String date = Optional.ofNullable(event.optString("date"))
				.map(s->"."+s)
				.orElse("");
			File file = new File("./logs/votl%s.log".formatted(date));
			if (!file.exists()) {
				editErrorOther(event, "No file by name: "+file.getName());
			} else {
				event.getHook().editOriginalAttachments(FileUpload.fromData(file)).queue();
			}
		}
	}
}
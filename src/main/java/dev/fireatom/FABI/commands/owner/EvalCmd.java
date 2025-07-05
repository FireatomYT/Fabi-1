package dev.fireatom.FABI.commands.owner;

import java.util.List;
import java.util.Map;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import org.codehaus.groovy.runtime.powerassert.PowerAssertionError;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

public class EvalCmd extends SlashCommand {
	public EvalCmd() {
		this.name = "eval";
		this.path = "bot.owner.eval";
		this.options = List.of(
			new OptionData(OptionType.STRING, "code", lu.getText(path+".code.help"), true) 
		);
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		String args = event.optString("code");
		if (args == null) {
			return;
		}
		args = args.trim();
		if (args.startsWith("```") && args.endsWith("```")) {
			args = args.substring(3, args.length() - 3);
			if (args.startsWith("java")) {
				args = args.substring(4);
			}
		}

		Map<String, Object> variables = Map.of(
			"bot", bot,
			"event", event,
			"jda", event.getJDA(),
			"guild", (event.isFromGuild() ? event.getGuild() : "null"),
			"client", event.getClient()
		);

		Binding binding = new Binding(variables);
		GroovyShell shell = new GroovyShell(binding);

		long startTime = System.currentTimeMillis();

		try {
			String reply = String.valueOf(shell.evaluate(args));

			editEmbed(event, formatEvalEmbed(event, args, reply,
				lu.getText(event, "bot.owner.eval.time",
					String.valueOf(System.currentTimeMillis() - startTime))
	 			, true
			));
		} catch (PowerAssertionError | Exception ex) {
			editEmbed(event, formatEvalEmbed(event, args, ex.getMessage(),
				lu.getText(event, "bot.owner.eval.time",
					String.valueOf(System.currentTimeMillis() - startTime))
				, false
			));
		}
	}

	private MessageEmbed formatEvalEmbed(SlashCommandEvent event, String input, String output, String footer, boolean success) {
		EmbedBuilder embed = bot.getEmbedUtil().getEmbed()
			.setColor(success ? Constants.COLOR_SUCCESS : Constants.COLOR_FAILURE)
			.addField(lu.getText(event, "bot.owner.eval.input"),
				"```groovy\n"+MessageUtil.limitString(input, 1000)+"\n```",
				false)
			.addField(lu.getText(event, "bot.owner.eval.output"),
				"```groovy\n"+MessageUtil.limitString(output, 1000)+"\n```",
				false)
			.setFooter(footer, null);

		return embed.build();
	}
}
package dev.fireatom.FABI.commands.owner;

import java.sql.SQLException;
import java.util.List;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.constants.CmdCategory;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class ForceAccessCmd extends SlashCommand {
	public ForceAccessCmd() {
		this.name = "forceaccess";
		this.path = "bot.owner.forceaccess";
		this.category = CmdCategory.OWNER;
		this.accessLevel = CmdAccessLevel.DEV;
		this.options = List.of(
			new OptionData(OptionType.STRING, "server", lu.getText(path+".server.help"), true),
			new OptionData(OptionType.INTEGER, "type", lu.getText(path+".type.help"), true)
				.addChoice("Role", 1)
				.addChoice("User", 2),
			new OptionData(OptionType.STRING, "target", lu.getText(path+".target.help"), true).setMaxLength(30),
			new OptionData(OptionType.INTEGER, "access_level", lu.getText(path+".access_level.help"), true)
				.addChoice("- Remove -", CmdAccessLevel.ALL.getLevel())
				.addChoice("Ban exemption", CmdAccessLevel.EXEMPT.getLevel())
				.addChoice("Helper", CmdAccessLevel.HELPER.getLevel())
				.addChoice("Moderator", CmdAccessLevel.MOD.getLevel())
				.addChoice("Operator", CmdAccessLevel.OPERATOR.getLevel())
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getJDA().getGuildById(event.optString("server"));
		if (guild == null) {
			editError(event, path+".no_guild");
			return;
		}

		CmdAccessLevel level = CmdAccessLevel.byLevel(event.optInteger("access_level"));
		long targetId = event.optLong("target");
		try {
			if (event.optInteger("type") == 1) {
				// Target is role
				if (level.equals(CmdAccessLevel.ALL))
					bot.getDBUtil().access.removeRole(guild.getIdLong(), targetId);
				else
					bot.getDBUtil().access.addRole(guild.getIdLong(), targetId, level);
				editMsg(event, lu.getGuildText(event, path+".done", level.getName(), "Role `"+targetId+"`"));
			} else {
				// Target is user
				if (level.equals(CmdAccessLevel.ALL))
					bot.getDBUtil().access.removeUser(guild.getIdLong(), targetId);
				else
					bot.getDBUtil().access.addOperator(guild.getIdLong(), targetId);
				editMsg(event, lu.getGuildText(event, path+".done", level.getName(), "User `"+targetId+"`"));
			}
		} catch (SQLException ex) {
			editErrorDatabase(event, ex, "force access");
		}
	}
}

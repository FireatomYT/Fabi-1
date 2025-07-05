package dev.fireatom.FABI.commands.strike;

import java.sql.SQLException;
import java.util.List;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.internal.utils.tuple.Pair;

public class ClearStrikesCmd extends SlashCommand {

	public ClearStrikesCmd() {
		this.name = "clearstrikes";
		this.path = "bot.moderation.clearstrikes";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true)
		);
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.STRIKES;
		this.accessLevel = CmdAccessLevel.ADMIN;
		addMiddlewares(
			"throttle:guild,1,10"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		User tu = event.optUser("user");
		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (tu.isBot()) {
			editError(event, path+".is_bot");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		Pair<Integer, String> strikeData = bot.getDBUtil().strikes.getData(guildId, tu.getIdLong());
		if (strikeData == null) {
			editEmbed(event, bot.getEmbedUtil().getEmbed().setDescription(lu.getGuildText(event, path+".no_strikes")).build());
			return;
		}
		int activeCount = strikeData.getLeft();
		try {
			// Clear strike DB
			bot.getDBUtil().strikes.removeGuildUser(guildId, tu.getIdLong());
			// Set all strikes cases inactive
			bot.getDBUtil().cases.setInactiveStrikeCases(guildId, tu.getIdLong());
		} catch (SQLException ex) {
			editErrorDatabase(event, ex, "clear user strikes");
			return;
		}

		// Log
		bot.getGuildLogger().mod.onStrikesCleared(event.getGuild(), tu, event.getUser());
		// Reply
		editEmbed(event, bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, path+".done", activeCount, tu.getName()))
			.build()
		);
	}

}

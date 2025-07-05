package dev.fireatom.FABI.commands.moderation;

import java.sql.SQLException;
import java.util.List;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.objects.constants.Limits;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class UnbanCmd extends SlashCommand {
	
	public UnbanCmd() {
		this.name = "unban";
		this.path = "bot.moderation.unban";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
				.setMaxLength(Limits.REASON_CHARS)
		);
		this.botPermissions = new Permission[]{Permission.BAN_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		addMiddlewares(
			"throttle:guild,1,10"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = event.getGuild();
		User tu = event.optUser("user");

		if (tu == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tu) || event.getJDA().getSelfUser().equals(tu)) {
			editError(event, path+".not_self");
			return;
		}

		// Remove active ban log
		CaseData banData = bot.getDBUtil().cases.getMemberActive(tu.getIdLong(), guild.getIdLong(), CaseType.BAN);
		if (banData != null) {
			ignoreExc(() -> bot.getDBUtil().cases.setInactive(banData.getRowId()));
		}

		guild.retrieveBan(tu).queue(ban -> {
			// Check if in blacklist
			for (int groupId : bot.getDBUtil().group.getGuildGroups(guild.getIdLong())) {
				// Check every group this server is part of for if user is blacklisted
				if (bot.getDBUtil().serverBlacklist.inGroupUser(groupId, tu.getIdLong())) {
					if (bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
						// User is Operator+, remove blacklist
						try {
							bot.getDBUtil().serverBlacklist.removeUser(groupId, tu.getIdLong());
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "Failed to remove user from blacklist.");
							return;
						}
						bot.getGuildLogger().mod.onBlacklistRemoved(event.getUser(), tu, groupId);
					} else {
						// User is not Operator+, reject unban
						editError(event, path+".blacklisted", "Group ID : "+groupId);
						return;
					}
				}
			}
			Member mod = event.getMember();
			final String reason = event.optString("reason", lu.getGuildText(event, path+".no_reason"));

			// perform unban
			guild.unban(tu).reason(reason).queue(done -> {
				// add info to db
				CaseData unbanData;
				try {
					unbanData = bot.getDBUtil().cases.add(
						CaseType.UNBAN, tu.getIdLong(), tu.getName(),
						mod.getIdLong(), mod.getUser().getName(),
						guild.getIdLong(), reason, null
					);
				} catch (Exception ex) {
					editErrorDatabase(event, ex, "Failed to create new case.");
					return;
				}

				// log unban
				bot.getGuildLogger().mod.onNewCase(guild, tu, unbanData, banData != null ? banData.getReason() : ban.getReason()).thenAccept(logUrl -> {
					// reply and ask for unban sync
					event.getHook().editOriginalEmbeds(
						bot.getModerationUtil().actionEmbed(lu.getLocale(event), unbanData.getLocalIdInt(),
							path+".success", tu, mod.getUser(), reason, logUrl)
					).setActionRow(
						Button.primary("sync_unban:"+tu.getId(), "Sync unban").withEmoji(Emoji.fromUnicode("🆑"))
					).queue();
				});
			}, failure -> {
				editErrorOther(event, "Failed to unban user.\n> "+failure.getMessage());
			});
		},
		failure -> {
			// reply and ask for unban sync
			event.getHook().editOriginalEmbeds(
				bot.getEmbedUtil().getEmbed(Constants.COLOR_FAILURE)
					.setDescription(lu.getGuildText(event, path+".no_ban"))
					.build()
			).setActionRow(
				Button.primary("sync_unban:"+tu.getId(), "Sync unban").withEmoji(Emoji.fromUnicode("🆑"))
			).queue();
		});
	}

}

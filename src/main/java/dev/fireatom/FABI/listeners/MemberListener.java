package dev.fireatom.FABI.listeners;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.objects.logs.LogType;
import dev.fireatom.FABI.utils.database.DBUtil;

import dev.fireatom.FABI.utils.database.managers.CaseManager;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

public class MemberListener extends ListenerAdapter {

	private final Logger log = (Logger) LoggerFactory.getLogger(MemberListener.class);

	private final App bot;
	private final DBUtil db;

	public MemberListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}
	
	@Override
	public void onGuildMemberJoin(@NotNull GuildMemberJoinEvent event) {
		// Log
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			bot.getGuildLogger().member.onJoined(event.getMember());
		}

		long userId = event.getUser().getIdLong();
		Guild guild = event.getGuild();
		long guildId = guild.getIdLong();
		// Check for persistent role
		try {
			List<Role> roles = new ArrayList<>();
			for (Long roleId : db.persistent.getUserRoles(guildId, userId)) {
				Role role = guild.getRoleById(roleId);
				if (role == null) {
					// Role is deleted
					db.persistent.removeRole(guildId, roleId);
					continue;
				}
				roles.add(role);
			}
			if (!roles.isEmpty()) {
				List<Role> newRoles = new ArrayList<>(event.getMember().getRoles());
				newRoles.addAll(roles);
				guild.modifyMemberRoles(event.getMember(), newRoles).queueAfter(3, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
		} catch (Exception e) {
			log.warn("Failed to assign persistent roles for {} @ {}\n{}", userId, guildId, e.getMessage());
		}

		// Check for active mute - then give timeout
		CaseManager.CaseData caseData = db.cases.getMemberActive(userId, guildId, CaseType.MUTE);
		if (caseData != null) {
			Instant timeEnd = caseData.getTimeEnd();
			if (timeEnd != null && timeEnd.isAfter(Instant.now())) {
				event.getMember().timeoutUntil(timeEnd)
					.reason("Active mute (#%s): %s".formatted(caseData.getLocalId(), caseData.getReason()))
					.queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
			}
		}
	}
	
	@Override
	public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
		// Log
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			event.getGuild().retrieveAuditLogs()
				.type(ActionType.KICK)
				.limit(1)
				.queue(list -> {
					if (!list.isEmpty()) {
						AuditLogEntry entry = list.getFirst();
						if (!entry.getUser().equals(event.getJDA().getSelfUser()) && entry.getTargetIdLong() == event.getUser().getIdLong()
							&& entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
							bot.getGuildLogger().mod.onUserKick(entry, event.getUser());
						}
					}
					bot.getGuildLogger().member.onLeft(event.getGuild(), event.getMember(), event.getUser());
				},
				failure -> {
					log.warn("Unable to retrieve audit log for member kick.", failure);
					bot.getGuildLogger().member.onLeft(event.getGuild(), event.getMember(), event.getUser());
				});
		}

		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();
		// Add persistent roles
		if (event.getMember() != null) {
			try {
				List<Role> roles = event.getMember().getRoles();
				if (!roles.isEmpty()) {
					List<Long> persistentRoleIds = db.persistent.getRoles(guildId);
					if (!persistentRoleIds.isEmpty()) {
						List<Long> common = new ArrayList<>(roles.stream().map(Role::getIdLong).toList());
						common.retainAll(persistentRoleIds);
						if (!common.isEmpty()) {
							db.persistent.addUser(guildId, userId, common);
						}
					}
				}
			} catch (Exception e) {
				log.warn("Failed to save persistent roles for {} @ {}\n{}", userId, guildId, e.getMessage());
			}
		}

		// When user leaves guild, check if there are any records in DB that would be better to remove.
		// This does not consider clearing User DB, when bot leaves guild.
		try {
			db.access.removeUser(guildId, userId);
			db.user.remove(event.getUser().getIdLong());
		} catch (SQLException ignored) {}

		if (db.getTicketSettings(event.getGuild()).autocloseLeftEnabled()) {
			db.tickets.getOpenedChannel(userId, guildId).forEach(channelId -> {
				try {
					db.tickets.closeTicket(Instant.now(), channelId, "Ticket's author left the server");
				} catch (SQLException ignored) {}
				GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
				if (channel != null) channel.delete().reason("Author left").queue();
			});
		}
	}

	@Override
	public void onGuildMemberUpdateNickname(@NotNull GuildMemberUpdateNicknameEvent event) {
		if (db.getLogSettings(event.getGuild()).enabled(LogType.MEMBER)) {
			bot.getGuildLogger().member.onNickChange(event.getMember(), event.getOldValue(), event.getNewValue());
		}
	}
	
}
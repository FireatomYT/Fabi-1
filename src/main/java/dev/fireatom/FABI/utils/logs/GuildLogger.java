package dev.fireatom.FABI.utils.logs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.ExpType;
import dev.fireatom.FABI.objects.logs.LogType;
import dev.fireatom.FABI.objects.logs.MessageData;
import dev.fireatom.FABI.utils.CaseProofUtil;
import dev.fireatom.FABI.utils.database.DBUtil;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;

import dev.fireatom.FABI.utils.encoding.EncodingUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.audit.AuditLogKey;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.utils.AttachmentProxy;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.internal.requests.IncomingWebhookClientImpl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public class GuildLogger {

	private final Logger log = (Logger) LoggerFactory.getLogger(GuildLogger.class);

	private final JDA JDA;
	private final DBUtil db;
	private final LogEmbedUtil logUtil;
	private final WebhookLogUtil webhookUtil;

	public final ModerationLogs mod =	new ModerationLogs();
	public final RoleLogs role =		new RoleLogs();
	public final GroupLogs group =		new GroupLogs();
	public final TicketLogs ticket =	new TicketLogs();
	public final ServerLogs server =	new ServerLogs();
	public final ChannelLogs channel =	new ChannelLogs();
	public final MemberLogs member =	new MemberLogs();
	public final MessageLogs message =	new MessageLogs();
	public final VoiceLogs voice =		new VoiceLogs();
	public final LevelLogs level =		new LevelLogs();
	public final BotLogs botLogs =		new BotLogs();

	public GuildLogger(App bot, LogEmbedUtil logEmbedUtil) {
		this.JDA = bot.JDA;
		this.db = bot.getDBUtil();
		this.logUtil = logEmbedUtil;
		this.webhookUtil = new WebhookLogUtil(db);
	}

	private IncomingWebhookClientImpl getWebhookClient(LogType type, Guild guild) {
		return webhookUtil.getWebhookClient(guild, type);
	}

	private void sendLog(Guild guild, LogType type, Supplier<MessageEmbed> embedSupplier) {
		webhookUtil.sendMessageEmbed(guild, type, embedSupplier);
	}

	private CompletableFuture<String> submitLog(@NotNull IncomingWebhookClientImpl webhookClient, MessageEmbed embed) {
		return webhookClient.sendMessageEmbeds(embed).submit()
			.exceptionally(ex -> null)
			.thenApply(msg -> msg==null ? null : msg.getJumpUrl());
	}

	private CompletableFuture<String> submitLog(@NotNull IncomingWebhookClientImpl webhookClient, MessageEmbed embed, CaseProofUtil.ProofData proofData) {
		try (final InputStream is = new AttachmentProxy(proofData.proxyUrl).download().join()) {
			return webhookClient.sendMessageEmbeds(embed).addFiles(FileUpload.fromData(is.readAllBytes(), proofData.fileName)).submit()
				.exceptionally(ex -> null)
				.thenApply(msg -> msg==null ? null : msg.getJumpUrl());
		} catch (IOException e) {
			log.error("Exception at log submission.", e);
			return submitLog(webhookClient, embed);
		}
	}

	// Moderation actions
	public class ModerationLogs {
		private final LogType type = LogType.MODERATION;

		public CompletableFuture<String> onNewCase(Guild guild, User target, CaseData caseData) {
			return onNewCase(guild, target, caseData, null, null);
		}

		public CompletableFuture<String> onNewCase(Guild guild, User target, CaseData caseData, String optionalData) {
			return onNewCase(guild, target, caseData, null, optionalData);
		}

		public CompletableFuture<String> onNewCase(Guild guild, User target, CaseData caseData, CaseProofUtil.ProofData proofData) {
			return onNewCase(guild, target, caseData, proofData, null);
		}
		
		public CompletableFuture<String> onNewCase(Guild guild, User target, @NotNull CaseData caseData, @Nullable CaseProofUtil.ProofData proofData, String optionalData) {
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return CompletableFuture.completedFuture(null);

			String proofFileName = proofData==null ? null : proofData.setFileName(caseData.getRowId());
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			MessageEmbed embed = switch (caseData.getType()) {
				case BAN ->
					logUtil.banEmbed(locale, caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case UNBAN ->
					logUtil.unbanEmbed(locale, caseData, optionalData);
				case MUTE ->
					logUtil.muteEmbed(locale, caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case UNMUTE ->
					logUtil.unmuteEmbed(locale, caseData, target.getEffectiveAvatarUrl(), optionalData);
				case KICK ->
					logUtil.kickEmbed(locale, caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case STRIKE_1, STRIKE_2, STRIKE_3 ->
					logUtil.strikeEmbed(locale, caseData, target.getEffectiveAvatarUrl(), proofFileName);
				case BLACKLIST ->
					null; // TODO
				case GAME_STRIKE ->
					logUtil.gameStrikeEmbed(locale, caseData, target.getEffectiveAvatarUrl(), proofFileName, optionalData);
			};
			if (embed == null) return CompletableFuture.completedFuture(null);
			return proofData==null ? submitLog(client, embed) : submitLog(client, embed, proofData);
		}

		public void onStrikesCleared(Guild guild, User target, User mod) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.strikesClearedEmbed(locale, target.getName(), target.getIdLong(), mod.getIdLong()));
		}

		public void onStrikeDeleted(Guild guild, User target, User mod, int caseLocalId, int deletedAmount, int maxAmount) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.strikeDeletedEmbed(locale, target.getName(), target.getIdLong(), mod.getIdLong(), caseLocalId, deletedAmount, maxAmount));
		}

		public void onAutoUnban(CaseData caseData, Guild guild) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.autoUnbanEmbed(locale, caseData));
		}

		public void onChangeReason(Guild guild, CaseData caseData, Member moderator, String newReason) {
			if (caseData == null) {
				log.warn("Unknown case provided with interaction, guild: {}", guild.getName());
				return;
			}

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.reasonChangedEmbed(locale, caseData, moderator.getIdLong(), newReason));
		}

		public void onChangeDuration(Guild guild, CaseData caseData, Member moderator, String newTime) {
			if (caseData == null) {
				log.warn("Unknown case provided with interaction, guild: {}", guild.getName());
				return;
			}

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.durationChangedEmbed(locale, caseData, moderator.getIdLong(), newTime));
		}

		public void onHelperSyncBan(int groupId, Guild guild, User target, String reason, int success, int max) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.helperBanEmbed(locale, groupId, target, reason, success, max));
		}

		public void onHelperSyncUnban(int groupId, Guild guild, User target, String reason, int success, int max) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.helperUnbanEmbed(locale, groupId, target, reason, success, max));
		}

		public void onHelperSyncKick(int groupId, Guild guild, User target, String reason, int success, int max) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.helperKickEmbed(locale, groupId, target, reason, success, max));
		}

		public void onBlacklistAdded(User mod, User target, List<Integer> groupIds) {
			for (int groupId : groupIds) {
				final String groupInfo = "%s (#%d)".formatted(db.group.getName(groupId), groupId);
				Guild master = JDA.getGuildById(db.group.getOwner(groupId));
				final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(master);
				sendLog(master, type, () -> logUtil.blacklistAddedEmbed(locale, mod, target, groupInfo));
			}
		}

		public void onBlacklistRemoved(User mod, User target, int groupId) {
			final String groupInfo = "%s (#%d)".formatted(db.group.getName(groupId), groupId);
			Guild master = JDA.getGuildById(db.group.getOwner(groupId));

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(master);
			sendLog(master, type, () -> logUtil.blacklistRemovedEmbed(locale, mod, target, groupInfo));
		}

		public void onUserBan(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.userBanEmbed(locale, target, entry.getReason(), modId));
		}

		public void onUserUnban(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.userUnbanEmbed(locale, target, entry.getReason(), modId));
		}

		public void onUserKick(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.userKickEmbed(locale, target, entry.getReason(), modId));
		}

		public void onUserTimeoutUpdated(AuditLogEntry entry, User target, OffsetDateTime until) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.userTimeoutUpdateEmbed(locale, target, entry.getReason(), modId, until));
		}

		public void onUserTimeoutRemoved(AuditLogEntry entry, User target) {
			final Guild guild = entry.getGuild();
			final long modId = entry.getUserIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.userTimeoutRemoveEmbed(locale, target, entry.getReason(), modId));
		}

		public void onMessagePurge(User mod, User target, int msgCount, GuildChannel channel) {
			final Guild guild = channel.getGuild();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.messagePurge(locale, mod, target, msgCount, channel));
		}
	}

	// Roles actions
	public class RoleLogs {
		private final LogType type = LogType.ROLE;

		public void onApproved(Member member, Member admin, Guild guild, List<Role> roles, int ticketId) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.rolesApprovedEmbed(locale, ticketId, member.getIdLong(),
				roles.stream().map(Role::getAsMention).collect(Collectors.joining(" ")), admin.getIdLong()));
		}

		public void onRoleAdded(Guild guild, User mod, User target, Role role) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.roleAddedEmbed(locale, mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), role.getAsMention()));
		}

		public void onRolesAdded(Guild guild, User mod, User target, String rolesAdded) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.rolesAddedEmbed(locale, mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), rolesAdded));
		}

		public void onRoleRemoved(Guild guild, User mod, User target, Role role) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.roleRemovedEmbed(locale, mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), role.getAsMention()));
		}

		public void onRolesRemoved(Guild guild, User mod, User target, String rolesRemoved) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.rolesRemovedEmbed(locale, mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), rolesRemoved));
		}

		public void onRoleRemovedAll(Guild guild, User mod, Role role) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.roleRemovedAllEmbed(locale, mod.getIdLong(), role.getIdLong()));
		}

		public void onRolesModified(Guild guild, User mod, User target, String rolesModified) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.rolesModifiedEmbed(locale, mod.getIdLong(), target.getIdLong(), target.getEffectiveAvatarUrl(), rolesModified));
		}

		public void onTempRoleAdded(Guild guild, User mod, User target, Role role, Duration duration) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.tempRoleAddedEmbed(locale, mod, target, role, duration));
		}

		public void onTempRoleAdded(Guild guild, User mod, User target, long roleId, Duration duration, boolean deleteAfter) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.tempRoleAddedEmbed(locale, mod, target, roleId, duration, deleteAfter));
		}

		public void onTempRoleRemoved(Guild guild, User mod, User target, Role role) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.tempRoleRemovedEmbed(locale, mod, target, role));
		}

		public void onTempRoleUpdated(Guild guild, User mod, User target, Role role, Instant until) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.tempRoleUpdatedEmbed(locale, mod, target, role, until));
		}

		public void onTempRoleAutoRemoved(Guild guild, Long targetId, Role role) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.tempRoleAutoRemovedEmbed(locale, targetId, role));
		}
	}

	// Group actions
	public class GroupLogs {
		private final LogType type = LogType.GROUP;

		public void onCreation(SlashCommandEvent event, Integer groupId, String name) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(event);
			sendLog(event.getGuild(), type, () -> logUtil.groupCreatedEmbed(locale, event.getMember().getAsMention(), event.getGuild().getIdLong(),
				event.getGuild().getIconUrl(), groupId, name));
		}

		public void onDeletion(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// For each group guild (except master) remove if from group DB and send log to log channel
			List<Long> memberIds = db.group.getGroupMembers(groupId);
			for (Long memberId : memberIds) {
				Guild member = JDA.getGuildById(memberId);

				final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(member);
				sendLog(member, type, () -> logUtil.groupMemberDeletedEmbed(locale, ownerId, ownerIcon, groupId, name));
			}

			// Master log
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(event);
			sendLog(event.getGuild(), type, () -> logUtil.groupOwnerDeletedEmbed(locale, event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));
		}

		public void onDeletion(long ownerId, String ownerIcon, Integer groupId) {
			String groupName = db.group.getName(groupId);

			// For each group guild (except master) remove if from group DB and send log to log channel
			List<Long> memberIds = db.group.getGroupMembers(groupId);
			for (Long memberId : memberIds) {
				Guild member = JDA.getGuildById(memberId);

				final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(member);
				sendLog(member, type, () -> logUtil.groupMemberDeletedEmbed(locale, ownerId, ownerIcon, groupId, groupName));
			}
		}

		public void onGuildJoined(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			Guild owner = JDA.getGuildById(ownerId);
			String ownerIcon = owner.getIconUrl();

			// Send log to added server
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(event);
			sendLog(event.getGuild(), type, () -> logUtil.groupMemberJoinedEmbed(locale, event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));

			// Master log
			final DiscordLocale ownerLocale = App.getInstance().getLocaleUtil().getLocale(owner);
			sendLog(owner, type, () -> logUtil.groupOwnerJoinedEmbed(ownerLocale, ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildLeft(SlashCommandEvent event, Integer groupId, String name) {
			long ownerId = db.group.getOwner(groupId);
			Guild owner = JDA.getGuildById(ownerId);
			String ownerIcon = owner.getIconUrl();

			// Send log to removed server
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(event);
			sendLog(event.getGuild(), type, () -> logUtil.groupMemberLeftEmbed(locale, event.getMember().getAsMention(), ownerId, ownerIcon, groupId, name));

			// Master log
			final DiscordLocale ownerLocale = App.getInstance().getLocaleUtil().getLocale(owner);
			sendLog(owner, type, () -> logUtil.groupOwnerLeftEmbed(ownerLocale, ownerId, ownerIcon, event.getGuild().getName(), event.getGuild().getIdLong(), groupId, name));
		}

		public void onGuildLeft(Guild target, int groupId) {
			long ownerId = db.group.getOwner(groupId);
			Guild owner = JDA.getGuildById(ownerId);
			String ownerIcon = owner.getIconUrl();

			String groupName = db.group.getName(groupId);

			// Inform group's owner
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(owner);
			sendLog(owner, type, () -> logUtil.groupOwnerLeftEmbed(locale, ownerId, ownerIcon, target.getName(), target.getIdLong(), groupId, groupName));
		}

		public void onGuildRemoved(SlashCommandEvent event, Guild target, Integer groupId, String name) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to removed server
			final DiscordLocale targetLocale = App.getInstance().getLocaleUtil().getLocale(target);
			sendLog(target, type, () -> logUtil.groupMemberLeftEmbed(targetLocale, "Forced, by group Master", ownerId, ownerIcon, groupId, name));

			// Master log
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(event);
			sendLog(event.getGuild(), type, () -> logUtil.groupOwnerRemovedEmbed(locale, event.getMember().getAsMention(), ownerId, ownerIcon, target.getName(), target.getIdLong(), groupId, name));
		}

		public void onRenamed(SlashCommandEvent event, String oldName, Integer groupId, String newName) {
			long ownerId = event.getGuild().getIdLong();
			String ownerIcon = event.getGuild().getIconUrl();

			// Send log to each group guild
			List<Long> memberIds = db.group.getGroupMembers(groupId);
			for (Long memberId : memberIds) {
				Guild member = JDA.getGuildById(memberId);

				final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(member);
				sendLog(member, type, () -> logUtil.groupMemberRenamedEmbed(locale, ownerId, ownerIcon, groupId, oldName, newName));
			}

			// Master log
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(event);
			sendLog(event.getGuild(), type, () -> logUtil.groupOwnerRenamedEmbed(locale, event.getMember().getAsMention(), ownerId, ownerIcon, groupId, oldName, newName));
		}
	}

	// Tickets actions
	public class TicketLogs {
		private final LogType type = LogType.TICKET;

		public void onCreate(Guild guild, GuildChannel messageChannel, User author) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.ticketCreatedEmbed(locale, messageChannel, author));
		}

		public void onClose(Guild guild, GuildChannel messageChannel, User userClosed, Long authorId, FileUpload file) {
			if (file == null) {
				onClose(guild, messageChannel, userClosed, authorId);
				return;
			}

			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;
			try {
				final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
				client.sendMessageEmbeds(
					logUtil.ticketClosedEmbed(locale, messageChannel, userClosed, authorId, db.tickets.getClaimer(messageChannel.getIdLong()))
				).addFiles(file).queue();
			} catch (Exception ex) {
				log.warn("Failed to send ticket close log: {}", ex.getMessage(), ex);
			}
		}

		public void onClose(Guild guild, GuildChannel messageChannel, User userClosed, Long authorId) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.ticketClosedEmbed(locale, messageChannel, userClosed, authorId, db.tickets.getClaimer(messageChannel.getIdLong())));
		}
	}

	// Server actions
	public class ServerLogs {
		private final LogType type = LogType.GUILD;

		public void onGuildUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = guild.getIdLong();
			final String name = guild.getName();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.guildUpdate(locale, id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onEmojiCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.emojiCreate(locale, id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onEmojiUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.emojiUpdate(locale, id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onEmojiDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.emojiDelete(locale, id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onStickerCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.stickerCreate(locale, id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onStickerUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.stickerUpdate(locale, id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onStickerDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.stickerDelete(locale, id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onRoleCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.ROLE_NAME).getNewValue();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.roleCreated(locale, id, name, entry.getChanges().values(), entry.getUserIdLong(), entry.getReason()));
		}

		public void onRoleDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.ROLE_NAME).getOldValue();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.roleDeleted(locale, id, name, entry.getChanges().values(), entry.getUserIdLong(), entry.getReason()));
		}

		public void onRoleUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = guild.getRoleById(id).getName();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.roleUpdate(locale, id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}

	}

	// Channel actions
	public class ChannelLogs {
		private final LogType type = LogType.CHANNEL;

		public void onChannelCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.CHANNEL_NAME).getNewValue();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.channelCreated(locale, id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onChannelDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = entry.getChangeByKey(AuditLogKey.CHANNEL_NAME).getOldValue();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.channelDeleted(locale, id, name, entry.getChanges().values(), entry.getUserIdLong(), entry.getReason()));
		}

		public void onChannelUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();
			final String name = guild.getGuildChannelById(id).getName();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.channelUpdate(locale, id, name, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onOverrideCreate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.overrideCreate(locale, id, entry, entry.getUserIdLong()));
		}

		public void onOverrideUpdate(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.overrideUpdate(locale, id, entry, entry.getUserIdLong(), guild.getId()));
		}

		public void onOverrideDelete(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.overrideDelete(locale, id, entry, entry.getUserIdLong()));
		}
	}

	// Member actions
	public class MemberLogs {
		private final LogType type = LogType.MEMBER;

		public void onNickChange(Member target, String oldNick, String newNick) {
			final Guild guild = target.getGuild();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.memberNickUpdate(locale, target.getUser(), oldNick, newNick));
		}

		public void onRoleChange(AuditLogEntry entry) {
			final Guild guild = entry.getGuild();
			final long id = entry.getTargetIdLong();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.rolesChange(locale, id, entry.getChanges().values(), entry.getUserIdLong()));
		}

		public void onJoined(Member member) {
			final Guild guild = member.getGuild();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.memberJoin(locale, member));
		}

		public void onLeft(Guild guild, Member cachedMember, User user) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.memberLeave(locale, cachedMember, user, cachedMember!=null ? cachedMember.getRoles() : List.of()));
		}
	}

	// Voice actions
	public class VoiceLogs {
		private final LogType type = LogType.VOICE;

		public void onVoiceMute(Member target, boolean isMuted, Long modId) {
			final Guild guild = target.getGuild();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.voiceMute(locale, target.getIdLong(), target.getUser().getName(), target.getEffectiveAvatarUrl(), isMuted, modId));
		}

		public void onVoiceDeafen(Member target, boolean isDeafen, Long modId) {
			final Guild guild = target.getGuild();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.voiceDeafen(locale, target.getIdLong(), target.getUser().getName(), target.getEffectiveAvatarUrl(), isDeafen, modId));
		}
	}

	// Message actions
	public class MessageLogs {
		private final LogType type = LogType.MESSAGE;

		public void onMessageUpdate(Member author, GuildChannel channel, long messageId, MessageData oldData, MessageData newData) {
			if (oldData == null || newData == null) return;

			final Guild guild = channel.getGuild();
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			MessageData.DiffData diff = MessageData.getDiffContent(oldData.getContentStripped(), newData.getContentStripped());

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			MessageEmbed embed = logUtil.messageUpdate(locale, author, channel.getIdLong(), messageId, oldData, newData, diff);
			if (embed == null) return;
			// Create changes file only if there are significant changes
			FileUpload fileUpload = (diff!=null && diff.manyChanges()) ? uploadContentUpdate(oldData, newData, messageId) : null;
			if (fileUpload != null) {
				client.sendMessageEmbeds(embed)
					.addFiles(fileUpload)
					.queue();
			} else {
				client.sendMessageEmbeds(embed).queue();
			}
		}

		public void onMessageDelete(GuildChannel channel, long messageId, MessageData data, Long modId) {
			final Guild guild = channel.getGuild();
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			if ((data == null || data.isEmpty()) && modId == null) return;

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			FileUpload fileUpload = uploadContent(data, messageId);
			if (fileUpload != null) {
				client.sendMessageEmbeds(logUtil.messageDelete(locale, channel.getIdLong(), messageId, data, modId))
					.addFiles(fileUpload)
					.queue();
			} else {
				client.sendMessageEmbeds(logUtil.messageDelete(locale, channel.getIdLong(), messageId, data, modId)).queue();
			}
		}

		public void onMessageBulkDelete(GuildChannel channel, String count, List<MessageData> messages, Long modId) {
			final Guild guild = channel.getGuild();
			IncomingWebhookClientImpl client = getWebhookClient(type, guild);
			if (client == null) return;

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			if (!messages.isEmpty()) {
				FileUpload fileUpload = uploadContentBulk(messages, channel.getIdLong());
				if (fileUpload != null) {
					client.sendMessageEmbeds(logUtil.messageBulkDelete(locale, channel.getIdLong(), count, modId))
						.addFiles(fileUpload)
						.queue();
					return;
				}
			}
			client.sendMessageEmbeds(logUtil.messageBulkDelete(locale, channel.getIdLong(), count, modId)).queue();
		}

		private FileUpload uploadContentUpdate(MessageData oldData, MessageData newData, long messageId) {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write("[%s (%s)]\n".formatted(newData.getAuthorName(), newData.getAuthorId()).getBytes());

				baos.write("------- OLD MESSAGE\n\n".getBytes());
				baos.write(oldData.getContent().getBytes(StandardCharsets.UTF_8));
				baos.write("\n\n------- NEW MESSAGE\n\n".getBytes());
				baos.write(newData.getContent().getBytes(StandardCharsets.UTF_8));

				return FileUpload.fromData(baos.toByteArray(), EncodingUtil.encodeMessage(messageId, Instant.now().getEpochSecond()));
			} catch (IOException ex) {
				log.error("Error at updated message content upload.", ex);
				return null;
			}
		}

		private FileUpload uploadContent(MessageData data, long messageId) {
			if (data.getContent().length() < 1000) return null;
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write("[%s (%s)]\n".formatted(data.getAuthorName(), data.getAuthorId()).getBytes());

				baos.write("------- CONTENT\n\n".getBytes());
				baos.write(data.getContent().getBytes(StandardCharsets.UTF_8));

				return FileUpload.fromData(baos.toByteArray(), EncodingUtil.encodeMessage(messageId, Instant.now().getEpochSecond()));
			} catch (IOException ex) {
				log.error("Error at deleted message content upload.", ex);
				return null;
			}
		}

		private FileUpload uploadContentBulk(List<MessageData> messages, long channelId) {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				baos.write("Channel ID: %s\n\n".formatted(channelId).getBytes());
				int cached = 0;
				for (MessageData data : messages) {
					if (data.isEmpty()) continue;
					cached++;
					baos.write("[%s (%s)]:\n".formatted(data.getAuthorName(), data.getAuthorId()).getBytes());
					if (data.getAttachment() != null)
						baos.write("[Attachment: %s]\n".formatted(data.getAttachment().getFileName()).getBytes(StandardCharsets.UTF_8));
					baos.write(data.getContent().getBytes(StandardCharsets.UTF_8));
					baos.write("\n\n-------===-------\n\n".getBytes());
				}
				if (cached == 0) return null;
				return FileUpload.fromData(baos.toByteArray(), EncodingUtil.encodeMessage(channelId, Instant.now().getEpochSecond()));
			} catch (IOException ex) {
				log.error("Error at bulk deleted messages content upload.", ex);
				return null;
			}
		}
	}

	// Level actions
	public class LevelLogs {
		private final LogType type = LogType.LEVEL;

		public void onLevelUp(Member target, int level, ExpType expType) {
			final Guild guild = target.getGuild();

			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.levelUp(locale, target, level, expType));
		}
	}

	// Bot actions
	public class BotLogs {
		private final LogType type = LogType.BOT;

		public void onAccessAdded(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.accessAdded(locale, mod, userTarget, roleTarget, level.getName()));
		}

		public void onAccessRemoved(Guild guild, User mod, @Nullable User userTarget, @Nullable Role roleTarget, CmdAccessLevel level) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.accessRemoved(locale, mod, userTarget, roleTarget, level.getName()));
		}

		public void onModuleEnabled(Guild guild, User mod, CmdModule module) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.moduleEnabled(locale, mod, module));
		}

		public void onModuleDisabled(Guild guild, User mod, CmdModule module) {
			final DiscordLocale locale = App.getInstance().getLocaleUtil().getLocale(guild);
			sendLog(guild, type, () -> logUtil.moduleDisabled(locale, mod, module));
		}
	}
}

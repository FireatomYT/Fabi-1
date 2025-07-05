package dev.fireatom.FABI.listeners;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.utils.database.managers.GuildVoiceManager.VoiceSettings;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildDeafenEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceGuildMuteEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.requests.ErrorResponse;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.logs.LogType;
import dev.fireatom.FABI.utils.database.DBUtil;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

public class VoiceListener extends ListenerAdapter {

	public static final Set<Permission> ownerPerms = Set.of(
		Permission.MANAGE_CHANNEL, Permission.VOICE_SET_STATUS, Permission.VOICE_MOVE_OTHERS,
		Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT, Permission.MESSAGE_SEND
	);

	private final int CHANNEL_LIMIT_SECONDS = 60;
	private final Cache<Long, Long> channelCreationLimit = Caffeine.newBuilder()
		.expireAfterWrite(CHANNEL_LIMIT_SECONDS, TimeUnit.SECONDS)
		.build();

	private final App bot;
	private final DBUtil db;

	public VoiceListener(App bot) {
		this.bot = bot;
		this.db = bot.getDBUtil();
	}

	@Override
	public void onGuildVoiceGuildMute(@NotNull GuildVoiceGuildMuteEvent event) {
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.VOICE)) return;

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MEMBER_UPDATE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty()) {
					AuditLogEntry entry = list.getFirst();
					if (entry.getChangeByKey("mute")!=null && entry.getTargetIdLong() == event.getMember().getIdLong() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
						bot.getGuildLogger().voice.onVoiceMute(event.getMember(), event.isGuildMuted(), entry.getUserIdLong());
						return;
					}
				}
				bot.getGuildLogger().voice.onVoiceMute(event.getMember(), event.isGuildMuted(), null);
			});
	}

	@Override
    public void onGuildVoiceGuildDeafen(@NotNull GuildVoiceGuildDeafenEvent event) {
		if (!db.getLogSettings(event.getGuild()).enabled(LogType.VOICE)) return;

		event.getGuild().retrieveAuditLogs()
			.type(ActionType.MEMBER_UPDATE)
			.limit(1)
			.queue(list -> {
				if (!list.isEmpty()) {
					AuditLogEntry entry = list.getFirst();
					if (entry.getChangeByKey("deaf")!=null && entry.getTargetIdLong() == event.getMember().getIdLong() && entry.getTimeCreated().isAfter(OffsetDateTime.now().minusSeconds(15))) {
						bot.getGuildLogger().voice.onVoiceDeafen(event.getMember(), event.isGuildDeafened(), entry.getUserIdLong());
						return;
					}
				}
				bot.getGuildLogger().voice.onVoiceDeafen(event.getMember(), event.isGuildDeafened(), null);
			});
	}

	@Override
	public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
		if (bot.getBlacklist().isBlacklisted(event.getGuild()) || bot.getBlacklist().isBlacklisted(event.getMember())) {
			return;
		}

		Long masterVoiceId = db.getVoiceSettings(event.getGuild()).getChannelId();
		// If joined master vc
		AudioChannelUnion channelJoined = event.getChannelJoined();
		if (channelJoined != null && masterVoiceId != null && channelJoined.getIdLong() == masterVoiceId) {
			handleVoiceCreate(event.getGuild(), event.getMember());
		}

		// if left custom vc
		AudioChannelUnion channelLeft = event.getChannelLeft();
		if (channelLeft != null && db.voice.existsChannel(channelLeft.getIdLong()) && channelLeft.getMembers().isEmpty()) {
			channelLeft.delete().reason("Custom channel, empty").queueAfter(500, TimeUnit.MILLISECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
			db.voice.remove(channelLeft.getIdLong());
		}

		// start reward counting
		if (db.levels.getSettings(event.getGuild()).isVoiceEnabled()) {
			if (channelJoined != null && channelLeft == null) {
				// Joined vc first time
				bot.getLevelUtil().putVoiceCache(event.getMember());
			} else if (channelJoined == null && channelLeft != null) {
				// left voice
				bot.getLevelUtil().handleVoiceLeft(event.getMember());
			}
		}
	}

	private void handleVoiceCreate(Guild guild, Member member) {
		if (!member.getVoiceState().inAudioChannel()) return;
		final long userId = member.getIdLong();
		final DiscordLocale guildLocale = App.getInstance().getLocaleUtil().getLocale(guild);

		// Check for existing channel
		if (db.voice.existsUser(userId)) {
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.cooldown"))
					.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER))
				);
			return;
		}
		// Rate limit
		Long lastChannelCreationTime = channelCreationLimit.getIfPresent(userId);
		if (lastChannelCreationTime == null) {
			channelCreationLimit.put(userId, System.currentTimeMillis());
		} else {
			long allowAfter = (CHANNEL_LIMIT_SECONDS*1000) - (System.currentTimeMillis() - lastChannelCreationTime);
			member.getUser().openPrivateChannel()
				.queue(channel -> channel.sendMessage(
					bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.cooldown")
						+ "\n> Try again " + TimeFormat.RELATIVE.after(allowAfter)
				).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER)));
			return;
		}

		VoiceSettings voiceSettings = db.getVoiceSettings(guild);
		Long categoryId = voiceSettings.getCategoryId();
		if (categoryId == null) return;

		String channelName = Optional.ofNullable(db.user.getName(userId))
			.or(() -> Optional.ofNullable(voiceSettings.getDefaultName()))
			.orElse(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.default_name"))
			.replace("{user}", member.getEffectiveName());
		channelName = channelName.substring(0, Math.min(100, channelName.length()));

		Integer channelLimit = Optional.ofNullable(db.user.getLimit(userId))
			.or(() -> Optional.ofNullable(voiceSettings.getDefaultLimit()))
			.orElse(0);
		
		guild.createVoiceChannel(channelName, guild.getCategoryById(categoryId))
			.reason(member.getUser().getEffectiveName()+" private channel")
			.setUserlimit(channelLimit)
			.syncPermissionOverrides()
			.addPermissionOverride(member, ownerPerms, null)
			.queue(
				channel -> {
					db.voice.add(userId, channel.getIdLong());
					try {
						guild.moveVoiceMember(member, channel).queueAfter(500, TimeUnit.MICROSECONDS, null, new ErrorHandler().ignore(ErrorResponse.USER_NOT_CONNECTED));
					} catch (Throwable ignored) {}
				},
				failure -> {
					member.getUser().openPrivateChannel()
						.flatMap(channel ->
							channel.sendMessage(bot.getLocaleUtil().getLocalized(guildLocale, "bot.voice.listener.failed")
								.formatted(failure.getMessage())
							)
						)
						.queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
				}
			);
	}

}

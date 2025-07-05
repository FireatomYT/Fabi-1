package dev.fireatom.FABI.utils;

import java.time.Duration;
import java.time.Instant;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.DBUtil;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.message.TimeUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ModerationUtil {

	private final DBUtil dbUtil;
	private final LocaleUtil lu;

	public ModerationUtil(DBUtil dbUtil, LocaleUtil lu) {
		this.dbUtil = dbUtil;
		this.lu = lu;
	}

	@Nullable
	public String getDmText(@NotNull CaseType type, Guild guild, String reason, Duration duration, User mod, boolean canAppeal) {
		final DiscordLocale locale = lu.getLocale(guild);
		int level;
		String text;
		switch (type) {
			case BAN -> {
				level = dbUtil.getGuildSettings(guild).getInformBan().getLevel();
				if (level == 0) return null;
				if (level >= 2)
					text = duration.isZero() ?
						lu.getLocalized(locale, "logger_embed.pm.banned_perm") :
						lu.getLocalized(locale, "logger_embed.pm.banned_temp");
				else
					text = lu.getLocalized(locale, "logger_embed.pm.banned");
			}
			case KICK -> {
				level = dbUtil.getGuildSettings(guild).getInformKick().getLevel();
				if (level == 0) return null;
				text = lu.getLocalized(locale, "logger_embed.pm.kicked");
			}
			case MUTE -> {
				level = dbUtil.getGuildSettings(guild).getInformMute().getLevel();
				if (level == 0) return null;
				text = lu.getLocalized(locale, "logger_embed.pm.muted");
			}
			case STRIKE_1, STRIKE_2, STRIKE_3 -> {
				level = dbUtil.getGuildSettings(guild).getInformStrike().getLevel();
				if (level == 0) return null;
				text = lu.getLocalized(locale, "logger_embed.pm.strike").formatted(
					lu.getLocalized(locale, "logger_embed.pm.strike"+(type.getValue()-20)),
					"%s"
				);
			}
			default -> {
				return null;
			}
		}

		StringBuilder builder = new StringBuilder(
			formatText(text, guild, level >= 2 ? reason : null, level >= 2 ? duration : null, level >= 3 ? mod : null)
		);
		if (type.equals(CaseType.BAN) && canAppeal) {
			String link = dbUtil.getGuildSettings(guild).getAppealLink();
			if (link != null) {
				builder.append("\n")
					.append(lu.getLocalized(locale, "logger_embed.pm.appeal").formatted(link));
			}
		}

		return builder.toString();
	}

	@Nullable
	public String getGamestrikeDmText(@NotNull CaseType type, Guild guild, String reason, User mod, GuildChannel targetChannel, int count, int limit) {
		if (type != CaseType.GAME_STRIKE) return null;

		final DiscordLocale locale = lu.getLocale(guild);
		int level = dbUtil.getGuildSettings(guild).getInformStrike().getLevel();

		String text = lu.getLocalized(locale, "logger_embed.pm.gamestrike")
			.formatted(targetChannel.getName(), targetChannel.getJumpUrl(), count, limit);

		StringBuilder builder = new StringBuilder(
			formatText(text, guild, level >= 2 ? reason : null, null, level >= 3 ? mod : null)
		);

		if (count >= limit) {
			builder.append("\n")
				.append(lu.getLocalized(locale, "logger_embed.pm.gamestrike_limit"));
		}

		return builder.toString();
	}

	@Nullable
	public MessageEmbed getDramaEmbed(@NotNull CaseType type, Guild guild, Member target, String reason, Duration duration) {
		return getDramaEmbed(type, guild, target, reason, duration, null);
	}

	@Nullable
	public MessageEmbed getDramaEmbed(@NotNull CaseType type, Guild guild, Member target, String reason, Duration duration, GuildChannel targetChannel) {
		final DiscordLocale locale = lu.getLocale(guild);
		int level;
		String text;
		switch (type) {
			case KICK -> {
				level = dbUtil.getGuildSettings(guild).getInformKick().getLevel();
				text = lu.getLocalized(locale, "logger_embed.drama.kicked");
			}
			case MUTE -> {
				level = dbUtil.getGuildSettings(guild).getInformMute().getLevel();
				text = lu.getLocalized(locale, "logger_embed.drama.muted");
			}
			case GAME_STRIKE -> {
				level = dbUtil.getGuildSettings(guild).getInformStrike().getLevel();
				text = lu.getLocalized(locale, "logger_embed.drama.gamestrike")
					.formatted(targetChannel.getName(), targetChannel.getJumpUrl());
			}
			case STRIKE_1, STRIKE_2, STRIKE_3 -> {
				level = dbUtil.getGuildSettings(guild).getInformStrike().getLevel();
				text = lu.getLocalized(locale, "logger_embed.drama.strike")
					.formatted(lu.getLocalized(locale, "logger_embed.pm.strike"+(type.getValue()-20)));
			}
			default -> {
				return null;
			}
		}

		return new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(target.getEffectiveName(), target.getEffectiveAvatarUrl())
			.setDescription(formatText(text, guild, level >= 2 ? reason : null, level >= 2 ? duration : null, null))
			.setTimestamp(Instant.now())
			.build();
	}

	@Nullable
	public MessageEmbed getDelstrikeEmbed(int amount, Guild guild, User mod) {
		int level = dbUtil.getGuildSettings(guild).getInformDelstrike().getLevel();
		if (level == 0) return null;
		String text = lu.getLocalized(lu.getLocale(guild), "logger_embed.pm.delstrike").formatted(amount);
		return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
				.setDescription(formatText(text, guild, null, null, level >= 3 ? mod : null))
				.build();
	}

	@NotNull
	public MessageEmbed getReasonUpdateEmbed(Guild guild, Instant timestamp, CaseType caseType, String oldReason, String newReason) {
		final DiscordLocale locale = lu.getLocale(guild);
		if (oldReason == null) oldReason = "-";
		if (caseType.equals(CaseType.MUTE)) {
			// if is mute
			return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
				.setDescription(lu.getLocalized(locale, "logger_embed.pm.reason_mute")
					.replace("{guild}", guild.getName())
					.replace("{time}", TimeUtil.formatTime(timestamp, false))
				).appendDescription("\n\n**Old**: ||`"+oldReason+"`||\n**New**: `"+newReason+"`")
				.build();
		} else {
			// else is strike
			return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
				.setDescription(lu.getLocalized(locale, "logger_embed.pm.reason_strike")
					.replace("{guild}", guild.getName())
					.replace("{time}", TimeUtil.formatTime(timestamp, false))
				).appendDescription("\n\n**Old**: ||`"+oldReason+"`||\n**New**: `"+newReason+"`")
				.build();
		}
	}

	@NotNull
	private String formatText(final String text, Guild guild, String reason, Duration duration, User mod) {
		String newText = (duration == null) ? text : text.replace("{time}", TimeUtil.durationToLocalizedString(lu, guild.getLocale(), duration));
		StringBuilder builder = new StringBuilder(newText.replace("{guild}", guild.getName()));
		if (reason != null) builder.append("\n> ").append(reason);
		if (mod != null) builder.append("\n\\- ").append(mod.getGlobalName());
		return builder.toString();
	}

	public MessageEmbed actionEmbed(DiscordLocale locale, int localCaseId, String actionPath, User target, User mod, String reason, String logUrl) {
		return new ActionEmbedBuilder(locale, localCaseId, target, mod, reason)
			.setDescription(lu.getLocalized(locale, actionPath))
			.addLink(logUrl)
			.build();
	}

	public MessageEmbed actionEmbed(DiscordLocale locale, int localCaseId, String actionPath, User target, User mod, String reason, Duration duration, String logUrl) {
		return new ActionEmbedBuilder(locale, localCaseId, target, mod, reason)
			.setDescription(lu.getLocalized(locale, actionPath)
				.formatted(TimeUtil.formatDuration(lu, locale, Instant.now(), duration)))
			.addLink(logUrl)
			.build();
	}

	public EmbedBuilder actionEmbed(DiscordLocale locale, int localCaseId, String actionPath, String typePath, User target, User mod, String reason, String logUrl) {
		return new ActionEmbedBuilder(locale, localCaseId, target, mod, reason)
			.setDescription(lu.getLocalized(locale, actionPath)
				.formatted(lu.getLocalized(locale, typePath)))
			.addLink(logUrl)
			.getBuilder();
	}

	public class ActionEmbedBuilder {
		private final DiscordLocale locale;
		private final EmbedBuilder embedBuilder = new EmbedBuilder();

		public ActionEmbedBuilder(DiscordLocale locale, int caseLocalId, User target, User mod, String reason) {
			embedBuilder.setColor(Constants.COLOR_SUCCESS)
				.addField(lu.getLocalized(locale, "logger.user"), "%s (%s)".formatted(target.getName(), target.getAsMention()), true)
				.addField(lu.getLocalized(locale, "logger.reason"), reason, true)
				.addField(lu.getLocalized(locale, "logger.moderation.mod"), "%s (%s)".formatted(mod.getName(), mod.getAsMention()), false)
				.setTimestamp(Instant.now())
				.setFooter("#"+caseLocalId);
			this.locale = locale;
		}

		public ActionEmbedBuilder setDescription(String text) {
			embedBuilder.setDescription(text);
			return this;
		}

		public ActionEmbedBuilder addLink(String logUrl) {
			if (logUrl!=null)
				embedBuilder.appendDescription(lu.getLocalized(locale, "logger.moderation.log_url").formatted(logUrl));
			return this;
		}

		public EmbedBuilder getBuilder() {
			return embedBuilder;
		}

		public MessageEmbed build() {
			return embedBuilder.build();
		}
	}

	@NotNull
	public <T extends SlashCommand> String parseReasonMentions(SlashCommandEvent event, T command) {
		OptionMapping option = event.getOption("reason");
		if (option == null) {
			return lu.getText(event, command.getPath()+".no_reason");
		}

		String reason = option.getAsString();
		Mentions mentions = option.getMentions();

		String newReason = reason;
		for (var channel : mentions.getChannels()) {
			newReason = newReason.replaceAll("<#"+channel.getIdLong()+">", "#"+channel.getName());
		}
		for (var role : mentions.getRoles()) {
			newReason = newReason.replaceAll("<@&"+role.getIdLong()+">", "@"+role.getName());
		}
		for (var member : mentions.getMembers()) {
			newReason = newReason.replaceAll("<@"+member.getIdLong()+">", "@"+member.getUser().getName());
		}
		for (var user : mentions.getUsers()) {
			newReason = newReason.replaceAll("<@"+user.getIdLong()+">", "@"+user.getName());
		}

		return newReason;
	}

}

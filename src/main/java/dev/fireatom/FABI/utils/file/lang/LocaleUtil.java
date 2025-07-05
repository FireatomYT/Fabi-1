package dev.fireatom.FABI.utils.file.lang;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.Emote;

import dev.fireatom.FABI.utils.message.MessageUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("unused")
public class LocaleUtil {

	private final App bot;
	private final LangUtil langUtil;

	public static final DiscordLocale DEFAULT_LOCALE = DiscordLocale.ENGLISH_UK;

	public LocaleUtil(App bot) {
		this.bot = bot;
		this.langUtil = new LangUtil(bot.getFileManager());
	}

	@NotNull
	public String getLocalized(@NotNull DiscordLocale locale, @NotNull String path) {
		return Emote.getWithEmotes(langUtil.getString(locale, path));
	}

	@NotNull
	public String getLocalized(@NotNull DiscordLocale locale, @NotNull String path, @Nullable String target) {
		if (target == null)
			return getLocalized(locale, path, List.of());
		else
			return getLocalized(locale, path, List.of(target));
	}

	@NotNull
	public String getLocalized(@NotNull DiscordLocale locale, @NotNull String path, @NotNull List<String> targets) {
		String targetReplacement = targets.isEmpty() ? "none" : MessageUtil.getFormattedUsers(this, targets.toArray(String[]::new));

		return Objects.requireNonNull(getLocalized(locale, path)
			.replace("{target}", targetReplacement)
			.replace("{targets}", targetReplacement)
		);
	}

	@Nullable
	public String getLocalizedNullable(@NotNull DiscordLocale locale, @NotNull String path) {
		return langUtil.getNullableString(locale, path);
	}

	@NotNull
	public String getLocalizedRandom(@NotNull DiscordLocale locale, @NotNull String path) {
		return Emote.getWithEmotes(langUtil.getRandomString(locale, path));
	}

	@NotNull
	public Map<DiscordLocale, String> getFullLocaleMap(String path, String defaultText) {
		Map<DiscordLocale, String> localeMap = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			// Ignores UK/US change
			if (locale.equals(DiscordLocale.ENGLISH_UK) || locale.equals(DiscordLocale.ENGLISH_US)) continue;
			localeMap.put(locale, getLocalized(locale, path));
		}
		localeMap.put(DiscordLocale.ENGLISH_UK, defaultText);
		localeMap.put(DiscordLocale.ENGLISH_US, defaultText);
		return localeMap;
	}

	@NotNull
	public Map<DiscordLocale, String> getLocaleMap(String path) {
		Map<DiscordLocale, String> localeMap = new HashMap<>();
		for (DiscordLocale locale : bot.getFileManager().getLanguages()) {
			// Ignores UK/US change
			if (locale.equals(DiscordLocale.ENGLISH_UK) || locale.equals(DiscordLocale.ENGLISH_US)) continue;
			localeMap.put(locale, getLocalized(locale, path));
		}
		return localeMap;
	}


	@NotNull
	public String getText(@NotNull String path) {
		return getLocalized(DEFAULT_LOCALE, path);
	}

	@NotNull
	public String getText(@NotNull IReplyCallback replyCallback, @NotNull String path) {
		return getLocalized(getLocale(replyCallback), path);
	}

	@NotNull
	public String getText(@NotNull IReplyCallback replyCallback, @NotNull String path, @Nullable Object... args) {
		return new Formatter().format(getLocalized(getLocale(replyCallback), path), args).toString();
	}

	@NotNull
	public String getGuildText(IReplyCallback replyCallback, @NotNull String path) {
		return getLocalized(getLocale(replyCallback.getGuild()), path);
	}

	@NotNull
	public String getGuildText(IReplyCallback replyCallback, @NotNull String path, @Nullable Object... args) {
		return new Formatter().format(getLocalized(getLocale(replyCallback.getGuild()), path), args).toString();
	}


	@NotNull
	public String getTargetText(@NotNull IReplyCallback replyCallback, @NotNull String path, @Nullable String target) {
		if (target == null)
			return getTargetText(replyCallback, path, List.of());
		else
			return getTargetText(replyCallback, path, List.of(target));
	}

	@NotNull
	public String getTargetText(@NotNull IReplyCallback replyCallback, @NotNull String path, List<String> targets) {
		return getLocalized(getLocale(replyCallback), path, targets);
	}


	@NotNull
	public DiscordLocale getLocale(IReplyCallback replyCallback) {
		if (replyCallback.isFromGuild()) {
			return getLocale(replyCallback.getGuild());
		} else {
			return replyCallback.getUserLocale();
		}
	}

	@NotNull
	public DiscordLocale getLocale(Guild guild) {
		DiscordLocale locale = App.getInstance().getDBUtil().getGuildSettings(guild).getLocale();
		if (locale == DiscordLocale.UNKNOWN) {
			return guild.getLocale();
		} else {
			return locale;
		}
	}

}

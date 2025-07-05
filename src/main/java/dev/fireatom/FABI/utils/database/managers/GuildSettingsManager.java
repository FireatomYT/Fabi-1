package dev.fireatom.FABI.utils.database.managers;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;
import static dev.fireatom.FABI.utils.CastUtil.resolveOrDefault;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GuildSettingsManager extends LiteBase {

	private final Set<String> columns = Set.of(
		"color", "lastWebhookId", "appealLink", "reportChannelId",
		"strikeExpire", "strikeCooldown", "modulesOff",
		"informBan", "informKick", "informMute", "informStrike", "informDelstrike",
		"roleWhitelist", "drama", "dramaChannel", "locale"
	);

	// Cache
	private final Cache<Long, GuildSettings> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	private final GuildSettings blankSettings = new GuildSettings();
	
	public GuildSettingsManager(ConnectionUtil cu) {
		super(cu, "guild");
	}

	public GuildSettings getSettings(long guildId) {
		return cache.get(guildId, id -> applyOrDefault(getData(id), GuildSettings::new, blankSettings));
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	public void remove(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}
	
	public void setColor(long guildId, int color) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, color) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET color=%<d".formatted(table, guildId, color));
	}

	public void setLastWebhookId(long guildId, long webhookId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, lastWebhookId) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET lastWebhookId=%<d".formatted(table, guildId, webhookId));
	}

	public void setAppealLink(long guildId, String link) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, appealLink) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET appealLink=%<s".formatted(table, guildId, quote(link)));
	}

	public void setReportChannelId(long guildId, @Nullable Long channelId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, reportChannelId) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET reportChannelId=%<s".formatted(table, guildId, channelId==null ? "NULL" : channelId));
	}

	public void setStrikeExpiresAfter(long guildId, int expiresAfter) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, strikeExpire) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET strikeExpire=%<d".formatted(table, guildId, expiresAfter));
	}

	public void setStrikeCooldown(long guildId, int cooldown) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, strikeCooldown) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET strikeCooldown=%<d".formatted(table, guildId, cooldown));
	}

	public void setModuleDisabled(long guildId, int modulesOff) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, modulesOff) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET modulesOff=%<d".formatted(table, guildId, modulesOff));
	}

	public void setInformBanLevel(long guildId, ModerationInformLevel informLevel) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informBan) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informBan=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformKickLevel(long guildId, ModerationInformLevel informLevel) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informKick) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informKick=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformMuteLevel(long guildId, ModerationInformLevel informLevel) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informMute) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informMute=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformStrikeLevel(long guildId, ModerationInformLevel informLevel) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informStrike) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informStrike=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setInformDelstrikeLevel(long guildId, ModerationInformLevel informLevel) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, informDelstrike) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET informDelstrike=%<d".formatted(table, guildId, informLevel.getLevel()));
	}

	public void setRoleWhitelist(long guildId, boolean roleWhitelist) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, roleWhitelist) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET roleWhitelist=%<d".formatted(table, guildId, roleWhitelist?1:0));
	}

	public void setDramaLevel(long guildId, DramaLevel dramaLevel) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, drama) VALUES (%s, %d) ON CONFLICT(guildId) DO UPDATE SET drama=%<d".formatted(table, guildId, dramaLevel.level));
	}

	public void setDramaChannelId(long guildId, @Nullable Long channelId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, dramaChannel) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET dramaChannel=%<s".formatted(table, guildId, channelId==null ? "NULL" : channelId));
	}

	public void setLocale(long guildId, @Nullable DiscordLocale locale) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, locale) VALUES (%s, %s) ON CONFLICT(guildId) DO UPDATE SET locale=%<s".formatted(table, guildId, locale==null ? "NULL" : quote(locale.getLocale())));
	}


	private void invalidateCache(long guildId) {
		cache.invalidate(guildId);
	}

	public static class GuildSettings {
		private final Long lastWebhookId, reportChannelId, dramaChannelId;
		private final int color, strikeExpire, strikeCooldown, modulesOff;
		private final String appealLink;
		@NotNull private final ModerationInformLevel informBan, informKick, informMute, informStrike, informDelstrike;
		private final boolean roleWhitelist;
		@NotNull private final DramaLevel dramaLevel;
		@NotNull private final DiscordLocale locale;

		public GuildSettings() {
			this.color = Constants.COLOR_DEFAULT;
			this.lastWebhookId = null;
			this.appealLink = null;
			this.reportChannelId = null;
			this.strikeExpire = 7;
			this.strikeCooldown = 0;
			this.modulesOff = 0;
			this.informBan = ModerationInformLevel.DEFAULT;
			this.informKick = ModerationInformLevel.DEFAULT;
			this.informMute = ModerationInformLevel.DEFAULT;
			this.informStrike = ModerationInformLevel.DEFAULT;
			this.informDelstrike = ModerationInformLevel.NONE;
			this.roleWhitelist = false;
			this.dramaLevel = DramaLevel.OFF;
			this.dramaChannelId = null;
			this.locale = DiscordLocale.UNKNOWN;
		}

		public GuildSettings(Map<String, Object> data) {
			this.color = resolveOrDefault(data.get("color"), obj -> Integer.decode(obj.toString()), Constants.COLOR_DEFAULT);
			this.lastWebhookId = getOrDefault(data.get("lastWebhookId"), null);
			this.appealLink = getOrDefault(data.get("appealLink"), null);
			this.reportChannelId = getOrDefault(data.get("reportChannelId"), null);
			this.strikeExpire = getOrDefault(data.get("strikeExpire"), 7);
			this.strikeCooldown = getOrDefault(data.get("strikeCooldown"), 0);
			this.modulesOff = getOrDefault(data.get("modulesOff"), 0);
			this.informBan = ModerationInformLevel.byLevel(getOrDefault(data.get("informBan"), 1));
			this.informKick = ModerationInformLevel.byLevel(getOrDefault(data.get("informKick"), 1));
			this.informMute = ModerationInformLevel.byLevel(getOrDefault(data.get("informMute"), 1));
			this.informStrike = ModerationInformLevel.byLevel(getOrDefault(data.get("informStrike"), 1));
			this.informDelstrike = ModerationInformLevel.byLevel(getOrDefault(data.get("informDelstrike"), 0));
			this.roleWhitelist = getOrDefault(data.get("roleWhitelist"), 0) == 1;
			this.dramaLevel = DramaLevel.byLevel(getOrDefault(data.get("drama"), 0));
			this.dramaChannelId = getOrDefault(data.get("dramaChannel"), null);
			this.locale = resolveOrDefault(data.get("locale"), s->DiscordLocale.from((String) s), DiscordLocale.UNKNOWN);
		}

		public int getColor() {
			return color;
		}

		@Nullable
		public Long getLastWebhookId() {
			return lastWebhookId;
		}

		@Nullable
		public String getAppealLink() {
			return appealLink;
		}

		@Nullable
		public Long getReportChannelId() {
			return reportChannelId;
		}

		public Duration getStrikeExpires() {
			return Duration.ofDays(strikeExpire);
		}

		@NotNull
		public Duration getStrikeCooldown() {
			return Duration.ofSeconds(strikeCooldown);
		}
		
		public int getModulesOff() {
			return modulesOff;
		}

		@NotNull
		public EnumSet<CmdModule> getDisabledModules() {
			return CmdModule.decodeModules(modulesOff);
		}

		public boolean isDisabled(CmdModule module) {
			return (modulesOff & module.getValue()) == module.getValue();
		}

		@NotNull
		public ModerationInformLevel getInformBan() {
			return informBan;
		}

		@NotNull
		public ModerationInformLevel getInformKick() {
			return informKick;
		}

		@NotNull
		public ModerationInformLevel getInformMute() {
			return informMute;
		}

		@NotNull
		public ModerationInformLevel getInformStrike() {
			return informStrike;
		}

		@NotNull
		public ModerationInformLevel getInformDelstrike() {
			return informDelstrike;
		}

		public boolean isRoleWhitelistEnabled() {
			return roleWhitelist;
		}

		@Nullable
		public Long getDramaChannelId() {
			return dramaChannelId;
		}

		@NotNull
		public DramaLevel getDramaLevel() {
			return dramaLevel;
		}

		/**
		 * @return Guild forced language or {@link DiscordLocale#UNKNOWN}.
		 */
		@NotNull
		public DiscordLocale getLocale() {
			return locale;
		}
	}

	public enum ModerationInformLevel {
		NONE(0, "logger_embed.inform.0"),
		DEFAULT(1, "logger_embed.inform.1"),
		REASON(2, "logger_embed.inform.2"),
		MOD(3, "logger_embed.inform.3");

		private final int level;
		private final String path;

		private static final Map<Integer, ModerationInformLevel> BY_LEVEL = new HashMap<>();

		static {
			for (ModerationInformLevel informLevel : ModerationInformLevel.values()) {
				BY_LEVEL.put(informLevel.getLevel(), informLevel);
			}
		}

		ModerationInformLevel(int level, String path) {
			this.level = level;
			this.path = path;
		}

		public int getLevel() {
			return level;
		}

		public String getPath() {
			return path;
		}

		public static ModerationInformLevel byLevel(int value) {
			return BY_LEVEL.get(value);
		}

		public static List<Command.Choice> asChoices(LocaleUtil lu) {
			return Stream.of(values()).map(informLevel -> new Command.Choice(lu.getText(informLevel.getPath()), informLevel.getLevel())).toList();
		}
	}

	public enum DramaLevel {
		OFF(0),
		ONLY_BAD_DM(1),
		ALL(2);

		public final int level;

		private static final Map<Integer, DramaLevel> BY_LEVEL = new HashMap<>();

		static {
			for (DramaLevel level : DramaLevel.values()) {
				BY_LEVEL.put(level.level, level);
			}
		}

		DramaLevel(int level) {
			this.level = level;
		}

		public static DramaLevel byLevel(int value) {
			return BY_LEVEL.get(value);
		}
	}

}

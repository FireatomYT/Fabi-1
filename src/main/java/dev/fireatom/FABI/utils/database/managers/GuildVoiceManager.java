package dev.fireatom.FABI.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import java.sql.SQLException;
import java.util.Map;
import java.util.Set;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;

public class GuildVoiceManager extends LiteBase {

	private final Set<String> columns = Set.of(
		"categoryId", "channelId", "defaultName", "defaultLimit"
	);
	// Cache
	private final Cache<Long, VoiceSettings> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	private final VoiceSettings blankSettings = new VoiceSettings();

	public GuildVoiceManager(ConnectionUtil cu) {
		super(cu, "guildVoice");
	}

	public void setup(long guildId, long categoryId, long channelId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, categoryId, channelId) VALUES (%d, %d, %d) ON CONFLICT(guildId) DO UPDATE SET categoryId=%3$d, channelId=%4$d"
			.formatted(table, guildId, categoryId, channelId));
	}

	public void remove(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void setName(long guildId, String defaultName) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, defaultName) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET defaultName=%<s"
			.formatted(table, guildId, quote(defaultName)));
	}

	public void setLimit(long guildId, int defaultLimit) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, defaultLimit) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET defaultLimit=%<d"
			.formatted(table, guildId, defaultLimit));
	}

	public VoiceSettings getSettings(long guildId) {
		return cache.get(guildId, id -> applyOrDefault(getData(id), VoiceSettings::new, blankSettings));
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	private void invalidateCache(long guildId) {
		cache.invalidate(guildId);
	}

	public static class VoiceSettings {
		private final Long categoryId, channelId;
		private final String defaultName;
		private final Integer defaultLimit;

		public VoiceSettings() {
			this.categoryId = null;
			this.channelId = null;
			this.defaultName = null;
			this.defaultLimit = null;
		}

		public VoiceSettings(Map<String, Object> data) {
			this.categoryId = getOrDefault(data.get("categoryId"), null);
			this.channelId = getOrDefault(data.get("channelId"), null);
			this.defaultName = getOrDefault(data.get("defaultName"), null);
			this.defaultLimit = getOrDefault(data.get("defaultLimit"), null);
		}

		public Long getChannelId() {
			return channelId;
		}

		public Long getCategoryId() {
			return categoryId;
		}

		public String getDefaultName() {
			return defaultName;
		}

		public Integer getDefaultLimit() {
			return defaultLimit;
		}

	}

}

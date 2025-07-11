package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.objects.logs.LogType;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;

public class GuildLogsManager extends LiteBase {

	// Cache
	private final Cache<Long, LogSettings> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.expireAfterAccess(1, TimeUnit.HOURS)
		.build();
	private final LogSettings blankSettings = new LogSettings();

	private final Set<String> logColumns = LogType.getAllNames();
	
	public GuildLogsManager(ConnectionUtil cu) {
		super(cu, "logWebhooks");
	}

	public void setLogWebhook(@NotNull LogType type, long guildId, WebhookData webhookData) throws SQLException {
		invalidateCache(guildId);
		String data = webhookData==null ? "NULL" : webhookData.encodeData();
		execute("INSERT INTO %s(guildId, %s) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET %2$s=%4$s".formatted(table, quote(type.getName()), guildId, quote(data)));
	}

	public void removeLogWebhook(LogType type, long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("UPDATE %s SET %s=NULL WHERE (guildId=%d)".formatted(table, type.getName(), guildId));
	}

	public void removeGuild(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public WebhookData getLogWebhook(long guildId, LogType type) {
		return getSettings(guildId).getWebhookData(type);
	}

	public LogSettings getSettings(long guildId) {
		return cache.get(guildId, id -> applyOrDefault(getData(id), LogSettings::new, blankSettings));
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), logColumns);
	}

	private void invalidateCache(long guildId) {
		cache.invalidate(guildId);
	}

	public static class LogSettings {
		private final Map<LogType, WebhookData> logs;

		public LogSettings() {
			this.logs = new HashMap<>();
		}

		public LogSettings(Map<String, Object> map) {
			this.logs = new HashMap<>();
			map.entrySet().stream()
				.filter(e -> e.getValue() != null)
				.forEach(e -> logs.put(LogType.of(e.getKey()), new WebhookData((String) e.getValue())));
		}

		public WebhookData getWebhookData(LogType type) {
			return logs.getOrDefault(type, null);
		}

		public Map<LogType, Long> getChannels() {
			return logs.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getChannelId()));
		}

		public Set<WebhookData> getWebhooks() {
			return new HashSet<>(logs.values());
		}

		public boolean enabled(LogType type) {
			return logs.containsKey(type);
		}

		public boolean isEmpty() {
			return logs.isEmpty();
		}
	}

	public static class WebhookData {
		private final long channelId, webhookId;
		private final String token;
		
		public WebhookData(long channelId, long webhookId, String token) {
			this.channelId = channelId;
			this.webhookId = webhookId;
			this.token = token;
		}

		public WebhookData(@NotNull String data) {
			String[] array = data.split(":");
			this.channelId = Long.parseLong(array[0]);
			this.webhookId = Long.parseLong(array[1]);
			this.token = array[2];
		}

		public long getChannelId() {
			return channelId;
		}

		public long getWebhookId() {
			return webhookId;
		}

		public String getToken() {
			return token;
		}

		public String encodeData() {
			return channelId+":"+webhookId+":"+token;
		}
	}
}

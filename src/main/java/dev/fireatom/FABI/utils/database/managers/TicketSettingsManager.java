package dev.fireatom.FABI.utils.database.managers;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;
import static dev.fireatom.FABI.utils.CastUtil.resolveOrDefault;

import java.sql.SQLException;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;

public class TicketSettingsManager extends LiteBase {
	
	private final Set<String> columns = Set.of(
		"autocloseTime", "autocloseLeft", "timeToReply",
		"rowName1", "rowName2", "rowName3",
		"otherRole", "roleSupport", "deletePing",
		"allowClose", "transcripts"
	);

	// Cache
	private final Cache<Long, TicketSettings> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	private final TicketSettings defaultSettings = new TicketSettings();

	public TicketSettingsManager(ConnectionUtil cu) {
		super(cu, "ticketSettings");
	}

	public TicketSettings getSettings(long guildId) {
		return cache.get(guildId, id -> applyOrDefault(getData(id), TicketSettings::new, defaultSettings));
	}

	private Map<String, Object> getData(long guildId) {
		return selectOne("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), columns);
	}

	public void remove(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void setRowText(long guildId, int row, String text) throws SQLException {
		if (row < 1 || row > 3)
			throw new IndexOutOfBoundsException(row);
		invalidateCache(guildId);
		execute("INSERT INTO %1$s(guildId, rowName%2$d) VALUES (%3$d, %4$s) ON CONFLICT(guildId) DO UPDATE SET rowName%2$d=%4$s".formatted(table, row, guildId, quote(text)));
	}

	public void setAutocloseTime(long guildId, int hours) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, autocloseTime) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseTime=%<d".formatted(table, guildId, hours));
	}

	public void setAutocloseLeft(long guildId, boolean close) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, autocloseLeft) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET autocloseLeft=%<d".formatted(table, guildId, close ? 1 : 0));
	}

	public void setTimeToReply(long guildId, int hours) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, timeToReply) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET timeToReply=%<d".formatted(table, guildId, hours));
	}

	public void setOtherRoles(long guildId, boolean otherRoles) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, otherRole) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET otherRole=%<d".formatted(table, guildId, otherRoles ? 1 : 0));
	}

	public void setSupportRoles(long guildId, @NotNull List<Long> roleIds) throws SQLException {
		invalidateCache(guildId);
		final String text = roleIds.stream().map(String::valueOf).collect(Collectors.joining(";"));
		execute("INSERT INTO %s(guildId, roleSupport) VALUES (%d, %s) ON CONFLICT(guildId) DO UPDATE SET roleSupport=%<s".formatted(table, guildId, quote(text)));
	}

	public void setDeletePings(long guildId, boolean deletePing) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, deletePing) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET deletePing=%<d".formatted(table, guildId, deletePing ? 1 : 0));
	}

	public void setAllowClose(long guildId, AllowClose value) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, allowClose) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET allowClose=%<d".formatted(table, guildId, value.getValue()));
	}

	public void setTranscript(long guildId, TranscriptsMode value) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, transcripts) VALUES (%d, %d) ON CONFLICT(guildId) DO UPDATE SET transcripts=%<d".formatted(table, guildId, value.getValue()));
	}


	private void invalidateCache(long guildId) {
		cache.invalidate(guildId);
	}

	public static class TicketSettings {
		private final int autocloseTime, timeToReply;
		private final boolean autocloseLeft, otherRoles, deletePings;
		private final List<String> rowText;
		private final List<Long> roleSupportIds;
		private final AllowClose allowClose;
		private final TranscriptsMode transcriptsMode;

		public TicketSettings() {
			this.autocloseTime = 0;
			this.autocloseLeft = false;
			this.timeToReply = 0;
			this.otherRoles = true;
			this.rowText = Collections.nCopies(3, "Select roles");
			this.roleSupportIds = List.of();
			this.deletePings = true;
			this.allowClose = AllowClose.EVERYONE;
			this.transcriptsMode = TranscriptsMode.EXCEPT_ROLES;
		}

		public TicketSettings(Map<String, Object> data) {
			this.autocloseTime = getOrDefault(data.get("autocloseTime"), 0);
			this.autocloseLeft = getOrDefault(data.get("autocloseLeft"), 0) == 1;
			this.timeToReply = getOrDefault(data.get("timeToReply"), 0);
			this.otherRoles = getOrDefault(data.get("otherRole"), 0) == 1;
			this.rowText = List.of(
				getOrDefault(data.get("rowName1"), "Select roles"),
				getOrDefault(data.get("rowName2"), "Select roles"),
				getOrDefault(data.get("rowName3"), "Select roles")
			);
			this.roleSupportIds = resolveOrDefault(
				data.get("roleSupport"),
				obj -> {
					String value = String.valueOf(obj);
					if (value.isBlank()) return List.of();
					return Stream.of(value.split(";"))
						.map(Long::parseLong)
						.toList();
					},
				List.of()
			);
			this.deletePings = getOrDefault(data.get("deletePing"), 1) == 1;
			this.allowClose = AllowClose.valueOf(getOrDefault(data.get("allowClose"), AllowClose.EVERYONE.value));
			this.transcriptsMode = TranscriptsMode.valueOf(getOrDefault(data.get("transcripts"), TranscriptsMode.EXCEPT_ROLES.value));
		}

		public Duration getAutocloseTime() {
			return Duration.ofHours(autocloseTime);
		}

		public boolean autocloseLeftEnabled() {
			return autocloseLeft;
		}

		public Duration getTimeToReply() {
			return Duration.ofHours(timeToReply);
		}

		public boolean otherRoleEnabled() {
			return otherRoles;
		}

		@NotNull
		public String getRowText(int n) {
			if (n < 1 || n > 3)
				throw new IndexOutOfBoundsException(n);
			return rowText.get(n-1);
		}

		@NotNull
		public List<Long> getRoleSupportIds() {
			return roleSupportIds;
		}

		public boolean deletePingsEnabled() {
			return deletePings;
		}

		@NotNull
		public AllowClose getAllowClose() {
			return allowClose;
		}

		@NotNull
		public TranscriptsMode getTranscriptsMode() {
			return transcriptsMode;
		}
	}

	public enum AllowClose {
		EVERYONE(0),
		HELPER(1),
		SUPPORT(2);

		private final int value;
		private final static Map<Integer, AllowClose> BY_VALUE = new HashMap<>(values().length);

		static {
			for (AllowClose c : values()) {
				BY_VALUE.put(c.value, c);
			}
		}

		AllowClose(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static AllowClose valueOf(int value) {
			return BY_VALUE.get(value);
		}
	}

	public enum TranscriptsMode {
		ALL(0),
		EXCEPT_ROLES(1),
		NONE(2);

		private final int value;
		private final static Map<Integer, TranscriptsMode> BY_VALUE = new HashMap<>(values().length);

		static {
			for (TranscriptsMode c : values()) {
				BY_VALUE.put(c.value, c);
			}
		}

		TranscriptsMode(int value) {
			this.value = value;
		}

		public int getValue() {
			return value;
		}

		public static TranscriptsMode valueOf(int value) {
			return BY_VALUE.get(value);
		}
	}

}

package dev.fireatom.FABI.utils.database.managers;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;
import static dev.fireatom.FABI.utils.CastUtil.requireNonNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public class CaseManager extends LiteBase {
	protected final Logger log = (Logger) LoggerFactory.getLogger(CaseManager.class);
	
	private final Set<String> fullCaseKeys = Set.of(
		"rowId", "localId", "type", "targetId", "targetTag",
		"modId", "modTag", "guildId", "reason", "timeStart",
		"duration", "active", "logUrl"
	);
	
	public CaseManager(ConnectionUtil cu) {
		super(cu, "cases");
	}

	// add new case
	@NotNull
	public CaseData add(CaseType type, long userId, String userName, long modId, String modName, long guildId, String reason, Duration duration) throws SQLException {
		Instant now = Instant.now();
		final String sql = """
			INSERT INTO %s(type, targetId, targetTag, modId, modTag, guildId, reason, timeStart, duration, active, localId)
			VALUES (%d, %d, %s, %d, %s, %d, %s, %d, %d, %d, COALESCE((SELECT MAX(localId) + 1 FROM cases WHERE guildId=%d), 1))
			RETURNING rowId, localId;
			""".formatted(
				table, type.getValue(), userId, quote(userName), modId, quote(modName), guildId, quote(reason),
			now.getEpochSecond(), duration == null ? -1 : duration.getSeconds(),
				type.isActiveInt(), guildId
			);

		final int rowId, localId;
		//noinspection SqlSourceToSinkFlow
		try (
			Connection conn = DriverManager.getConnection(getUrl());
			ResultSet rs = conn.prepareStatement(sql).executeQuery()
		) {
			if (rs.next()) {
				rowId = rs.getInt("rowId");
				localId = rs.getInt("localId");
			} else {
				throw new SQLException("Failed to create new case.");
			}
		} catch (SQLException e) {
			log.warn("DB SQLite: Error creating new case\nRequest: {}", sql, e);
			throw e;
		}

		return new CaseData(
			rowId, localId,
			type, guildId,
			userId, userName,
			modId, modName,
			reason,
			now, duration,
			type.isActive(), null
		);
	}

	// update case reason
	public void updateReason(int rowId, String reason) throws SQLException {
		execute("UPDATE %s SET reason=%s WHERE (rowId=%d)".formatted(table, quote(reason), rowId));
	}

	// update case duration
	public void updateDuration(int rowId, Duration duration) throws SQLException {
		execute("UPDATE %s SET duration=%d WHERE (rowId=%d)".formatted(table, duration.getSeconds(), rowId));
	}

	// set case inactive
	public void setInactive(int rowId) throws SQLException {
		execute("UPDATE %s SET active=0 WHERE (rowId=%d)".formatted(table, rowId));
	}

	public void setLogUrl(int rowId, String logUrl) {
		if (logUrl==null) return;
		try {
			execute("UPDATE %s SET logUrl=%s WHERE (rowId=%d)".formatted(table, quote(logUrl), rowId));
		} catch (SQLException ignored) {}
	}

	// get case info by row
	public CaseData getInfo(int rowId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (rowId=%d)".formatted(table, rowId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}
	// get case info for guild
	public CaseData getInfo(long guildId, int localId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND localId=%d)".formatted(table, guildId, localId), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// get 10 cases for guild's user sorted in pages
	public List<CaseData> getGuildUser(long guildId, long userId, int page) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d) ORDER BY rowId DESC LIMIT 10 OFFSET %d"
			.formatted(table, guildId, userId, (page-1)*10), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(CaseData::new).toList();
	}

	// get 10 cases for guild's user sorted in pages, active or inactive only
	public List<CaseData> getGuildUser(long guildId, long userId, int page, boolean active) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d AND active=%d) ORDER BY rowId DESC LIMIT 10 OFFSET %d"
			.formatted(table, guildId, userId, active?1:0, (page-1)*10), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(CaseData::new).toList();
	}

	// get user active temporary cases data
	public CaseData getMemberActive(long userId, long guildId, CaseType type) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (guildId=%d AND targetId=%d AND type=%d AND active=1)"
			.formatted(table, guildId, userId, type.getValue()), fullCaseKeys);
		if (data == null) return null;
		return new CaseData(data);
	}

	// set all ban cases for user inactive
	public void setInactiveStrikeCases(long userId, long guildId) throws SQLException {
		execute("UPDATE %s SET active=0 WHERE (targetId=%d AND guildId=%d AND type>20)".formatted(table, userId, guildId));
	}

	// set all strike cases for user inactive
	// Better way for this is harder...
	public void setInactiveByType(long userId, long guildId, CaseType type) {
		try {
			execute("UPDATE %s SET active=0 WHERE (targetId=%d AND guildId=%d AND type=%d)".formatted(table, userId, guildId, type.getValue()));
		} catch (SQLException ignored) {}
	}

	// get case pages
	public int countCases(long guildId, long userId) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%d AND targetId=%d)".formatted(table, guildId, userId));
	}

	// count cases by moderator after certain date
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId, Instant afterTime) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d AND timeStart>%d) GROUP BY type"
			.formatted(table, guildId, modId, afterTime.getEpochSecond()), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}

	// count all cases by moderator
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d) GROUP BY type"
			.formatted(table, guildId, modId), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}

	// count cases by moderator after and before certain dates
	public Map<Integer, Integer> countCasesByMod(long guildId, long modId, long afterEpoch, long beforeEpoch) {
		List<Map<String, Object>> data = select("SELECT type, COUNT(*) AS cc FROM %s WHERE (guildId=%d AND modId=%d AND timeStart>%d AND timeStart<%d) GROUP BY type"
			.formatted(table, guildId, modId, afterEpoch, beforeEpoch), Set.of("type", "cc"));
		if (data.isEmpty()) return Collections.emptyMap();
		return data.stream().collect(Collectors.toMap(s -> (Integer) s.get("type"), s -> (Integer) s.get("cc")));
	}

	//  BANS
	// get all active expired bans
	public List<CaseData> getExpired() {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (active=1 AND type<20 AND duration>0 AND timeStart+duration<%d) ORDER BY rowId DESC LIMIT 10"
			.formatted(table, Instant.now().getEpochSecond()), fullCaseKeys);
		if (data.isEmpty()) return Collections.emptyList();
		return data.stream().map(CaseData::new).toList();
	}


	public static class CaseData {
		private final int rowId, localId;
		@NotNull
		private final CaseType type;
		private final long guildId, targetId, modId;
		private final String targetTag, modTag, reason, logUrl;
		@NotNull
		private final Instant timeStart;
		@NotNull
		private final Duration duration;
		private final boolean active;

		public CaseData(
			int rowId, int localId,
			@NotNull CaseType type, long guildId,
			long targetId, String targetTag,
			long modId, String modTag,
			@Nullable String reason,
			@NotNull Instant timeStart, Duration duration,
			boolean active, String logUrl
		) {
			this.rowId = rowId;
			this.localId = localId;
			this.type = type;
			this.guildId = guildId;
			this.targetId = targetId;
			this.targetTag = targetTag;
			this.modId = modId;
			this.modTag = modTag;
			this.reason = reason;
			this.timeStart = timeStart;
			this.duration = Optional.ofNullable(duration).orElse(Duration.ofSeconds(-1));
			this.active = active;
			this.logUrl = logUrl;
		}

		public CaseData(Map<String, Object> map) {
			this.rowId = requireNonNull(map.get("rowId"));
			this.localId = requireNonNull(map.get("localId"));
			this.type = CaseType.byType(requireNonNull(map.get("type")));
			this.guildId = requireNonNull(map.get("guildId"));
			this.targetId = requireNonNull(map.get("targetId"));
			this.targetTag = getOrDefault(map.get("targetTag"), null);
			this.modId = getOrDefault(map.get("modId"), 0L);
			this.modTag = getOrDefault(map.get("modTag"), null);
			this.reason = getOrDefault(map.get("reason"), null);
			this.timeStart = Instant.ofEpochSecond(getOrDefault(map.get("timeStart"), 0L));
			this.duration = Duration.ofSeconds(getOrDefault(map.get("duration"), -1L));
			this.active = ((Integer) requireNonNull(map.get("active"))) == 1;
			this.logUrl = getOrDefault(map.get("logUrl"), null);
		}

		public int getRowId() {
			return rowId;
		}

		@NotNull
		public String getLocalId() {
			return String.valueOf(localId);
		}

		public int getLocalIdInt() {
			return localId;
		}

		@NotNull
		public CaseType getType() {
			return type;
		}

		public long getTargetId() {
			return targetId;
		}
		@NotNull
		public String getTargetTag() {
			return targetTag;
		}

		public long getModId() {
			return modId;
		}
		@Nullable
		public String getModTag() {
			return modTag;
		}

		public long getGuildId() {
			return guildId;
		}

		@NotNull
		public String getReason() {
			return reason!=null ? reason : "-none-";
		}

		@NotNull
		public Instant getTimeStart() {
			return timeStart;
		}

		@Nullable
		public Instant getTimeEnd() {
			return duration.isZero() ? null : timeStart.plus(duration);
		}

		/**
		 * @return Not-null duration, where negative - "not expirable type",
		 * 0 duration - permanent action, and positive - expirable action.
		 */
		@NotNull
		public Duration getDuration() {
			return duration;
		}

		public boolean isActive() {
			return active;
		}

		@Nullable
		public String getLogUrl() {
			return logUrl;
		}
	}

}

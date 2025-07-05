package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.Nullable;

import static dev.fireatom.FABI.utils.CastUtil.castLong;
import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;

@SuppressWarnings("unused")
public class ServerBlacklistManager extends LiteBase {
	
	public ServerBlacklistManager(ConnectionUtil cu) {
		super(cu, "serverBlacklist");
	}

	public void add(long guildId, int groupId, long userId, @Nullable String reason, long modId) {
		try {
			execute("INSERT INTO %s(guildId, groupId, userId, reason, modId) VALUES (%s, %s, %s, %s, %s)"
				.formatted(table, guildId, groupId, userId, quote(reason), modId));
		} catch (SQLException ignored) {}
	}

	public boolean inGroupUser(int groupId, long userId) {
		return selectOne("SELECT userId FROM %s WHERE (groupId=%d AND userId=%d)"
			.formatted(table, groupId, userId), "userId", Long.class) != null;
	}

	public void removeUser(int groupId, long userId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d AND userId=%d)".formatted(table, groupId, userId));
	}

	public BlacklistData getInfo(int groupId, long userId) {
		Map<String, Object> data = selectOne("SELECT * FROM %s WHERE (groupId=%d AND userId=%d)"
			.formatted(table, groupId, userId), Set.of("guildId", "reason", "modId"));
		return (data==null || data.isEmpty()) ? null : new BlacklistData(data);
	}

	public List<BlacklistData> getByPage(int groupId, int page) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (groupId=%d) ORDER BY userId DESC LIMIT 20 OFFSET %d"
			.formatted(table, groupId, (page-1)*20), Set.of("guildId", "userId", "reason", "modId"));
		return (data.isEmpty()) ? List.of() : data.stream()
			.map(BlacklistData::new)
			.toList();
	}

	public List<BlacklistData> searchUserId(long userId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (userId=%d) "
			.formatted(table, userId), Set.of("guildId", "groupId", "userId", "reason", "modId"));
		return (data.isEmpty()) ? List.of() : data.stream()
			.map(BlacklistData::new)
			.toList();
	}

	public Integer countEntries(int groupId) {
		return count("SELECT COUNT(*) FROM %s WHERE (groupId=%d)".formatted(table, groupId));
	}

	public class BlacklistData {
		private final long guildId, modId;
		private final Long userId;
		private final Integer groupId;
		private final String reason;

		BlacklistData(Map<String, Object> data) {
			this.guildId = castLong(data.get("guildId"));
			this.groupId = getOrDefault(data.get("groupId"), null);
			this.userId = castLong(data.get("userId"));
			this.modId = castLong(data.get("modId"));
			this.reason = String.valueOf(data.get("reason"));
		}

		public long getGuildId() {
			return guildId;
		}

		public Integer getGroupId() {
			return groupId;
		}

		public Long getUserId() {
			return userId;
		}

		public long getModId() {
			return modId;
		}

		public String getReason() {
			return reason;
		}
	}

}

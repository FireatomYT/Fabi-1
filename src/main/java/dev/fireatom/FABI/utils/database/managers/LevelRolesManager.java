package dev.fireatom.FABI.utils.database.managers;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.objects.ExpType;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.fireatom.FABI.utils.CastUtil.requireNonNull;

public class LevelRolesManager extends LiteBase {
	// cache
	private final Cache<Long, LevelRoleData> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();

	public LevelRolesManager(ConnectionUtil cu) {
		super(cu, "levelRoles");
	}

	public void add(long guildId, int level, String roleIds, boolean exact, ExpType type) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, level, roles, exact, type) VALUES (%d, %d, %s, %d, %d) ON CONFLICT(guildId, level, type) DO UPDATE SET roles=%4$s, exact=%5$d, type=%6$d"
			.formatted(table, guildId, level, quote(roleIds), exact?1:0, type.ordinal()));
	}

	public void removeGuild(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(table, guildId));
	}

	public void remove(long guildId, int level) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%d AND level=%d)".formatted(table, guildId, level));
	}

	public Set<Long> getRoles(long guildId, int level, ExpType expType) {
		return getAllLevels(guildId).getRoles(expType, level);
	}

	@Nullable
	public LevelRoleData getAllLevels(long guildId) {
		return cache.get(guildId, this::getData);
	}

	private LevelRoleData getData(long guildId) {
		List<Map<String, Object>> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), Set.of("level", "roles", "type"));
		if (data.isEmpty()) return new LevelRoleData();
		return new LevelRoleData(data);
	}

	public int countLevels(long guildId) {
		return getAllLevels(guildId).size();
	}

	private void invalidateCache(long guildId) {
		cache.invalidate(guildId);
	}

	public class LevelRoleData {
		private final Map<Integer, Set<Long>> textRoles = new HashMap<>();
		private final Map<Integer, Set<Long>> voiceRoles = new HashMap<>();

		public LevelRoleData() {}

		public LevelRoleData(List<Map<String, Object>> data) {
			data.forEach(map -> {
				int typeValue = requireNonNull(map.get("type"));
				int level = requireNonNull(map.get("level"));
				Set<Long> roleIds = Stream.of(String.valueOf(map.get("roles")).split(";"))
					.map(Long::parseLong)
					.collect(Collectors.toSet());
				switch (typeValue) {
					case 0 -> {
						textRoles.put(level, roleIds);
						voiceRoles.put(level, roleIds);
					}
					case 1 -> textRoles.put(level, roleIds);
					case 2 -> voiceRoles.put(level, roleIds);
				}
			});
		}

		public Set<Long> getRoles(ExpType expType, int level) {
			return getAllRoles(expType).getOrDefault(level, Set.of());
		}

		public Map<Integer, Set<Long>> getAllRoles(ExpType expType) {
			return switch (expType) {
				case TEXT -> textRoles;
				case VOICE -> voiceRoles;
				default -> throw new IllegalStateException("Unexpected value: " + expType);
			};
		}

		public boolean existsAtLevel(int level) {
			return textRoles.containsKey(level) || voiceRoles.containsKey(level);
		}

		public int size() {
			return textRoles.size() + voiceRoles.size();
		}

		public boolean isEmpty() {
			return textRoles.isEmpty() && voiceRoles.isEmpty();
		}
	}
}

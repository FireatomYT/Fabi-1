package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;
import org.jetbrains.annotations.NotNull;

import static dev.fireatom.FABI.utils.CastUtil.castLong;

@SuppressWarnings("unused")
public class AccessManager extends LiteBase {

	private final String table_role = "accessRole";
	private final String table_user = "accessUser";

	// Cache
	private final Cache<Long, Map<Long, CmdAccessLevel>> roleCache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	private final Cache<Long, List<Long>> operatorCache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();
	
	public AccessManager(ConnectionUtil cu) {
		super(cu, null);
	}

	public void addRole(long guildId, long roleId, CmdAccessLevel level) throws SQLException {
		invalidateRoleCache(guildId);
		execute("INSERT INTO %s(guildId, roleId, level) VALUES (%s, %s, %d)".formatted(table_role, guildId, roleId, level.getLevel()));
	}

	public void addOperator(long guildId, long userId) throws SQLException {
		invalidateOperatorCache(guildId);
		execute("INSERT INTO %s(guildId, userId, level) VALUES (%s, %s, %d)".formatted(table_user, guildId, userId, CmdAccessLevel.OPERATOR.getLevel()));
	}

	public void removeRole(long guildId, long roleId) throws SQLException {
		invalidateRoleCache(guildId);
		execute("DELETE FROM %s WHERE (roleId=%s)".formatted(table_role, roleId));
	}
	
	public void removeUser(long guildId, long userId) throws SQLException {
		invalidateOperatorCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId));
	}

	public void removeAll(long guildId) throws SQLException {
		invalidateRoleCache(guildId);
		invalidateOperatorCache(guildId);
		execute("DELETE FROM %1$s WHERE (guildId=%3$s); DELETE FROM %2$s WHERE (guildId=%3$s);".formatted(table_role, table_user, guildId));
	}

	public CmdAccessLevel getRoleLevel(long roleId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "level", Integer.class);
		if (data == null) return CmdAccessLevel.ALL;
		return CmdAccessLevel.byLevel(data);
	}

	public CmdAccessLevel getUserLevel(long guildId, long userId) {
		Integer data = selectOne("SELECT level FROM %s WHERE (guildId=%s AND userId=%s)".formatted(table_user, guildId, userId), "level", Integer.class);
		if (data == null) return null;
		return CmdAccessLevel.byLevel(data);
	}

	@NotNull
	public Map<Long, CmdAccessLevel> getAllRoles(long guildId) {
		return roleCache.get(guildId, id -> applyOrDefault(getRoleData(id), this::parseRoleData, Map.of()));
	}

	public List<Long> getRoles(long guildId, CmdAccessLevel level) {
		return select("SELECT roleId FROM %s WHERE (guildId=%s AND level=%s)".formatted(table_role, guildId, level.getLevel()), "roleId", Long.class);
	}

	public List<Long> getOperators(long guildId) {
		return operatorCache.get(guildId, this::getOperatorsData);
	}

	public boolean isRole(long roleId) {
		return selectOne("SELECT roleId FROM %s WHERE (roleId=%s)".formatted(table_role, roleId), "roleId", Long.class) != null;
	}

	public boolean isOperator(long guildId, long userId) {
		return getOperators(guildId).contains(userId);
	}

	private List<Map<String, Object>> getRoleData(long guildId) {
		return select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table_role, guildId), Set.of("roleId", "level"));
	}

	private List<Long> getOperatorsData(long guildId) {
		return select("SELECT userId FROM %s WHERE (guildId=%d and level=%d)"
			.formatted(table_user, guildId, CmdAccessLevel.OPERATOR.getLevel()), "userId", Long.class);
	}

	private void invalidateRoleCache(long guildId) {
		roleCache.invalidate(guildId);
	}

	private void invalidateOperatorCache(long guildId) {
		operatorCache.invalidate(guildId);
	}

	public Map<Long, CmdAccessLevel> parseRoleData(List<Map<String, Object>> data) {
		if (data == null || data.isEmpty()) return Map.of();
		return data.stream().collect(Collectors.toMap(k-> castLong(k.get("roleId")), k-> CmdAccessLevel.byLevel((int) k.get("level"))));
	}

	public int countRoles(long guildId) {
		return getAllRoles(guildId).size();
	}

	public int countOperators(long guildId) {
		return getOperators(guildId).size();
	}

}

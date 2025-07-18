package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.List;

import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

public class GroupManager extends LiteBase {

	private final String groups = "groups";
	private final String members = "groupMembers";
	
	public GroupManager(ConnectionUtil cu) {
		super(cu, null);
	}

	// groups table
	public int create(long guildId, String name, long appealGuildId) throws SQLException {
		return executeWithRow("INSERT INTO %s(ownerId, name, appealGuildId) VALUES (%d, %s, %d)"
			.formatted(groups, guildId, quote(name), appealGuildId));
	}

	public void deleteGroup(int groupId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d)".formatted(groups, groupId));
	}

	public void deleteGuildGroups(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (ownerId=%d)".formatted(groups, guildId));
	}

	public void rename(int groupId, String name) throws SQLException {
		execute("UPDATE %s SET name=%s WHERE (groupId=%d)".formatted(groups, quote(name), groupId));
	}

	public Long getOwner(int groupId) {
		return selectOne("SELECT ownerId FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "ownerId", Long.class);
	}

	public List<Integer> getOwnedGroups(long guildId) {
		return select("SELECT groupId FROM %s WHERE (ownerId=%d)".formatted(groups, guildId), "groupId", Integer.class);
	}

	public String getName(int groupId) {
		return selectOne("SELECT name FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "name", String.class);
	}

	public boolean isOwner(int groupId, long guildId) {
		return selectOne("SELECT ownerId FROM %s WHERE (groupId=%d AND ownerId=%d)"
			.formatted(groups, groupId, guildId), "ownerId", Long.class) != null;
	}

	public void setAppealGuildId(int groupId, long appealGuildId) throws SQLException {
		execute("UPDATE %s SET appealGuildId=%s WHERE (groupId=%d)".formatted(groups, appealGuildId, groupId));
	}

	public Long getAppealGuildId(int groupId) {
		Long data = selectOne("SELECT appealGuildId FROM %s WHERE (groupId=%d)".formatted(groups, groupId), "appealGuildId", Long.class);
		return data==null ? 0L : data;
	}

	public void setInvite(int groupId, int invite) throws SQLException {
		execute("UPDATE %s SET invite=%s WHERE (groupId=%s)".formatted(groups, invite, groupId));
	}

	public Integer getInvite(int groupId) {
		return selectOne("SELECT invite FROM %s WHERE (groupId=%s)".formatted(groups, groupId), "invite", Integer.class);
	}

	public Integer getGroupByInvite(int invite) {
		return selectOne("SELECT groupId FROM %s WHERE (invite=%s)".formatted(groups, invite), "groupId", Integer.class);
	}

	public int countOwnedGroups(long guildId) {
		return count("SELECT COUNT(*) FROM %s WHERE (ownerId=%d)".formatted(groups, guildId));
	}

	// groupMembers table
	public void add(int groupId, long guildId, boolean canManage) throws SQLException {
		execute("INSERT INTO %s(groupId, guildId, canManage) VALUES (%d, %d, %d)".formatted(members, groupId, guildId, canManage ? 1 : 0));
	}

	public void remove(int groupId, long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d AND guildId=%d)".formatted(members, groupId, guildId));
	}

	public void removeGuildFromGroups(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId=%d)".formatted(members, guildId));
	}
	
	public void clearGroup(int groupId) throws SQLException {
		execute("DELETE FROM %s WHERE (groupId=%d)".formatted(members, groupId));
	}

	public Boolean isMember(int groupId, long guildId) {
		return selectOne("SELECT guildId FROM %s WHERE (groupId=%d AND guildId=%d)".formatted(members, groupId, guildId), "guildId", Long.class) != null;
	}

	public List<Long> getGroupMembers(int groupId) {
		return select("SELECT guildId FROM %s WHERE (groupId=%d)".formatted(members, groupId), "guildId", Long.class);
	}

	public int countMembers(int groupId) {
		return count("SELECT COUNT(guildId) FROM %s WHERE (groupId=%d)".formatted(members, groupId));
	}

	public List<Integer> getGuildGroups(long guildId) {
		return select("SELECT groupId FROM %s WHERE (guildId=%d)".formatted(members, guildId), "groupId", Integer.class);
	}

	public List<Integer> getManagedGroups(long guildId) {
		return select("SELECT groupId FROM %s WHERE (guildId=%d AND canManage=1)".formatted(members, guildId), "groupId", Integer.class);
	}

	public List<Long> getGroupManagers(int groupId) {
		return select("SELECT guildId FROM %s WHERE (groupId=%d AND canManage=1)".formatted(members, groupId), "guildId", Long.class);
	} 

	public boolean canManage(int groupId, long guildId) {
		Integer data = selectOne("SELECT canManage FROM %s WHERE (groupId=%d AND guildId=%d)".formatted(members, groupId, guildId), "canManage", Integer.class);
		return data != null && data == 1;
	}

	public void setManage(int groupId, long guildId, boolean canManage) throws SQLException {
		execute("UPDATE %s SET canManage=%d WHERE (groupId=%d AND guildId=%d)".formatted(members, canManage ? 1 : 0, groupId, guildId));
	}

	public int countJoinedGroups(long guildId) {
		return count("SELECT COUNT(*) FROM %s WHERE (guildId=%d)".formatted(members, guildId));
	}

}

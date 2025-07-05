package dev.fireatom.FABI.utils.database.managers;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

public class LogExemptionsManager extends LiteBase {

	// Cache
	private final Cache<Long, Set<Long>> cache = Caffeine.newBuilder()
		.maximumSize(Constants.DEFAULT_CACHE_SIZE)
		.build();

	public LogExemptionsManager(ConnectionUtil cu) {
		super(cu, "logExceptions");
	}

	public void addExemption(long guildId, long targetId) throws SQLException {
		invalidateCache(guildId);
		execute("INSERT INTO %s(guildId, targetId) VALUES (%s, %s)".formatted(table, guildId, targetId));
	}

	public void removeExemption(long guildId, long targetId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s AND targetId=%s)".formatted(table, guildId, targetId));
	}

	public void removeGuild(long guildId) throws SQLException {
		invalidateCache(guildId);
		execute("DELETE FROM %s WHERE (guildId=%s)".formatted(table, guildId));
	}

	public boolean isExemption(long guildId, long targetId) {
		return getExemptions(guildId).contains(targetId);
	}

	public Set<Long> getExemptions(long guildId) {
		return cache.get(guildId, this::getData);
	}

	public Set<Long> getData(long guildId) {
		List<Long> data = select("SELECT * FROM %s WHERE (guildId=%d)".formatted(table, guildId), "targetId", Long.class);
		return data.isEmpty() ? Set.of() : new HashSet<>(data);
	}

	public int countExemptions(long guildId) {
		return getExemptions(guildId).size();
	}

	private void invalidateCache(long guildId) {
		cache.invalidate(guildId);
	}

}

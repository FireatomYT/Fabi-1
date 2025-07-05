package dev.fireatom.FABI.utils.database.managers;

import dev.fireatom.FABI.utils.database.ConnectionUtil;
import dev.fireatom.FABI.utils.database.LiteBase;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModReportManager extends LiteBase {
	public ModReportManager(ConnectionUtil cu) {
		super(cu, "modreport");
	}

	public void setup(long guildId, long channelId, String roleIds, LocalDateTime nextReport, int interval) throws SQLException {
		execute(("INSERT INTO %s(guildId, channelId, roleIds, nextReport, interval) VALUES (%d, %d, %s, %d, %d)"+
			"ON CONFLICT(guildId) DO UPDATE SET channelId=%3$d, roleIds=%4$s, nextReport=%5$d, interval=%6$d"
		).formatted(table, guildId, channelId, quote(roleIds), nextReport.toEpochSecond(ZoneOffset.UTC), interval));
	}

	public void removeGuild(long guildId) throws SQLException {
		execute("DELETE FROM %s WHERE (guildId = %d)".formatted(table, guildId));
	}

	public void updateNext(long channelId, LocalDateTime nextReport) throws SQLException {
		execute("UPDATE %s SET nextReport = %d WHERE (channelId = %d)"
			.formatted(table, nextReport.toEpochSecond(ZoneOffset.UTC), channelId));
	}

	public List<Map<String, Object>> getExpired() {
		List<Map<String, Object>> list = select("SELECT * FROM %s WHERE (nextReport<=%d)"
				.formatted(table, Instant.now().getEpochSecond()),
			Set.of("guildId", "channelId", "roleIds", "nextReport", "interval")
		);
		if (list.isEmpty()) return List.of();
		return list;
	}
}

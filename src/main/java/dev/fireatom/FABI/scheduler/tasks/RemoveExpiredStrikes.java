package dev.fireatom.FABI.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.fireatom.FABI.utils.CastUtil.castLong;

public class RemoveExpiredStrikes implements Task {

	private final Logger LOG = (Logger) LoggerFactory.getLogger(RemoveExpiredStrikes.class);

	@Override
	public void handle(App bot) {
		for (Map<String, Object> data : bot.getDBUtil().strikes.getExpired()) {
			try {
				Long guildId = castLong(data.get("guildId"));
				Long userId = castLong(data.get("userId"));
				Integer strikes = (Integer) data.get("count");

				if (strikes <= 0) {
					// Should not happen...
					bot.getDBUtil().strikes.removeGuildUser(guildId, userId);
				} else if (strikes == 1) {
					// One strike left, remove user
					bot.getDBUtil().strikes.removeGuildUser(guildId, userId);
					// set case inactive
					bot.getDBUtil().cases.setInactiveStrikeCases(userId, guildId);
				} else {
					String[] cases = String.valueOf(data.getOrDefault("data", "")).split(";");
					// Update data
					if (cases[0].isEmpty()) {
						bot.getDBUtil().strikes.removeGuildUser(guildId, userId);
						LOG.error("Strike data is empty. Data at guild '{}', user '{}'", guildId, userId);
					} else {
						String[] caseInfo = cases[0].split("-");
						String caseRowId = caseInfo[0];
						int newCount = Integer.parseInt(caseInfo[1]) - 1;

						StringBuilder newData = new StringBuilder();
						if (newCount > 0) {
							newData.append(caseRowId).append("-").append(newCount);
							if (cases.length > 1)
								newData.append(";");
						} else {
							// Set case inactive
							bot.getDBUtil().cases.setInactive(Integer.parseInt(caseRowId));
						}
						if (cases.length > 1) {
							List<String> list = new ArrayList<>(List.of(cases));
							list.removeFirst();
							newData.append(String.join(";", list));
						}
						// Remove one strike and reset time
						bot.getDBUtil().strikes.removeStrike(guildId, userId,
							Instant.now().plus(bot.getDBUtil().getGuildSettings(guildId).getStrikeExpires()),
							1, newData.toString()
						);
					}
				}
			} catch (SQLException ex) {
				LOG.error("Database error when removing expired strikes", ex);
			}
		}
	}
}

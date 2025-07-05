package dev.fireatom.FABI.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;
import dev.fireatom.FABI.utils.database.managers.LevelManager;
import dev.fireatom.FABI.utils.level.PlayerObject;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Iterator;

public class DrainDbQueue implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(DrainDbQueue.class);

	@Override
	public void handle(App bot) {
		if (bot.getLevelUtil().getUpdateQueue().isEmpty()) {
			return;
		}

		Iterator<PlayerObject> it = bot.getLevelUtil().getUpdateQueue().iterator();
		int updated = 0;
		while (it.hasNext()) {
			PlayerObject player = it.next();
			LevelManager.PlayerData playerData = bot.getDBUtil().levels.getPlayer(player);
			if (playerData == null) continue;

			try {
				bot.getDBUtil().levels.updatePlayer(player, playerData);
			} catch (SQLException e) {
				LOG.error("Failed to update player: {}", player);
				continue;
			}

			it.remove();
			updated++;
		}
		if (updated>0) {
			LOG.debug("Updated data for {} players", updated);
		}
	}
}

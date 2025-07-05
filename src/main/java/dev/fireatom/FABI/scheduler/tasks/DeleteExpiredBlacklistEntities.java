package dev.fireatom.FABI.scheduler.tasks;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;

public class DeleteExpiredBlacklistEntities implements Task {
	@Override
	public void handle(App bot) {
		if (bot.getBlacklist() == null) return;

		synchronized (bot.getBlacklist().getBlacklistEntities()) {
			bot.getBlacklist()
				.getBlacklistEntities()
				.entrySet()
				.removeIf(e -> !e.getValue().isBlacklisted());
		}
	}
}

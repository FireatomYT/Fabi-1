package dev.fireatom.FABI.scheduler.tasks;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;

public class RemoveExpiredPersistentRoles implements Task {

	@Override
	public void handle(App bot) {
		bot.getDBUtil().persistent.removeExpired();
	}

}

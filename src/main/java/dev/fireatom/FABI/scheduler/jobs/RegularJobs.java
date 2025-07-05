package dev.fireatom.FABI.scheduler.jobs;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Job;
import dev.fireatom.FABI.scheduler.tasks.DeleteExpiredBlacklistEntities;
import dev.fireatom.FABI.scheduler.tasks.DrainDbQueue;
import dev.fireatom.FABI.scheduler.tasks.RemoveExpiredCases;

import java.util.concurrent.TimeUnit;

public class RegularJobs extends Job {

	private final DrainDbQueue drainDbQueue = new DrainDbQueue();
	private final RemoveExpiredCases removeExpiredCases = new RemoveExpiredCases();
	private final DeleteExpiredBlacklistEntities deleteExpiredBlacklistEntities = new DeleteExpiredBlacklistEntities();

	public RegularJobs(App bot) {
		super(bot, 0, 1, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		handleTask(
			drainDbQueue,
			removeExpiredCases,
			deleteExpiredBlacklistEntities
		);
	}
}

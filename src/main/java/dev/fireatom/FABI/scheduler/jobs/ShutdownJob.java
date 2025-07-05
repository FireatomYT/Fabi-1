package dev.fireatom.FABI.scheduler.jobs;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Job;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class ShutdownJob extends Job {
	public ShutdownJob(App bot) {
		super(bot, 20, 20, TimeUnit.SECONDS);
	}

	@Override
	public void run() {
		if (bot.getShutdownTime() == null || bot.getShutdownTime().isAfter(Instant.now())) {
			return;
		}

		bot.shutdown(bot.getShutdownCode());
	}
}

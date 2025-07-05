package dev.fireatom.FABI.scheduler;

import dev.fireatom.FABI.contracts.scheduler.Job;
import dev.fireatom.FABI.utils.CountingThreadFactory;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

public class ScheduleHandler {

	private static final Set<ScheduledFuture<?>> tasks = new HashSet<>();
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3,
		new CountingThreadFactory("VOTL", "Jobs", false));

	public static void registerJob(@NotNull Job job) {
		tasks.add(scheduler.scheduleAtFixedRate(job, job.getDelay(), job.getPeriod(), job.getUnit()));
	}

	public static Set<ScheduledFuture<?>> entrySet() {
		return tasks;
	}

	public static ScheduledExecutorService getScheduler() {
		return scheduler;
	}

}

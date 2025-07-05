package dev.fireatom.FABI.contracts.scheduler;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.reflection.Reflectional;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public abstract class Job extends TimerTask implements Reflectional {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(Job.class);

	protected final App bot;

	private final long delay;
	private final long period;
	private final TimeUnit unit;

	public Job(App bot) {
		this(bot, 0);
	}

	public Job(App bot, long delay) {
		this(bot, delay, 1);
	}

	public Job(App bot, long delay, long period) {
		this(bot, delay, period, TimeUnit.MINUTES);
	}

	public Job(final App bot, final long delay, final long period, final TimeUnit unit) {
		this.bot = bot;
		this.delay = delay;
		this.period = period;
		this.unit = unit;
	}

	public long getDelay() {
		return delay;
	}

	public long getPeriod() {
		return period;
	}

	public TimeUnit getUnit() {
		return unit;
	}

	protected void handleTask(Task... tasks) {
		for (Task task : tasks) {
			try {
				LOG.trace("Invoking {}#handle(bot)", task.getClass().getName());
				task.handle(bot);
			} catch (Exception e) {
				LOG.error("An error occurred while running the {} class, message: {}",
					task.getClass().getName(), e.getMessage(), e
				);
			}
		}
	}

	@Override
	public int hashCode() {
		return Objects.hash(bot, delay, period, unit);
	}

}

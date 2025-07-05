package dev.fireatom.FABI.scheduler.jobs;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Job;
import dev.fireatom.FABI.scheduler.tasks.*;
import dev.fireatom.FABI.scheduler.tasks.*;

import java.util.concurrent.TimeUnit;

public class IrregularJobs extends Job {

	private final MarkTickets markTickets = new MarkTickets();
	private final CloseMarkedTickets closeMarkedTickets = new CloseMarkedTickets();
	private final CloseEmptyTickets closeEmptyTickets = new CloseEmptyTickets();
	private final RemoveTempRoles removeTempRoles = new RemoveTempRoles();
	private final RemoveExpiredStrikes removeExpiredStrikes = new RemoveExpiredStrikes();
	private final RemoveExpiredPersistentRoles removeExpiredPersistentRoles = new RemoveExpiredPersistentRoles();
	private final GenerateReport generateReport = new GenerateReport();

	public IrregularJobs(App bot) {
		super(bot, 1, 10, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		handleTask(
			markTickets,
			closeMarkedTickets,
			closeEmptyTickets,
			removeTempRoles,
			removeExpiredStrikes,
			removeExpiredPersistentRoles,
			generateReport
		);
	}
}

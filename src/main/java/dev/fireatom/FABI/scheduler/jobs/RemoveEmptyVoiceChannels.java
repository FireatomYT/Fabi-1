package dev.fireatom.FABI.scheduler.jobs;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Job;

import java.util.concurrent.TimeUnit;

public class RemoveEmptyVoiceChannels extends Job {

	public RemoveEmptyVoiceChannels(App bot) {
		super(bot, 2, 2, TimeUnit.MINUTES);
	}

	@Override
	public void run() {
		bot.getDBUtil().voice.checkCache(bot.JDA);
	}

}

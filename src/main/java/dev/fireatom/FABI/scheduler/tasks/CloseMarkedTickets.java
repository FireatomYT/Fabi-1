package dev.fireatom.FABI.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.LoggerFactory;

public class CloseMarkedTickets implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(CloseMarkedTickets.class);

	@Override
	public void handle(App bot) {
		bot.getDBUtil().tickets.getCloseMarkedTickets().forEach(channelId -> {
			GuildChannel channel = bot.JDA.getGuildChannelById(channelId);
			if (channel == null) {
				bot.getDBUtil().tickets.forceCloseTicket(channelId);
				return;
			}

			bot.getTicketUtil().closeTicket(channelId, null, "time", failure -> {
				bot.getDBUtil().tickets.setRequestStatus(channelId, -1L);
				if (ErrorResponse.UNKNOWN_MESSAGE.test(failure) || ErrorResponse.UNKNOWN_CHANNEL.test(failure)) return;
				LOG.error("Failed to delete channel {}", channelId, failure);
			});
		});
	}

}

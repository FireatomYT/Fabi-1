package dev.fireatom.FABI.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.LoggerFactory;

public class CloseEmptyTickets implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(CloseEmptyTickets.class);

	@Override
	public void handle(App bot) {
		bot.getDBUtil().tickets.getReplyExpiredTickets().forEach(channelId -> {
			GuildMessageChannel channel = bot.JDA.getChannelById(GuildMessageChannel.class, channelId);
			if (channel == null) {
				bot.getDBUtil().tickets.forceCloseTicket(channelId);
				return;
			}
			channel.getIterableHistory()
				.takeAsync(3)
				.thenAcceptAsync(list -> {
					boolean isAllBot = list.stream()
						.allMatch(msg -> msg.getAuthor().isBot());

					if (isAllBot) {
						// Last message is bot - close ticket
						bot.getTicketUtil().closeTicket(channelId, null, "activity", failure -> {
							bot.getDBUtil().tickets.setWaitTime(channelId, -1L);
							if (ErrorResponse.UNKNOWN_MESSAGE.test(failure) || ErrorResponse.UNKNOWN_CHANNEL.test(failure)) return;
							LOG.error("Failed to delete channel {}", channelId, failure);
						});
					} else {
						// There is human reply
						bot.getDBUtil().tickets.setWaitTime(channelId, -1L);
					}
				});
		});
	}

}

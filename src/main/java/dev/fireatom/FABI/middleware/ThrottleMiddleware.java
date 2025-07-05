package dev.fireatom.FABI.middleware;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.middleware.ThrottleMessage;
import dev.fireatom.FABI.contracts.middleware.Middleware;
import dev.fireatom.FABI.objects.constants.Constants;
import net.dv8tion.jda.api.entities.ISnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class ThrottleMiddleware extends Middleware {

	public static final Cache<String, ThrottleEntity> cache = Caffeine.newBuilder()
		.expireAfterWrite(60, TimeUnit.SECONDS)
		.build();

	public ThrottleMiddleware(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (args.length < 3) {
			App.getLogger().warn(
				"{} is parsing invalid amount of arguments to the throttle middleware, 3 arguments are required.", event.getFullCommandName()
			);
			return stack.next();
		}

		ThrottleType type = ThrottleType.fromName(args[0]);

		try {
			int maxAttempts = Integer.parseInt(args[1]);
			int decaySeconds = Integer.parseInt(args[2]);

			if (decaySeconds <= 0) {
				return stack.next();
			}

			ThrottleEntity entity = getEntityFromCache(type.genKey(event), maxAttempts, decaySeconds);
			if (entity.getHits() >= maxAttempts) {
				OffsetDateTime expires = bot.getBlacklist().getRatelimit().hit(type, event);

				if (expires != null) {
					sendBlacklistMessage(event, type==ThrottleType.USER ? event.getUser() : event.getMessageChannel(), expires);
					return false;
				}

				return cancelCommandThrottleRequest(event, stack, entity);
			}

			boolean response = stack.next();

			if (response) {
				entity.incrementHit();
			}

			return response;
		} catch (NumberFormatException ex) {
			App.getLogger().warn(
				"Invalid integers given to throttle middleware by {}, args: ({}, {})", event.getFullCommandName(), args[1], args[2]
			);
		}
		return false;
	}

	private boolean cancelCommandThrottleRequest(GenericCommandInteractionEvent event, MiddlewareStack stack, ThrottleEntity entity) {
		return runErrorCheck(event, () -> {
			String throttleMessage = bot.getLocaleUtil().getText(event, "errors.throttle.command",
				TimeFormat.RELATIVE.format(entity.getTime()));

			ThrottleMessage annotation = stack.getInteraction().getClass().getAnnotation(ThrottleMessage.class);
			if (annotation != null && !annotation.message().trim().isEmpty()) {
				if (annotation.overwrite()) {
					throttleMessage = annotation.message();
				} else {
					throttleMessage += annotation.message();
				}
			}

			sendErrorMsg(event, throttleMessage);

			return false;
		});
	}

	private ThrottleEntity getEntityFromCache(@NotNull String key, int maxAttempts, int decaySeconds) {
		ThrottleEntity entity = cache.get(key, (v) -> new ThrottleEntity(maxAttempts, decaySeconds));

		if (entity.hasExpired()) {
			cache.invalidate(key);
			return getEntityFromCache(key, maxAttempts, decaySeconds);
		}

		return entity;
	}

	private void sendBlacklistMessage(GenericCommandInteractionEvent event, Object o, OffsetDateTime expiresIn) {
		if (o instanceof User user) {
			sendBlacklistMessage(event, user, expiresIn);
		} else if (o instanceof MessageChannel channel) {
			sendBlacklistMessage(event, channel, expiresIn);
		}
	}

	private void sendBlacklistMessage(GenericCommandInteractionEvent event, User user, OffsetDateTime expiresIn) {
		user.openPrivateChannel()
			.flatMap(channel ->
				channel.sendMessageEmbeds(
					bot.getEmbedUtil()
						.getEmbed(Constants.COLOR_WARNING)
						.setDescription(
							bot.getLocaleUtil().getText(event, "errors.throttle.pm_user",
								TimeFormat.RELATIVE.format(expiresIn))
						)
						.setTimestamp(Instant.now())
						.build()
				)
			)
			.queue();
	}

	private void sendBlacklistMessage(GenericCommandInteractionEvent event, MessageChannel channel, OffsetDateTime expiresIn) {
		channel.sendMessageEmbeds(
			bot.getEmbedUtil()
				.getEmbed(Constants.COLOR_WARNING)
				.setDescription(
					bot.getLocaleUtil().getText(event, "errors.throttle.pm_guild",
						TimeFormat.RELATIVE.format(expiresIn))
				)
				.setTimestamp(Instant.now())
				.build()
		).queue();
	}

	public static class ThrottleEntity {
		private final int maxAttempts;
		private final long time;
		private int hit;

		ThrottleEntity(int maxAttempts, int decaySeconds) {
			this.time = Instant.now().plusSeconds(decaySeconds).toEpochMilli();
			this.maxAttempts = maxAttempts;
			this.hit = 0;
		}

		public int getHits() {
			return hit;
		}

		public void incrementHit() {
			hit++;
		}

		public int getMaxAttempts() {
			return maxAttempts;
		}

		public Instant getTime() {
			return Instant.ofEpochMilli(time);
		}

		public boolean hasExpired() {
			return Instant.now().isAfter(getTime());
		}
	}

	public enum ThrottleType {
		USER("U:%d", "User"),
		CHANNEL("C:%d", "Channel"),
		GUILD("G:%d", "Guild");

		private final String format;
		private final String name;

		ThrottleType(String format, String name) {
			this.format = format;
			this.name = name;
		}

		@NotNull
		public static ThrottleType fromName(String name) {
			for (ThrottleType type : ThrottleType.values()) {
				if (type.name().equalsIgnoreCase(name)) {
					return type;
				}
			}
			return USER;
		}

		public String genKey(GenericCommandInteractionEvent event) {
			long id = getSnowflake(event).getIdLong();
			if (this == GUILD && !event.isFromGuild()) {
				return USER.genKey(id);
			}
			return genKey(id);
		}

		public String genKey(long id) {
			return format.formatted(id);
		}

		public ISnowflake getSnowflake(GenericCommandInteractionEvent event) {
			return switch (this) {
				case GUILD -> event.isFromGuild() ? event.getGuild() : event.getUser();
				case CHANNEL -> event.getChannel();
				case USER -> event.getUser();
			};
		}

		public String getName() {
			return name;
		}
	}

}

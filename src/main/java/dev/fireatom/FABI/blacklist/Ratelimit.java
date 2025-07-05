package dev.fireatom.FABI.blacklist;

import ch.qos.logback.classic.Logger;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import dev.fireatom.FABI.contracts.blacklist.PunishmentLevel;
import dev.fireatom.FABI.middleware.ThrottleMiddleware;
import dev.fireatom.FABI.utils.message.TimeUtil;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Ratelimit {

	static final int hitLimit = 10;

	static final long hitTime = 30 * 1000; // 30 seconds

	public static final LoadingCache<Long, Rate> cache = Caffeine.newBuilder()
		.expireAfterWrite(hitTime, TimeUnit.MILLISECONDS)
		.build(Rate::new);

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(Ratelimit.class);

	private static final Map<Long, Integer> punishments = new HashMap<>();

	private static final List<PunishmentLevel> levels = List.of(
		() -> OffsetDateTime.now().plusMinutes(1),
		() -> OffsetDateTime.now().plusMinutes(15),
		() -> OffsetDateTime.now().plusMinutes(30),
		() -> OffsetDateTime.now().plusHours(1),
		() -> OffsetDateTime.now().plusHours(6),
		() -> OffsetDateTime.now().plusHours(12),
		() -> OffsetDateTime.now().plusDays(1),
		() -> OffsetDateTime.now().plusDays(3),
		() -> OffsetDateTime.now().plusWeeks(1)
	);

	private final Blacklist blacklist;

	Ratelimit(Blacklist blacklist) {
		this.blacklist = blacklist;
	}

	@Nullable
	public OffsetDateTime hit(ThrottleMiddleware.ThrottleType type, GenericCommandInteractionEvent event) {
		final long id = type.getSnowflake(event).getIdLong();

		Rate rate = cache.get(id);
		if (rate == null) {
			return null;
		}

		synchronized (rate) {
			rate.hit();

			if (rate.getHits() < hitLimit) {
				return null;
			}
		}

		Long last = rate.getLast();

		if (last != null && last < System.currentTimeMillis() - 2500) {
			return null;
		}

		OffsetDateTime punishment = getPunishment(id);

		LOG.info("{}:{} has been added to blacklist for excessive command usage, the blacklist expires {}.",
			type.getName(), id, TimeUtil.timeToString(punishment)
		);

		blacklist.addToBlacklist(
			type.equals(ThrottleMiddleware.ThrottleType.USER) ? Scope.USER : Scope.GUILD,
			id,
			"Automatic blacklist due to excessive command usage.",
			punishment
		);

		return punishment;
	}

	private OffsetDateTime getPunishment(long userId) {
		int level = punishments.getOrDefault(userId, -1) + 1;

		punishments.put(userId, level);

		return getPunishment(level);
	}

	private OffsetDateTime getPunishment(int level) {
		if (level < 0) {
			return levels.getFirst().generateTime();
		}
		return levels.get(level >= levels.size() ? levels.size()-1 : level).generateTime();
	}
}

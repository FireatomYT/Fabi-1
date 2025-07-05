package dev.fireatom.FABI.blacklist;

import dev.fireatom.FABI.App;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static dev.fireatom.FABI.utils.CastUtil.*;

public class Blacklist {

	private final App bot;
	private final Map<Long, BlacklistEntity> blacklist;
	private final Ratelimit ratelimit;

	public Blacklist(App bot) {
		this.bot = bot;

		this.blacklist = new HashMap<>();
		this.ratelimit = new Ratelimit(this);
	}

	public Ratelimit getRatelimit() {
		return ratelimit;
	}

	public boolean isBlacklisted(GenericInteractionCreateEvent event) {
		return isBlacklisted(event.getUser()) || (event.isFromGuild() && isBlacklisted(event.getGuild()));
	}

	public boolean isBlacklisted(@NotNull UserSnowflake user) {
		BlacklistEntity entity = getEntity(user.getIdLong());
		return entity != null && entity.isBlacklisted();
	}

	public boolean isBlacklisted(@NotNull Guild guild) {
		BlacklistEntity entity = getEntity(guild.getIdLong());
		return entity != null && entity.isBlacklisted();
	}

	public void addUser(@NotNull User user, @Nullable String reason) {
		addToBlacklist(Scope.USER, user.getIdLong(), reason);
	}

	public void addGuild(@NotNull Guild guild, @Nullable String reason) {
		addToBlacklist(Scope.GUILD, guild.getIdLong(), reason);
	}

	public void remove(long id) {
		blacklist.remove(id);

		try {
			bot.getDBUtil().blacklist.remove(id);
		} catch (SQLException e) {
			App.getLogger().error("Failed to sync blacklist with the database: {}", e.getMessage(), e);
		}
	}

	@Nullable
	public BlacklistEntity getEntity(long id) {
		return blacklist.get(id);
	}

	public void addToBlacklist(Scope scope, long id, @Nullable String reason) {
		addToBlacklist(scope, id, reason, null);
	}

	public void addToBlacklist(Scope scope, long id, @Nullable String reason, @Nullable OffsetDateTime expiresIn) {
		BlacklistEntity entity = getEntity(id);
		if (entity != null) {
			remove(id);
		}

		blacklist.put(id, new BlacklistEntity(scope, id, reason, expiresIn));

		try {
			bot.getDBUtil().blacklist.add(id, scope, expiresIn==null ? OffsetDateTime.now().plusYears(10) : expiresIn, reason);
		} catch (SQLException e) {
			App.getLogger().error("Failed to sync blacklist with the database: {}", e.getMessage(), e);
		}
	}

	/**
	 * Get blacklist entries.
	 * @return modifiable map of blacklist entries.
	 */
	public Map<Long, BlacklistEntity> getBlacklistEntities() {
		return blacklist;
	}

	public synchronized void syncBlacklistWithDatabase() {
		blacklist.clear();
		try {
			for (var map : bot.getDBUtil().blacklist.load()) {
				long id = requireNonNull(map.get("id"));
				Scope scope = Scope.fromId(getOrDefault(map.get("type"), 0));
				String reason = getOrDefault(map.get("reason"), null);
				OffsetDateTime expiresIn = resolveOrDefault(map.get("expiresIn"), o -> Instant.ofEpochSecond(castLong(o)).atOffset(ZoneOffset.UTC), null);

				blacklist.put(id, new BlacklistEntity(scope, id, reason, expiresIn));
			}
		} catch (Exception e) {
			App.getLogger().error("Failed to sync blacklist with the database: {}", e.getMessage(), e);
		}
	}
}

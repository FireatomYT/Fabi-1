package dev.fireatom.FABI.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

import static dev.fireatom.FABI.utils.CastUtil.castLong;

public class RemoveTempRoles implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(RemoveTempRoles.class);

	@Override
	public void handle(App bot) {
		bot.getDBUtil().tempRoles.expiredRoles().forEach(data -> {
			long roleId = castLong(data.get("roleId"));
			Role role = bot.JDA.getRoleById(roleId);
			if (role == null) {
				bot.getDBUtil().tempRoles.removeRole(roleId);
				return;
			}

			final long userId = castLong(data.get("userId"));
			if (bot.getDBUtil().tempRoles.shouldDelete(roleId)) {
				try {
					role.delete()
						.reason("Role expired")
						.queue(null, t -> failed(role, null, t));
				} catch (InsufficientPermissionException | HierarchyException ex) {
					failed(role, null, ex);
				}
				bot.getDBUtil().tempRoles.removeRole(roleId);
			} else {
				try {
					role.getGuild().removeRoleFromMember(User.fromId(userId), role)
						.reason("Role expired")
						.queue(null, t -> failed(role, userId, t));
					bot.getDBUtil().tempRoles.remove(roleId, userId);
				} catch (InsufficientPermissionException | HierarchyException ex) {
					failed(role, userId, ex);
				} catch (SQLException ignored) {}
			}
			// Log
			bot.getGuildLogger().role.onTempRoleAutoRemoved(role.getGuild(), userId, role);
		});
	}

	private <T extends Throwable> void failed(Role role, Long userId, T exception) {
		if (userId == null) {
			LOG.warn("Failed to delete temporary role '{}'", role.getIdLong(), exception);
		} else {
			LOG.warn("Failed to remove temporary role '{}' from '{}'", role.getIdLong(), userId, exception);
		}
	}

}

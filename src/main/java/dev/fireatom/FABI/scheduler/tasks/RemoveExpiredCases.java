package dev.fireatom.FABI.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.utils.database.managers.CaseManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class RemoveExpiredCases implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(RemoveExpiredCases.class);

	@Override
	public void handle(App bot) {
		List<CaseManager.CaseData> expired = bot.getDBUtil().cases.getExpired();
		if (expired.isEmpty()) {
			return;
		}

		for (CaseManager.CaseData caseData : expired) {
			try {
				bot.getDBUtil().cases.setInactive(caseData.getRowId());
			} catch (SQLException e) {
				LOG.warn("Failed to set case '{}' inactive", caseData.getRowId(), e);
				continue;
			}

			if (caseData.getType().equals(CaseType.BAN)) {
				Guild guild = bot.JDA.getGuildById(caseData.getGuildId());
				if (guild == null || !guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
					continue;
				}
				guild.unban(UserSnowflake.fromId(caseData.getTargetId()))
					.reason(bot.getLocaleUtil().getLocalized(bot.getLocaleUtil().getLocale(guild), "misc.ban_expired"))
					.queue(
						v -> bot.getGuildLogger().mod.onAutoUnban(caseData, guild),
						f -> LOG.warn("Failed to unban user {}, case '{}'", caseData.getTargetId(), caseData.getRowId(), f)
					);
			}
		}
	}

}

package dev.fireatom.FABI.scheduler.tasks;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.scheduler.Task;
import dev.fireatom.FABI.objects.ReportData;
import dev.fireatom.FABI.utils.encoding.EncodingUtil;
import dev.fireatom.FABI.utils.imagegen.renders.ModReportRender;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static dev.fireatom.FABI.utils.CastUtil.castLong;

public class GenerateReport implements Task {

	private static final Logger LOG = (Logger) LoggerFactory.getLogger(GenerateReport.class);

	@Override
	public void handle(App bot) {
		bot.getDBUtil().modReport.getExpired().forEach(data -> {
			long channelId = castLong(data.get("channelId"));
			TextChannel channel = bot.JDA.getTextChannelById(channelId);
			if (channel == null) {
				long guildId = castLong(data.get("guildId"));
				LOG.warn("Channel for modReport '{}' not found, deleting.", guildId);
				try {
					bot.getDBUtil().modReport.removeGuild(guildId);
				} catch (SQLException ignored) {}
				return;
			}

			Guild guild = channel.getGuild();
			String[] roleIds = String.valueOf(data.getOrDefault("roleIds", "")).split(";");
			List<Role> roles = Stream.of(roleIds)
				.map(guild::getRoleById)
				.toList();
			if (roles.isEmpty()) {
				LOG.warn("Roles for modReport '{}' not found, deleting.", guild.getId());
				try {
					bot.getDBUtil().modReport.removeGuild(guild.getIdLong());
				} catch (SQLException ignored) {}
				return;
			}

			Duration interval = Duration.ofDays((Integer) data.get("interval"));
			LocalDateTime nextReport = LocalDateTime.ofEpochSecond(castLong(data.get("nextReport")), 0, ZoneOffset.UTC);
			nextReport = interval.toDaysPart()==30 ? nextReport.plusMonths(1) : nextReport.plus(interval);
			// Update next report date
			// If fails - remove guild
			try {
				bot.getDBUtil().modReport.updateNext(channelId, nextReport);
			} catch (SQLException ignored) {
				try {
					bot.getDBUtil().modReport.removeGuild(guild.getIdLong());
				} catch (SQLException ignored2) {}
			}

			// Search for members with any of required roles (Mod, Admin, ...)
			guild.findMembers(m -> !Collections.disjoint(m.getRoles(), roles)).setTimeout(10, TimeUnit.SECONDS).onSuccess(members -> {
				if (members.isEmpty() || members.size() > 20) return; // TODO normal reply - too much users
				LocalDateTime now = LocalDateTime.now();
				LocalDateTime previous = (interval.toDaysPart()==30 ?
					now.minusMonths(1) :
					now.minus(interval)
				);

				List<ReportData> reportDataList = new ArrayList<>(members.size());
				members.forEach(m -> {
					if (m.getUser().isBot()) return;
					int countRoles = bot.getDBUtil().tickets.countTicketsByMod(
						guild.getIdLong(), m.getIdLong(), previous.toEpochSecond(ZoneOffset.UTC), now.toEpochSecond(ZoneOffset.UTC), true
					);
					Map<Integer, Integer> countCases = bot.getDBUtil().cases.countCasesByMod(guild.getIdLong(), m.getIdLong(), previous.toEpochSecond(ZoneOffset.UTC), now.toEpochSecond(ZoneOffset.UTC));
					ReportData reportData = new ReportData(m, countRoles, countCases);
					if (reportData.getCountTotalInt() > 0) {
						reportDataList.add(reportData);
					}
				});

				ModReportRender render = new ModReportRender(bot.getLocaleUtil().getLocale(guild), bot.getLocaleUtil(),
					previous, now, reportDataList);

				final String attachmentName = EncodingUtil.encodeModreport(guild.getIdLong(), now.toEpochSecond(ZoneOffset.UTC));

				try {
					channel.sendFiles(FileUpload.fromData(
						new ByteArrayInputStream(render.renderToBytes()),
						attachmentName
					)).queue();
				} catch (IOException e) {
					LOG.error("Failed to render and send mod report.", e);
				}
			});
		});
	}
}

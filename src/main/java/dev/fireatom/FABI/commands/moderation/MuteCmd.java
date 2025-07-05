package dev.fireatom.FABI.commands.moderation;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.objects.constants.Limits;
import dev.fireatom.FABI.utils.CaseProofUtil;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;
import dev.fireatom.FABI.utils.database.managers.GuildSettingsManager;
import dev.fireatom.FABI.utils.exception.AttachmentParseException;
import dev.fireatom.FABI.utils.exception.FormatterException;
import dev.fireatom.FABI.utils.message.TimeUtil;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class MuteCmd extends SlashCommand {
	
	public MuteCmd() {
		this.name = "mute";
		this.path = "bot.moderation.mute";
		this.options = List.of(
			new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
			new OptionData(OptionType.STRING, "time", lu.getText(path+".time.help"), true)
				.setMaxLength(12),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
				.setMaxLength(Limits.REASON_CHARS),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help"))
		);
		this.botPermissions = new Permission[]{Permission.MODERATE_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		addMiddlewares(
			"throttle:guild,2,20"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Member tm = event.optMember("user");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getUser().equals(tm.getUser()) || event.getJDA().getSelfUser().equals(tm.getUser())) {
			editError(event, path+".not_self");
			return;
		}

		final Duration duration;
		try {
			duration = TimeUtil.stringToDuration(event.optString("time"), false);
		} catch (FormatterException ex) {
			editError(event, ex.getPath());
			return;
		}
		if (duration.isZero()) {
			editError(event, path+".abort", "Duration must larger than 1 minute.");
			return;
		}
		if (duration.toDaysPart() > 28) {
			editError(event, path+".abort", "Maximum mute duration: 28 days.");
			return;
		}

		// Get proof
		final CaseProofUtil.ProofData proofData;
		try {
			proofData = CaseProofUtil.getData(event);
		} catch (AttachmentParseException e) {
			editError(event, e.getPath(), e.getMessage());
			return;
		}

		Guild guild = Objects.requireNonNull(event.getGuild());
		String reason = bot.getModerationUtil().parseReasonMentions(event, this);
		CaseData oldMuteData = bot.getDBUtil().cases.getMemberActive(tm.getIdLong(), guild.getIdLong(), CaseType.MUTE);

		if (tm.isTimedOut() && oldMuteData != null) {
			// Case already exists, change duration
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
				.setDescription(lu.getGuildText(event, path+".already_muted", oldMuteData.getLocalId()))
				.addField(lu.getGuildText(event, "logger.moderation.mute.short_title"), lu.getGuildText(event, "logger.moderation.mute.short_info")
					.replace("{username}", tm.getAsMention())
					.replace("{until}", TimeUtil.formatTime(tm.getTimeOutEnd(), false))
					, false)
				.build()
			);
		} else {
			// No case -> override current timeout
			// No case and not timed out -> timeout
			Member mod = event.getMember();
			if (!guild.getSelfMember().canInteract(tm)) {
				editError(event, path+".abort", "Bot can't interact with target member.");
				return;
			}
			if (bot.getCheckUtil().hasHigherAccess(tm, mod)) {
				editError(event, path+".higher_access");
				return;
			}
			if (!mod.canInteract(tm)) {
				editError(event, path+".abort", "You can't interact with target member.");
				return;
			}

			// timeout
			tm.timeoutFor(duration).reason(reason).queue(done -> {
				// inform
				final GuildSettingsManager.DramaLevel dramaLevel = bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaLevel();
				tm.getUser().openPrivateChannel().queue(pm -> {
					final String text = bot.getModerationUtil().getDmText(CaseType.MUTE, guild, reason, duration, mod.getUser(), false);
					if (text == null) return;
					pm.sendMessage(text).setSuppressEmbeds(true)
						.queue(null, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, (failure) -> {
							if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ONLY_BAD_DM)) {
								TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
									.map(event.getJDA()::getTextChannelById)
									.orElse(null);
								if (dramaChannel != null) {
									final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.MUTE, event.getGuild(), tm, reason, duration);
									if (dramaEmbed == null) return;
									dramaChannel.sendMessage("||%s||".formatted(tm.getAsMention()))
										.addEmbeds(dramaEmbed)
										.queue();
								}
							}
						}));
				});
				if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ALL)) {
					TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
						.map(event.getJDA()::getTextChannelById)
						.orElse(null);
					if (dramaChannel != null) {
						final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.MUTE, event.getGuild(), tm, reason, duration);
						if (dramaEmbed != null) {
							dramaChannel.sendMessageEmbeds(dramaEmbed).queue();
						}
					}
				}

				// Set previous mute case inactive, as member is not timed-out
				if (oldMuteData != null) {
					try {
						bot.getDBUtil().cases.setInactive(oldMuteData.getRowId());
					} catch (SQLException e) {
						editErrorDatabase(event, e, "Failed to set previous mute case inactive.");
						return;
					}
				}
				// add info to db
				CaseData newMuteData;
				try {
					newMuteData = bot.getDBUtil().cases.add(
						CaseType.MUTE, tm.getIdLong(), tm.getUser().getName(),
						mod.getIdLong(), mod.getUser().getName(),
						guild.getIdLong(), reason, duration
					);
				} catch (Exception ex) {
					editErrorDatabase(event, ex, "Failed to create new case.");
					return;
				}
				// log mute
				bot.getGuildLogger().mod.onNewCase(guild, tm.getUser(), newMuteData, proofData).thenAccept(logUrl -> {
					// Add log url to db
					bot.getDBUtil().cases.setLogUrl(newMuteData.getRowId(), logUrl);
					// send embed
					editEmbed(event, bot.getModerationUtil().actionEmbed(lu.getLocale(event), newMuteData.getLocalIdInt(),
						path+".success", tm.getUser(), mod.getUser(), reason, duration, logUrl)
					);
				});
			},
			failure -> editErrorOther(event, failure.getMessage()));
		}
	}

}

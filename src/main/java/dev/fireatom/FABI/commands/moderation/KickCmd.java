package dev.fireatom.FABI.commands.moderation;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Limits;
import dev.fireatom.FABI.utils.CaseProofUtil;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;

import dev.fireatom.FABI.utils.database.managers.GuildSettingsManager;
import dev.fireatom.FABI.utils.exception.AttachmentParseException;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.ErrorResponse;

public class KickCmd extends SlashCommand {

	public KickCmd () {
		this.name = "kick";
		this.path = "bot.moderation.kick";
		this.options = List.of(
			new OptionData(OptionType.USER, "member", lu.getText(path+".member.help"), true),
			new OptionData(OptionType.STRING, "reason", lu.getText(path+".reason.help"))
				.setMaxLength(Limits.REASON_CHARS),
			new OptionData(OptionType.ATTACHMENT, "proof", lu.getText(path+".proof.help")),
			new OptionData(OptionType.BOOLEAN, "dm", lu.getText(path+".dm.help"))
		);
		this.botPermissions = new Permission[]{Permission.KICK_MEMBERS};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.MOD;
		addMiddlewares(
			"throttle:guild,2,20"
		);
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		Guild guild = Objects.requireNonNull(event.getGuild());

		Member tm = event.optMember("member");
		if (tm == null) {
			editError(event, path+".not_found");
			return;
		}
		if (event.getMember().equals(tm) || guild.getSelfMember().equals(tm)) {
			editError(event, path+".not_self");
			return;
		}

		Member mod = event.getMember();
		if (!guild.getSelfMember().canInteract(tm)) {
			editError(event, path+".kick_abort", "Bot can't interact with target member.");
			return;
		}
		if (bot.getCheckUtil().hasHigherAccess(tm, mod)) {
			editError(event, path+".higher_access");
			return;
		}
		if (!mod.canInteract(tm)) {
			editError(event, path+".kick_abort", "You can't interact with target member.");
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

		String reason = bot.getModerationUtil().parseReasonMentions(event, this);
		// inform user
		final GuildSettingsManager.DramaLevel dramaLevel = bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaLevel();
		if (event.optBoolean("dm", true)) {
			tm.getUser().openPrivateChannel().queue(pm -> {
				final String text = bot.getModerationUtil().getDmText(CaseType.KICK, guild, reason, null, mod.getUser(), false);
				if (text == null) return;
				pm.sendMessage(text).setSuppressEmbeds(true)
					.queue(null, new ErrorHandler().handle(ErrorResponse.CANNOT_SEND_TO_USER, (failure) -> {
						if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ONLY_BAD_DM)) {
							TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
								.map(event.getJDA()::getTextChannelById)
								.orElse(null);
							if (dramaChannel != null) {
								final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.KICK, event.getGuild(), tm, reason, null);
								if (dramaEmbed == null) return;
								dramaChannel.sendMessage("||%s||".formatted(tm.getAsMention()))
									.addEmbeds(dramaEmbed)
									.queue();
							}
						}
					}));
			});
		}
		if (dramaLevel.equals(GuildSettingsManager.DramaLevel.ALL)) {
			TextChannel dramaChannel = Optional.ofNullable(bot.getDBUtil().getGuildSettings(event.getGuild()).getDramaChannelId())
				.map(event.getJDA()::getTextChannelById)
				.orElse(null);
			if (dramaChannel != null) {
				final MessageEmbed dramaEmbed = bot.getModerationUtil().getDramaEmbed(CaseType.KICK, event.getGuild(), tm, reason, null);
				if (dramaEmbed != null) {
					dramaChannel.sendMessageEmbeds(dramaEmbed).queue();
				}
			}
		}

		tm.kick().reason(reason).queueAfter(2, TimeUnit.SECONDS, done -> {
			// add info to db
			CaseData kickData;
			try {
				kickData = bot.getDBUtil().cases.add(
					CaseType.KICK, tm.getIdLong(), tm.getUser().getName(),
					mod.getIdLong(), mod.getUser().getName(),
					guild.getIdLong(), reason, null
				);
			} catch (Exception ex) {
				editErrorDatabase(event, ex, "Failed to create new case.");
				return;
			}
			// log kick
			bot.getGuildLogger().mod.onNewCase(guild, tm.getUser(), kickData, proofData).thenAccept(logUrl -> {
				// Add log url to db
				bot.getDBUtil().cases.setLogUrl(kickData.getRowId(), logUrl);
				// reply and ask for kick sync
				event.getHook().editOriginalEmbeds(
					bot.getModerationUtil().actionEmbed(lu.getLocale(event), kickData.getLocalIdInt(),
						path+".success", tm.getUser(), mod.getUser(), reason, logUrl)
				).setActionRow(
					Button.primary("sync_kick:"+tm.getId(), "Sync kick").withEmoji(Emoji.fromUnicode("🆑"))
				).queue();
			});
		},
		failure -> editErrorOther(event, failure.getMessage()));
	}
}

package dev.fireatom.FABI.utils.message;

import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EmbedUtil {

	private final LocaleUtil lu;

	public EmbedUtil(LocaleUtil localeUtil) {
		this.lu = localeUtil;
	}

	@NotNull
	public EmbedBuilder getEmbed(int color) {
		return new EmbedBuilder()
			.setColor(color);
	}

	@NotNull
	public EmbedBuilder getEmbed() {
		return getEmbed(Constants.COLOR_DEFAULT);
	}

	@NotNull
	public EmbedBuilder getEmbed(IReplyCallback replyCallback) {
		return getEmbed()
			.setFooter(
				lu.getText(replyCallback, "embed.footer", replyCallback.getUser().getName()),
				replyCallback.getUser().getEffectiveAvatarUrl()
			);
	}

	@NotNull
	private EmbedBuilder getErrorEmbed(IReplyCallback replyCallback) {
		return getEmbed()
			.setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getText(replyCallback, "errors.title"));
	}

	@NotNull
	public MessageEmbed getPermErrorEmbed(IReplyCallback replyCallback, Permission perm, boolean self) {
		return getPermErrorEmbed(replyCallback, null, perm, self);
	}

	@NotNull
	public MessageEmbed getPermErrorEmbed(IReplyCallback replyCallback, @Nullable GuildChannel channel, Permission perm, boolean self) {
		EmbedBuilder embed = getErrorEmbed(replyCallback);
		String msg;
		if (self) {
			if (channel == null) {
				msg = lu.getText(replyCallback, "errors.missing_perms.self",
					perm.getName());
			} else {
				msg = lu.getText(replyCallback, "errors.missing_perms.self_channel",
					perm.getName(), channel.getAsMention());
			}
		} else {
			if (channel == null) {
				msg = lu.getText(replyCallback, "errors.missing_perms.other",
					perm.getName());
			} else {
				msg = lu.getText(replyCallback, "errors.missing_perms.other_channel",
					perm.getName(), channel.getAsMention());
			}
		}

		return embed.setDescription(msg).build();
	}

	@NotNull
	public MessageEmbed getError(IReplyCallback replyCallback, @NotNull String path) {
		return getError(replyCallback, path, null);
	}

	@NotNull
	public MessageEmbed getError(IReplyCallback replyCallback, @NotNull String path, String details) {
		EmbedBuilder embedBuilder = getErrorEmbed(replyCallback)
			.setDescription(lu.getText(replyCallback, path));

		if (details != null)
			embedBuilder.addField(
				lu.getText(replyCallback, "errors.additional"),
				MessageUtil.limitString(details, 1024),
				false
			);

		return embedBuilder.build();
	}

	@NotNull
	public MessageCreateData createPermError(IReplyCallback replyCallback, Permission perm, boolean self) {
		return createPermError(replyCallback, null, perm, self);
	}

	@NotNull
	public MessageCreateData createPermError(IReplyCallback replyCallback, GuildChannel channel, Permission perm, boolean self) {
		MessageCreateBuilder mb = new MessageCreateBuilder();

		if (self && perm.equals(Permission.MESSAGE_EMBED_LINKS)) {
			if (channel == null) {
				mb.setContent(
					lu.getText(replyCallback, "errors.missing_perms.self",
						perm.getName())
				);
			} else {
				mb.setContent(
					lu.getText(replyCallback, "errors.missing_perms.self_channel",
						perm.getName(), channel.getAsMention())
				);
			}
		} else {
			mb.setEmbeds(getPermErrorEmbed(replyCallback, channel, perm, self));
		}
		return mb.build();
	}

	public void sendUnknownError(InteractionHook interactionHook, DiscordLocale locale, String reason) {
		interactionHook.sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
			.setTitle(lu.getLocalized(locale, "errors.title"))
			.setDescription(lu.getLocalized(locale, "errors.unknown"))
			.addField(lu.getLocalized(locale, "errors.additional"), MessageUtil.limitString(reason, 1024), false)
			.build()
		).setEphemeral(true).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));
	}

}

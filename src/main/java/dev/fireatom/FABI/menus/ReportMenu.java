package dev.fireatom.FABI.menus;

import dev.fireatom.FABI.base.command.MessageContextMenu;
import dev.fireatom.FABI.base.command.MessageContextMenuEvent;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.message.MessageUtil;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class ReportMenu extends MessageContextMenu {
	public ReportMenu() {
		this.name = "report";
		this.path = "menus.report";
		this.module = CmdModule.REPORT;
		addMiddlewares(
			"throttle:user,1,30"
		);
		this.ephemeral = true;
	}

	@Override
	protected void execute(MessageContextMenuEvent event) {
		event.getGuild().retrieveMember(event.getTarget().getAuthor()).queue(member -> {
			Long channelId = bot.getDBUtil().getGuildSettings(event.getGuild()).getReportChannelId();
			if (channelId == null || member.getUser().isBot() || member.hasPermission(Permission.ADMINISTRATOR)) {
				event.getHook().editOriginal(Constants.FAILURE).queue();
				return;
			}
			TextChannel channel = event.getGuild().getTextChannelById(channelId);
			if (channel == null) {
				event.getHook().editOriginal(Constants.FAILURE).queue();
				return;
			}

			MessageEmbed reportEmbed = getReportEmbed(event);
			Button delete = Button.danger("delete:%s:%s".formatted(event.getMessageChannel().getId(), event.getTarget().getId()), lu.getGuildText(event, path+".delete")).withEmoji(Emoji.fromUnicode("🗑️"));
			Button link = Button.link(event.getTarget().getJumpUrl(), lu.getGuildText(event, path+".link"));
			channel.sendMessageEmbeds(reportEmbed).addActionRow(link, delete).queue();

			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getGuildText(event, path+".done"))
				.build()
			).queue();
		}, failure -> event.getHook().editOriginal(Constants.FAILURE).queue());
		
	}

	private MessageEmbed getReportEmbed(MessageContextMenuEvent event) {
		String content = MessageUtil.limitString(event.getTarget().getContentStripped(), 1024);
		return new EmbedBuilder().setColor(Constants.COLOR_WARNING)
			.setTitle(lu.getGuildText(event, path+".title"))
			.addField(lu.getGuildText(event, path+".user"), event.getTarget().getAuthor().getAsMention(), true)
			.addField(lu.getGuildText(event, path+".channel"), event.getMessageChannel().getAsMention(), true)
			.addField(lu.getGuildText(event, path+".complain"), event.getMember().getAsMention(), false)
			.addField(lu.getGuildText(event, path+".content"), content, false)
			.setFooter("Message ID: %s".formatted(event.getTarget().getId()))
			.build();
	}
}

package dev.fireatom.FABI.commands.other;

import dev.fireatom.FABI.AppInfo;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.objects.constants.Links;

import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.entities.MessageEmbed;

public class AboutCmd extends SlashCommand {

	public AboutCmd() {
		this.name = "about";
		this.path = "bot.other.about";
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				lu.getText(event, "bot.other.about.embed.about_title")
					.replace("{name}", "VOTL bot"),
				lu.getText(event, "bot.other.about.embed.about_value")
					.replace("{developer_name}", Constants.DEVELOPER_TAG)
					.replace("{developer_id}", String.valueOf(Constants.DEVELOPER_ID)),
				false
			)
			.addField(
				lu.getText(event, "bot.other.about.embed.commands_title"),
				lu.getText(event, "bot.other.about.embed.commands_value"),
				false
			)
			.addField(
				lu.getText(event, "bot.other.about.embed.bot_info.title"),
				String.join(
					"\n",
					lu.getText(event, "bot.other.about.embed.bot_info.bot_version").replace("{bot_version}", AppInfo.VERSION),
					lu.getText(event, "bot.other.about.embed.bot_info.library")
						.replace("{jda_version}", JDAInfo.VERSION)
						.replace("{jda_github}", JDAInfo.GITHUB)
				),
				false
			)
			.addField(
				lu.getText(event, "bot.other.about.embed.links.title"),
				String.join(
					"\n",
					lu.getText(event, "bot.other.about.embed.links.discord").replace("{guild_invite}", Links.DISCORD),
					lu.getText(event, "bot.other.about.embed.links.github").replace("{github_url}", Links.GITHUB),
					lu.getText(event, "bot.other.about.embed.links.terms").replace("{terms_url}", Links.TERMS),
					lu.getText(event, "bot.other.about.embed.links.privacy").replace("{privacy_url}", Links.PRIVACY)
				),
				true
			)
			.addField(
				lu.getText(event, "bot.other.about.embed.links.translate"),
				"[Crowdin.com](%s)".formatted(Links.CROWDIN),
				false
			)
			.build();
		
		editEmbed(event, embed);
	}

}

package dev.fireatom.FABI.commands.other;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

public class StatusCmd extends SlashCommand {

	public StatusCmd() {
		this.name = "status";
		this.path = "bot.other.status";
		this.category = CmdCategory.OTHER;
		this.guildOnly = false;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {
		MessageEmbed embed = new EmbedBuilder().setColor(Constants.COLOR_DEFAULT)
			.setAuthor(event.getJDA().getSelfUser().getName(), event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.setThumbnail(event.getJDA().getSelfUser().getEffectiveAvatarUrl())
			.addField(
				lu.getText(event, "bot.other.status.embed.stats_title"),
				String.join(
					"\n",
					lu.getText(event, "bot.other.status.embed.stats.guilds",
						event.getJDA().getGuilds().size()),
					lu.getText(event, "bot.other.status.embed.stats.shard",
						event.getJDA().getShardInfo().getShardId() + 1, event.getJDA().getShardInfo().getShardTotal()),
					lu.getText(event, "bot.other.status.embed.stats.memory",
						(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024),
						Runtime.getRuntime().totalMemory() / (1024 * 1024)
					)
				),
				false
			)
			.addField(lu.getText(event, "bot.other.status.embed.shard_title"),
				String.join(
					"\n",
					lu.getText(event, "bot.other.status.embed.shard.users",
						event.getJDA().getUsers().size()),
					lu.getText(event, "bot.other.status.embed.shard.guilds",
						event.getJDA().getGuilds().size())
				),
				true
			)
			.addField("",
				String.join(
					"\n",
					lu.getText(event, "bot.other.status.embed.shard.text_channels",
						event.getJDA().getTextChannels().size()),
					lu.getText(event, "bot.other.status.embed.shard.voice_channels",
						event.getJDA().getVoiceChannels().size())
				),
				true
			)
			.setFooter(lu.getText(event, "bot.other.status.embed.last_restart"))
			.setTimestamp(event.getClient().getStartTime())
			.build();

		editEmbed(event, embed);
	}

}

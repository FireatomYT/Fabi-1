package dev.fireatom.FABI.commands.verification;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.base.waiter.EventWaiter;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import dev.fireatom.FABI.utils.exception.CheckException;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class VerifyPanelCmd extends SlashCommand {

	private final EventWaiter waiter;
	
	public VerifyPanelCmd() {
		this.name = "vfpanel";
		this.path = "bot.verification.vfpanel";
		this.children = new SlashCommand[]{
			new Create(), new Preview(), new SetText(), new SetImage()
		};
		this.botPermissions = new Permission[]{Permission.MESSAGE_SEND};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.waiter = App.getInstance().getEventWaiter();
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {
		public Create() {
			this.name = "create";
			this.path = "bot.verification.vfpanel.create";
			this.options = List.of(
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			GuildChannel channel = event.optGuildChannel("channel");
			if (channel == null ) {
				editError(event, path+".no_channel", "Received: No channel");
				return;
			}
			try {
				bot.getCheckUtil().hasPermissions(
					event,
					new Permission[]{Permission.VIEW_CHANNEL, Permission.MANAGE_WEBHOOKS},
					channel
				);
			} catch (CheckException ex) {
				editMsg(event, ex.getEditData());
				return;
			}
			TextChannel tc = (TextChannel) channel;

			if (bot.getDBUtil().getVerifySettings(guild).getRoleId() == null) {
				editError(event, path+".no_role");
				return;
			}

			Button next = Button.primary("verify", lu.getGuildText(event, path+".continue"));

			tc.sendMessageEmbeds(new EmbedBuilder()
				.setColor(bot.getDBUtil().getGuildSettings(guild).getColor())
				.setDescription(bot.getDBUtil().getVerifySettings(guild).getPanelText())
				.setImage(bot.getDBUtil().getVerifySettings(guild).getPanelImageUrl())
				.setFooter(event.getGuild().getName(), event.getGuild().getIconUrl())
				.build()
			).addActionRow(next).queue();

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", tc.getAsMention()))
				.build()
			);
		}
	}

	private class Preview extends SlashCommand {
		public Preview() {
			this.name = "preview";
			this.path = "bot.verification.vfpanel.preview";
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			int color = bot.getDBUtil().getGuildSettings(guild).getColor();
			
			MessageEmbed main = new EmbedBuilder().setColor(color)
				.setDescription(bot.getDBUtil().getVerifySettings(guild).getPanelText())
				.setImage(bot.getDBUtil().getVerifySettings(guild).getPanelImageUrl())
				.setFooter(event.getGuild().getName(), event.getGuild().getIconUrl())
				.build();

			editEmbed(event, main);
		}
	}

	private class SetText extends SlashCommand {
		public SetText() {
			this.name = "text";
			this.path = "bot.verification.vfpanel.text";
			addMiddlewares(
				"throttle:guild,1,30"
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			editMsg(event, lu.getGuildText(event, path+".send_text"));

			waiter.waitForEvent(
				MessageReceivedEvent.class,
				e -> e.getChannel().getIdLong() == event.getChannelIdLong() && e.getAuthor().getIdLong() == event.getUser().getIdLong(),
				msgEvent -> {
					String content = msgEvent.getMessage().getContentRaw();
					if (content.isBlank()) {
						editError(event, path+".empty");
						return;
					}
					if (content.length() > 1024) {
						editError(event, path+".too_long");
						return;
					}

					try {
						bot.getDBUtil().verifySettings.setPanelText(event.getGuild().getIdLong(), content);
					} catch (SQLException e) {
						editErrorDatabase(event, e, "Update verify panel text");
					}

					event.getHook()
						.editOriginal("")
						.setEmbeds(
							bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
								.setDescription(lu.getGuildText(event, path+".done"))
								.build(),
							new EmbedBuilder()
								.setTitle("TEXT")
								.setDescription(content)
								.build()
						)
						.queue();
				},
				30,
				TimeUnit.SECONDS,
				() -> editMsg(event, lu.getGuildText(event, path+".timed_out"))
			);
		}
	}

	private class SetImage extends SlashCommand {
		public final static Pattern URL_PATTERN = Pattern.compile("\\s*https?://\\S+\\s*", Pattern.CASE_INSENSITIVE);

		public SetImage() {
			this.name = "image";
			this.path = "bot.verification.vfpanel.image";
			this.options = List.of(
				new OptionData(OptionType.STRING, "image_url", lu.getText(path+".image_url.help"), true)
					.setMaxLength(200)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String imageUrl = event.optString("image_url");

			if (!imageUrl.equals("NULL") && !URL_PATTERN.matcher(imageUrl).matches()) {
				editError(event, path+".unknown_url", "URL: "+imageUrl);
				return;
			}
			try {
				bot.getDBUtil().verifySettings.setPanelImage(event.getGuild().getIdLong(), imageUrl);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set verify image");
				return;
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", imageUrl))
				.build()
			);
		}
	}

}

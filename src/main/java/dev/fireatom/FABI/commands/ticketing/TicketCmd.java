package dev.fireatom.FABI.commands.ticketing;

import java.net.URI;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.Limits;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.managers.TicketPanelManager.Panel;
import dev.fireatom.FABI.utils.database.managers.TicketSettingsManager;
import dev.fireatom.FABI.utils.database.managers.TicketTagManager.Tag;

import dev.fireatom.FABI.utils.message.MessageUtil;
import dev.fireatom.FABI.utils.message.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.Command.Choice;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;

public class TicketCmd extends SlashCommand {
	
	public TicketCmd() {
		this.name = "ticket";
		this.path = "bot.ticketing.ticket";
		this.children = new SlashCommand[]{
			new NewPanel(), new ModifyPanel(), new ViewPanel(), new SendPanel(), new DeletePanel(),
			new CreateTag(), new ModifyTag(), new ViewTag(), new DeleteTag(),
			new Automation(), new Settings()
		};
		this.category = CmdCategory.TICKETING;
		this.accessLevel = CmdAccessLevel.ADMIN;
		this.module = CmdModule.TICKETING;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	// Panel tools

	private class NewPanel extends SlashCommand {
		public NewPanel() {
			this.name = "new";
			this.path = "bot.ticketing.ticket.panels.new";
			this.options = List.of(
				new OptionData(OptionType.STRING, "embed_title", lu.getText(path+".embed_title.help"), true)
					.setMaxLength(256),
				new OptionData(OptionType.STRING, "embed_description", lu.getText(path+".embed_description.help"))
					.setMaxLength(2000),
				new OptionData(OptionType.STRING, "embed_image", lu.getText(path+".embed_image.help"))
					.setMaxLength(200),
				new OptionData(OptionType.STRING, "embed_footer", lu.getText(path+".embed_footer.help"))
					.setMaxLength(2048)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			if (bot.getDBUtil().ticketPanels.countPanels(event.getGuild().getIdLong()) >= Limits.TICKET_PANELS) {
				editErrorLimit(event, "panels", Limits.TICKET_PANELS);
				return;
			}

			String title = event.optString("embed_title");
			String description = event.optString("embed_description");
			String image = event.optString("embed_image");
			String footer = event.optString("embed_footer");

			if (isInvalidURL(image)) {
				editError(event, path+".image_not_valid", "Received invalid URL: `%s`".formatted(image));
				return;
			}

			final int panelId;
			try {
				panelId = bot.getDBUtil().ticketPanels.createPanel(event.getGuild().getIdLong(), title, description, image, footer);
			} catch (SQLException e) {
				editErrorOther(event, "Panel creation failed.");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", title, panelId))
				.build()
			);
		}
	}

	private class ModifyPanel extends SlashCommand {
		public ModifyPanel() {
			this.name = "modify";
			this.path = "bot.ticketing.ticket.panels.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true)
					.setMinValue(1),
				new OptionData(OptionType.STRING, "embed_title", lu.getText(path+".embed_title.help"))
					.setMaxLength(256),
				new OptionData(OptionType.STRING, "embed_description", lu.getText(path+".embed_description.help"))
					.setMaxLength(2000),
				new OptionData(OptionType.STRING, "embed_image", lu.getText(path+".embed_image.help"))
					.setMaxLength(200),
				new OptionData(OptionType.STRING, "embed_footer", lu.getText(path+".embed_footer.help"))
					.setMaxLength(2048)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			Long guildId = bot.getDBUtil().ticketPanels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}

			String title = event.optString("embed_title");
			String description = event.optString("embed_description");
			String image = event.optString("embed_image");
			String footer = event.optString("embed_footer");

			if (isInvalidURL(image)) {
				editError(event, path+".image_not_valid", "Received invalid URL: `%s`".formatted(image));
				return;
			}
			
			EmbedBuilder builder = bot.getEmbedUtil().getEmbed();
			if (title != null)			builder.addField(lu.getGuildText(event, path+".changed_title"), title, true);
			if (description != null)	builder.addField(lu.getGuildText(event, path+".changed_description"), description, true);
			if (image != null)			builder.addField(lu.getGuildText(event, path+".changed_image"), image, true);
			if (footer != null)			builder.addField(lu.getGuildText(event, path+".changed_footer"), footer, true);
			
			if (builder.getFields().isEmpty()) {
				editError(event, path+".no_options");
				return;
			}
			try {
				bot.getDBUtil().ticketPanels.updatePanel(panelId, title, description, image, footer);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "update panel");
				return;
			}
			editEmbed(event, builder.setColor(Constants.COLOR_SUCCESS)
				.setTitle(lu.getGuildText(event, path+".done"))
				.build());
		}
	}

	private class ViewPanel extends SlashCommand {
		public ViewPanel() {
			this.name = "view";
			this.path = "bot.ticketing.ticket.panels.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true)
					.setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			Long guildId = bot.getDBUtil().ticketPanels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}

			List<Button> buttons = bot.getDBUtil().ticketTags.getPanelTags(panelId);
			if (buttons.isEmpty())
				editEmbed(event, buildPanelEmbed(event.getGuild(), panelId));
			else
				event.getHook().editOriginalEmbeds(buildPanelEmbed(event.getGuild(), panelId)).setComponents(ActionRow.of(buttons).asDisabled()).queue(null,
					failure -> editErrorOther(event, failure.getMessage())
				);
		}
	}

	private class SendPanel extends SlashCommand {
		public SendPanel() {
			this.name = "send";
			this.path = "bot.ticketing.ticket.panels.send";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true)
					.setMinValue(1),
				new OptionData(OptionType.CHANNEL, "channel", lu.getText(path+".channel.help"), true)
					.setChannelTypes(ChannelType.TEXT)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			Long guildId = bot.getDBUtil().ticketPanels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}
			TextChannel channel = (TextChannel) event.optGuildChannel("channel");
			if (!channel.canTalk()) {
				editError(event, path+".cant_send", "Channel: %s".formatted(channel.getAsMention()));
				return;
			}

			List<Button> buttons = bot.getDBUtil().ticketTags.getPanelTags(panelId);
			if (buttons.isEmpty()) {
				channel.sendMessageEmbeds(buildPanelEmbed(event.getGuild(), panelId)).queue(done -> {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, path+".done", channel.getAsMention()))
						.build()
					);
				});
			} else {
				channel.sendMessageEmbeds(buildPanelEmbed(event.getGuild(), panelId)).setActionRow(buttons).queue(done -> {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, path+".done", channel.getAsMention()))
						.build()
					);
				},
					failure -> editErrorOther(event, failure.getMessage())
				);
			}
		}
	}

	private class DeletePanel extends SlashCommand {
		public DeletePanel() {
			this.name = "delete";
			this.path = "bot.ticketing.ticket.panels.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true)
					.setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("panels", lu.getText("bot.ticketing.ticket.panels.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			Long guildId = bot.getDBUtil().ticketPanels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}

			try {
				bot.getDBUtil().ticketPanels.delete(panelId);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "delete ticket panel");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", panelId))
				.build()
			);
		}
	}

	// Tag tools

	private class CreateTag extends SlashCommand {
		public CreateTag() {
			this.name = "create";
			this.path = "bot.ticketing.ticket.tags.create";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "panel_id", lu.getText(path+".panel_id.help"), true, true)
					.setMinValue(1),
				new OptionData(OptionType.INTEGER, "tag_type", lu.getText(path+".tag_type.help"), true).addChoices(
					new Choice("Thread", 1),
					new Choice("Channel", 2)
				),
				new OptionData(OptionType.STRING, "button_text", lu.getText(path+".button_text.help"))
					.setMaxLength(80),
				new OptionData(OptionType.STRING, "emoji", lu.getText(path+".emoji.help"))
					.setMaxLength(30),
				new OptionData(OptionType.CHANNEL, "location", lu.getText(path+".location.help"))
					.setChannelTypes(ChannelType.CATEGORY),
				new OptionData(OptionType.STRING, "message", lu.getText(path+".message.help"))
					.setMaxLength(2000),
				new OptionData(OptionType.STRING, "support_roles", lu.getText(path+".support_roles.help"))
					.setMaxLength(200),
				new OptionData(OptionType.STRING, "ticket_name", lu.getText(path+".ticket_name.help"))
					.setMaxLength(60),
				new OptionData(OptionType.INTEGER, "button_style", lu.getText(path+".button_style.help")).addChoices(
					new Choice("Blue", ButtonStyle.PRIMARY.getKey()),
					new Choice("Gray (default)", ButtonStyle.SECONDARY.getKey()),
					new Choice("Green", ButtonStyle.SUCCESS.getKey()),
					new Choice("Red", ButtonStyle.DANGER.getKey())
				)
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer panelId = event.optInteger("panel_id");
			Long guildId = bot.getDBUtil().ticketPanels.getGuildId(panelId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(panelId));
				return;
			}

			if (bot.getDBUtil().ticketTags.countPanelTags(panelId) >= 5) {
				editError(event, path+".max_tags", "Maximum 5 tags on 1 panel.");
				return;
			}

			Integer type = event.optInteger("tag_type", 1);
			String buttonName = event.optString("button_text", "Create ticket");
			String emoji = event.optString("emoji");
			Long categoryId = Optional.ofNullable(event.getOption("location", op -> op.getAsChannel().asCategory())).map(Category::getIdLong).orElse(null);
			String message = event.optString("message");
			String ticketName = event.optString("ticket_name", "ticket-");
			ButtonStyle buttonStyle = ButtonStyle.fromKey(event.optInteger("button_style", 1));

			List<Role> supportRoles = Optional.ofNullable(event.optMentions("support_roles")).map(Mentions::getRoles).orElse(Collections.emptyList());
			String supportRoleIds = null;
			if (!supportRoles.isEmpty()) {
				if (supportRoles.size() > 6) {
					editError(event, path+".too_many_roles", "Provided: %d".formatted(supportRoles.size()));
					return;
				}
				supportRoleIds = supportRoles.stream().map(Role::getId).collect(Collectors.joining(";"));
			}

			final int tagId;
			try {
				tagId = bot.getDBUtil().ticketTags.createTag(guildId, panelId, type, buttonName, emoji, categoryId, message, supportRoleIds, ticketName, buttonStyle.getKey());
			} catch (SQLException e) {
				editErrorOther(event, "Tag creation failed.");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", tagId, panelId))
				.build()
			);
		}
	}

	private class ModifyTag extends SlashCommand {
		public ModifyTag() {
			this.name = "modify";
			this.path = "bot.ticketing.ticket.tags.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "tag_id", lu.getText(path+".tag_id.help"), true, true)
					.setMinValue(1),
				new OptionData(OptionType.INTEGER, "tag_type", lu.getText(path+".tag_type.help")).addChoices(
					new Choice("Thread", 1),
					new Choice("Channel", 2)
				),
				new OptionData(OptionType.STRING, "button_text", lu.getText(path+".button_text.help"))
					.setMaxLength(80),
				new OptionData(OptionType.STRING, "emoji", lu.getText(path+".emoji.help"))
					.setMaxLength(20),
				new OptionData(OptionType.CHANNEL, "location", lu.getText(path+".location.help"))
					.setChannelTypes(ChannelType.CATEGORY),
				new OptionData(OptionType.STRING, "message", lu.getText(path+".message.help"))
					.setMaxLength(2000),
				new OptionData(OptionType.STRING, "support_roles", lu.getText(path+".support_roles.help"))
					.setMaxLength(200),
				new OptionData(OptionType.STRING, "ticket_name", lu.getText(path+".ticket_name.help"))
					.setMaxLength(60),
				new OptionData(OptionType.INTEGER, "button_style", lu.getText(path+".button_style.help")).addChoices(
					new Choice("Blue", ButtonStyle.PRIMARY.getKey()),
					new Choice("Gray (default)", ButtonStyle.SECONDARY.getKey()),
					new Choice("Green", ButtonStyle.SUCCESS.getKey()),
					new Choice("Red", ButtonStyle.DANGER.getKey())
				)
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer tagId = event.optInteger("tag_id");
			Long guildId = bot.getDBUtil().ticketTags.getGuildId(tagId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(tagId));
				return;
			}

			String buttonText = event.optString("button_text");
			String emoji = event.optString("emoji");
			ButtonStyle buttonStyle = ButtonStyle.fromKey(event.optInteger("button_style", 0));
			Integer type = event.optInteger("tag_type", null);
			String ticketName = event.optString("ticket_name");
			Category category = event.getOption("location", op -> op.getAsChannel().asCategory());
			String message = event.optString("message");

			List<Role> supportRoles = Optional.ofNullable(event.optMentions("support_roles")).map(Mentions::getRoles).orElse(Collections.emptyList());
			String supportRoleIds = null;
			if (!supportRoles.isEmpty()) {
				if (supportRoles.size() > 6) {
					editError(event, path+".too_many_roles", "Provided: %d".formatted(supportRoles.size()));
					return;
				}
				supportRoleIds = supportRoles.stream().map(Role::getId).collect(Collectors.joining(";"));
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed();
			if (buttonText != null)		builder.addField(lu.getGuildText(event, path+".changed_text"), buttonText, true);
			if (emoji != null)			builder.addField(lu.getGuildText(event, path+".changed_emoji"), emoji, true);
			if (buttonStyle != ButtonStyle.UNKNOWN)	builder.addField(lu.getGuildText(event, path+".changed_style"), buttonStyle.toString(), true);
			if (type != null)			builder.addField(lu.getGuildText(event, path+".changed_type"), (type > 1 ? "Channel" : "Thread"), true);
			if (ticketName != null)		builder.addField(lu.getGuildText(event, path+".changed_name"), ticketName, true);
			if (category != null)		builder.addField(lu.getGuildText(event, path+".changed_location"), category.getAsMention(), true);
			if (supportRoleIds != null)	builder.addField(lu.getGuildText(event, path+".changed_roles"),
				supportRoles.stream().map(Role::getAsMention).collect(Collectors.joining(" ")), false);
			if (message != null)		builder.addField(lu.getGuildText(event, path+".changed_message"), message, false);
			
			if (builder.getFields().isEmpty()) {
				editError(event, path+".no_options");
			} else {
				try {
					bot.getDBUtil().ticketTags.updateTag(
						tagId, type, buttonText, emoji,
						Optional.ofNullable(category).map(Category::getIdLong).orElse(null),
						message, supportRoleIds, ticketName, buttonStyle.getKey()
					);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "update ticket tag");
					return;
				}
				editEmbed(event, builder.setColor(Constants.COLOR_SUCCESS)
					.setTitle(lu.getGuildText(event, path+".done"))
					.build()
				);
			}
		}
	}

	private class ViewTag extends SlashCommand {
		public ViewTag() {
			this.name = "view";
			this.path = "bot.ticketing.ticket.tags.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "tag_id", lu.getText(path+".tag_id.help"), true, true)
					.setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer tagId = event.optInteger("tag_id");
			Long guildId = bot.getDBUtil().ticketTags.getGuildId(tagId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(tagId));
				return;
			}

			Tag tag = bot.getDBUtil().ticketTags.getTagFull(tagId);
			if (tag == null) {
				editError(event, path+".not_found", "No record found");
				return;
			}

			EmbedBuilder builder = tag.getPreviewEmbed((str) -> lu.getGuildText(event, path+str), tagId);

			String message = Optional.ofNullable(tag.getMessage()).orElse(lu.getGuildText(event, path+".none"));
			String category = Optional.ofNullable(tag.getLocation()).map(id -> event.getGuild().getCategoryById(id).getAsMention()).orElse(lu.getGuildText(event, path+".none"));
			String roles = Optional.of(tag.getSupportRoles())
				.filter(l -> !l.isEmpty())
				.map(ids -> ids.stream().map("<@&%s>"::formatted).collect(Collectors.joining(", ")))
				.orElse(lu.getGuildText(event, path+".none"));
			
			builder.addField(lu.getGuildText(event, path+".location"), category, true)
				.addField(lu.getGuildText(event, path+".roles"), roles, false)
				.addField(lu.getGuildText(event, path+".message"), message, false);
			
			event.getHook().editOriginalEmbeds(builder.build()).setActionRow(tag.previewButton()).queue();
		}
	}

	private class DeleteTag extends SlashCommand {
		public DeleteTag() {
			this.name = "delete";
			this.path = "bot.ticketing.ticket.tags.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "tag_id", lu.getText(path+".tag_id.help"), true)
					.setMinValue(1)
			);
			this.subcommandGroup = new SubcommandGroupData("tags", lu.getText("bot.ticketing.ticket.tags.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer tagId = event.optInteger("tag_id");
			Long guildId = bot.getDBUtil().ticketTags.getGuildId(tagId);
			if (guildId == null || !guildId.equals(event.getGuild().getIdLong())) {
				editError(event, path+".not_found", "Received ID: %s".formatted(tagId));
				return;
			}

			try {
				bot.getDBUtil().ticketTags.deleteTag(tagId);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "delete ticket tag");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", tagId))
				.build()
			);
		}
	}

	// Settings

	private class Automation extends SlashCommand {
		public Automation() {
			this.name = "automation";
			this.path = "bot.ticketing.ticket.automation";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "autoclose", lu.getText(path+".autoclose.help"))
					.setRequiredRange(0, 72),
				new OptionData(OptionType.BOOLEAN, "author_left", lu.getText(path+".author_left.help")),
				new OptionData(OptionType.INTEGER, "reply_time", lu.getText(path+".reply_time.help"))
					.setRequiredRange(0, 24)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long guildId = event.getGuild().getIdLong();
			
			StringBuilder response = new StringBuilder();
			if (event.hasOption("autoclose")) {
				int time = event.optInteger("autoclose");
				try {
					bot.getDBUtil().ticketSettings.setAutocloseTime(guildId, time);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "set ticket autoclose time");
					return;
				}
				response.append(lu.getGuildText(event, path+".changed_autoclose", time));
			}
			if (event.hasOption("author_left")) {
				boolean left = event.optBoolean("author_left");
				try {
					bot.getDBUtil().ticketSettings.setAutocloseLeft(guildId, left);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "set ticket autoclose left");
					return;
				}
				response.append(lu.getGuildText(event, path+".changed_left", left?Constants.SUCCESS:Constants.FAILURE));
			}
			if (event.hasOption("reply_time")) {
				int time = event.optInteger("reply_time");
				try {
					bot.getDBUtil().ticketSettings.setTimeToReply(guildId, time);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "set ticket time reply");
					return;
				}
				response.append(lu.getGuildText(event, path+".changed_reply", time));
			}
			
			if (response.isEmpty()) {
				editError(event, path+".no_options");
			} else {
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, path+".embed_title"))
					.appendDescription(response.toString())
					.build()
				);
			}
		}
	}

	private class Settings extends SlashCommand {
		public Settings() {
			this.name = "settings";
			this.path = "bot.ticketing.ticket.settings";
			this.options = List.of(
				new OptionData(OptionType.BOOLEAN, "delete_pings", lu.getText(path+".delete_pings.help")),
				new OptionData(OptionType.BOOLEAN, "other_roles", lu.getText(path+".other_roles.help")),
				new OptionData(OptionType.STRING, "role_tickets_support", lu.getText(path+".role_tickets_support.help"))
					.setMaxLength(200),
				new OptionData(OptionType.INTEGER, "allow_close", lu.getText(path+".allow_close.help"))
					.addChoice("Everyone (default)", TicketSettingsManager.AllowClose.EVERYONE.getValue())
					.addChoice("Helper+ access", TicketSettingsManager.AllowClose.HELPER.getValue())
					.addChoice("Ticket support roles", TicketSettingsManager.AllowClose.SUPPORT.getValue()),
				new OptionData(OptionType.INTEGER, "transcripts_mode", lu.getText(path+".transcripts_mode.help"))
					.addChoice("All tickets", TicketSettingsManager.TranscriptsMode.ALL.getValue())
					.addChoice("All, except role requests (default)", TicketSettingsManager.TranscriptsMode.EXCEPT_ROLES.getValue())
					.addChoice("None", TicketSettingsManager.TranscriptsMode.NONE.getValue())
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			StringBuilder response = new StringBuilder();

			if (event.getOptions().isEmpty()) {
				// Return overview
				TicketSettingsManager.TicketSettings settings = bot.getDBUtil().getTicketSettings(event.getGuild());

				response.append("\n> Autoclose time: **")
					.append(TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), settings.getAutocloseTime()))
					.append("**\n> Autoclose on left: ")
					.append(settings.autocloseLeftEnabled()?Constants.SUCCESS:Constants.FAILURE)
					.append("\n> Time to reply: **")
					.append(TimeUtil.durationToLocalizedString(lu, event.getUserLocale(), settings.getTimeToReply()))
					.append("**\n\n> Allow other roles: ")
					.append(settings.otherRoleEnabled()?Constants.SUCCESS:Constants.FAILURE)
					.append("\n\n> Support roles: ")
					.append(settings.getRoleSupportIds()
						.stream()
						.map(String::valueOf)
						.collect(Collectors.joining("`, `", "`", "`")))
					.append("\n\n> Delete pings: ")
					.append(settings.deletePingsEnabled()?Constants.SUCCESS:Constants.FAILURE)
					.append("\n> Allow close: **")
					.append(MessageUtil.capitalize(settings.getAllowClose().name()))
					.append("**\n> Transcripts saved: **")
					.append(MessageUtil.capitalize(settings.getTranscriptsMode().name()).replace("_", " "))
					.append("**");

				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getGuildText(event, path+".embed_view"))
					.appendDescription(response.toString())
					.build()
				);
			} else {
				// Edit settings
				if (event.hasOption("delete_pings")) {
					final boolean deletePings = event.optBoolean("delete_pings");

					try {
						bot.getDBUtil().ticketSettings.setDeletePings(event.getGuild().getIdLong(), deletePings);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "ticket settings set delete pings");
						return;
					}
					response.append(lu.getGuildText(event, path+".changed_delete", deletePings?Constants.SUCCESS:Constants.FAILURE));
				}
				if (event.hasOption("other_roles")) {
					final boolean otherRoles = event.optBoolean("other_roles");

					try {
						bot.getDBUtil().ticketSettings.setOtherRoles(event.getGuild().getIdLong(), otherRoles);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "ticket settings set other roles");
						return;
					}
					response.append(lu.getGuildText(event, path+".changed_other", otherRoles?Constants.SUCCESS:Constants.FAILURE));
				}
				if (event.hasOption("role_tickets_support")) {
					if (event.optString("role_tickets_support").equalsIgnoreCase("null")) {
						// Clear roles
						try {
							bot.getDBUtil().ticketSettings.setSupportRoles(event.getGuild().getIdLong(), List.of());
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "ticket settings clear support roles");
							return;
						}

						response.append(lu.getGuildText(event, path+".cleared_support"));
					} else {
						// Set roles
						List<Role> roles = event.optMentions("role_tickets_support").getRoles();
						if (roles.isEmpty() || roles.size()>3) {
							editError(event, path+".bad_roles");
							return;
						}
						try {
							bot.getDBUtil().ticketSettings.setSupportRoles(event.getGuild().getIdLong(), roles.stream().map(Role::getIdLong).toList());
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "ticket settings set support roles");
							return;
						}

						response.append(lu.getGuildText(event, path+".changed_support", roles.stream().map(Role::getAsMention).collect(Collectors.joining(", "))));
					}
				}
				if (event.hasOption("allow_close")) {
					TicketSettingsManager.AllowClose allowClose = TicketSettingsManager.AllowClose.valueOf(event.optInteger("allow_close"));
					if (allowClose != null) {
						try {
							bot.getDBUtil().ticketSettings.setAllowClose(event.getGuild().getIdLong(), allowClose);
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "ticket settings set allow close");
							return;
						}
						response.append(lu.getGuildText(event, path+".changed_close", MessageUtil.capitalize(allowClose.name())));
					}
				}
				if (event.hasOption("transcripts_mode")) {
					TicketSettingsManager.TranscriptsMode transcriptsMode = TicketSettingsManager.TranscriptsMode.valueOf(event.optInteger("transcripts_mode"));
					if (transcriptsMode != null) {
						try {
							bot.getDBUtil().ticketSettings.setTranscript(event.getGuild().getIdLong(), transcriptsMode);
						} catch (SQLException ex) {
							editErrorDatabase(event, ex, "ticket settings set allow close");
							return;
						}
						response.append(lu.getGuildText(event, path+".changed_transcript", MessageUtil.capitalize(transcriptsMode.name()).replace("_", " ")));
					}
				}

				if (response.isEmpty()) {
					editErrorUnknown(event, "Response for ticket settings is empty.");
				} else {
					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, path+".embed_changes"))
						.appendDescription(response.toString())
						.build()
					);
				}
			}
		}
	}

	private boolean isInvalidURL(String urlString) {
		if (urlString == null) return false;
		try {
			URI ignored = URI.create(urlString);
			return false;
		} catch (Exception e) {
			return true;
		}
	}

	private MessageEmbed buildPanelEmbed(Guild guild, Integer panelId) {
		Panel panel = bot.getDBUtil().ticketPanels.getPanel(panelId);
		return panel.getFilledEmbed(bot.getDBUtil().getGuildSettings(guild).getColor()).build();
	}
	
}

package dev.fireatom.FABI.listeners;

import static dev.fireatom.FABI.utils.CastUtil.castLong;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.CooldownScope;
import dev.fireatom.FABI.base.waiter.EventWaiter;
import dev.fireatom.FABI.commands.role.TempRoleCmd;
import dev.fireatom.FABI.objects.CaseType;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.Emote;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.database.DBUtil;
import dev.fireatom.FABI.utils.database.managers.ServerBlacklistManager;
import dev.fireatom.FABI.utils.database.managers.CaseManager.CaseData;
import dev.fireatom.FABI.utils.database.managers.RoleManager;
import dev.fireatom.FABI.utils.database.managers.TicketTagManager.Tag;
import dev.fireatom.FABI.utils.exception.FormatterException;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;
import dev.fireatom.FABI.utils.message.MessageUtil;

import dev.fireatom.FABI.utils.message.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed.Field;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu.SelectTarget;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.ChannelAction;
import net.dv8tion.jda.api.utils.TimeFormat;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import net.dv8tion.jda.internal.utils.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

public class InteractionListener extends ListenerAdapter {

	private final Logger log = (Logger) LoggerFactory.getLogger(InteractionListener.class);

	private final App bot;
	private final LocaleUtil lu;
	private final DBUtil db;
	private final EventWaiter waiter;

	private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES, Permission.MANAGE_WEBHOOKS);
	private final int MAX_GROUP_SELECT = 1;

	public InteractionListener(App bot, EventWaiter waiter) {
		this.bot = bot;
		this.lu = bot.getLocaleUtil();
		this.db = bot.getDBUtil();
		this.waiter = waiter;
	}

	public void editError(IReplyCallback event, String... text) {
		if (text.length > 1) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, text[0], text[1])).queue();
		} else {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, text[0])).queue();
		}
	}

	public void sendErrorLive(IReplyCallback event, String path) {
		event.replyEmbeds(bot.getEmbedUtil().getError(event, path)).setEphemeral(true).queue();
	}

	public void sendError(IReplyCallback event, String path) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, path)).setEphemeral(true).queue();
	}

	public void sendError(IReplyCallback event, String path, String info) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, path, info)).setEphemeral(true).queue();
	}

	public void sendSuccess(IReplyCallback event, String path) {
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS).setDescription(lu.getText(event, path)).build()).setEphemeral(true).queue();
	}

	// Check for cooldown parameters, if exists - check if cooldown active, else apply it
	private void runButtonInteraction(ButtonInteractionEvent event, @Nullable Cooldown cooldown, @NotNull Runnable function) {
		// Acknowledge interaction
		event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

		if (cooldown != null) {
			String key = getCooldownKey(cooldown, event);
			int remaining = bot.getClient().getRemainingCooldown(key);
			if (remaining > 0) {
				event.getHook().sendMessage(getCooldownErrorString(cooldown, event, remaining)).setEphemeral(true).queue();
				return;
			} else {
				bot.getClient().applyCooldown(key, cooldown.getTime());
			}
		}

		function.run();
	}

	@Override
	public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
		// Check if blacklisted
		if (bot.getBlacklist().isBlacklisted(event)) return;

		String[] actions = event.getComponentId().split(":");

		try {
			switch (actions[0]) {
				case "verify" -> runButtonInteraction(event, Cooldown.BUTTON_VERIFY, () -> buttonVerify(event));
				case "role" -> {
					switch (actions[1]) {
						case "start_request" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_SHOW, () -> buttonRoleShowSelection(event));
						case "other" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_OTHER, () -> buttonRoleSelectionOther(event));
						case "clear" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_CLEAR, () -> buttonRoleSelectionClear(event));
						case "remove" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_REMOVE, () -> buttonRoleRemove(event));
						case "toggle" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_TOGGLE, () -> buttonRoleToggle(event));
					}
				}
				case "ticket" -> {
					switch (actions[1]) {
						case "role_create" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_TICKET, () -> buttonRoleTicketCreate(event));
						case "role_approve" -> runButtonInteraction(event, Cooldown.BUTTON_ROLE_APPROVE, () -> buttonRoleTicketApprove(event));
						case "close" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLOSE, () -> buttonTicketClose(event));
						case "cancel" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CANCEL, () -> buttonTicketCloseCancel(event));
						case "claim" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CLAIM, () -> buttonTicketClaim(event));
						case "unclaim" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_UNCLAIM, () -> buttonTicketUnclaim(event));
					}
				}
				case "tag" -> runButtonInteraction(event, Cooldown.BUTTON_TICKET_CREATE, () -> buttonTagCreateTicket(event));
				case "delete" -> runButtonInteraction(event, Cooldown.BUTTON_REPORT_DELETE, () -> buttonReportDelete(event));
				case "voice" -> {
					if (!event.getMember().getVoiceState().inAudioChannel()) {
						sendErrorLive(event, "bot.voice.listener.not_in_voice");
						return;
					}
					Long channelId = db.voice.getChannel(event.getUser().getIdLong());
					if (channelId == null) {
						sendErrorLive(event, "errors.no_channel");
						return;
					}
					VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
					if (vc == null) return;
					switch (actions[1]) {
						case "lock" -> runButtonInteraction(event, null, () -> buttonVoiceLock(event, vc));
						case "unlock" -> runButtonInteraction(event, null, () -> buttonVoiceUnlock(event, vc));
						case "ghost" -> runButtonInteraction(event, null, () -> buttonVoiceGhost(event, vc));
						case "unghost" -> runButtonInteraction(event, null, () -> buttonVoiceUnghost(event, vc));
						case "permit" -> runButtonInteraction(event, null, () -> buttonVoicePermit(event));
						case "reject" -> runButtonInteraction(event, null, () -> buttonVoiceReject(event));
						case "perms" -> runButtonInteraction(event, null, () -> buttonVoicePerms(event, vc));
						case "delete" -> runButtonInteraction(event, null, () -> buttonVoiceDelete(event, vc));
					}
				}
				case "blacklist" -> runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonBlacklist(event));
				case "sync_unban" -> runButtonInteraction(event, null, () -> buttonSyncUnban(event));
				case "sync_ban" -> runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonSyncBan(event));
				case "sync_kick" -> runButtonInteraction(event, Cooldown.BUTTON_SYNC_ACTION, () -> buttonSyncKick(event));
				case "strikes" -> runButtonInteraction(event, Cooldown.BUTTON_SHOW_STRIKES, () -> buttonShowStrikes(event));
				case "manage-confirm" -> runButtonInteraction(event, Cooldown.BUTTON_MODIFY_CONFIRM, () -> buttonModifyConfirm(event));
				default -> log.debug("Unknown button interaction: {}", event.getComponentId());
			}
		} catch (Throwable t) {
			// Logs throwable and tries to respond to the user with the error
			// Thrown errors are not user's error, but code's fault as such things should be caught earlier and replied properly
			log.error("ButtonInteraction Exception", t);
			bot.getEmbedUtil().sendUnknownError(event.getHook(), lu.getLocale(event), t.getMessage());
		}
	}

	private void buttonVerify(ButtonInteractionEvent event) {
		Member member = event.getMember();
		Guild guild = event.getGuild();

		Long verifyRoleId = db.getVerifySettings(guild).getRoleId();
		if (verifyRoleId == null) {
			sendError(event, "bot.verification.failed_role", "The verification role is not configured");
			return;
		}
		Role verifyRole = guild.getRoleById(verifyRoleId);
		if (verifyRole == null) {
			sendError(event, "bot.verification.failed_role", "Verification role not found");
			return;
		}
		if (member.getRoles().contains(verifyRole)) {
			sendError(event, "bot.verification.you_verified");
			return;
		}

		// Check if user is blacklisted
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(db.group.getOwnedGroups(guild.getIdLong()));
		groupIds.addAll(db.group.getGuildGroups(guild.getIdLong()));
		for (int groupId : groupIds) {
			ServerBlacklistManager.BlacklistData data = db.serverBlacklist.getInfo(groupId, member.getIdLong());
			if (data != null && db.group.getAppealGuildId(groupId)!=guild.getIdLong()) {
				sendError(event, "bot.verification.blacklisted", "Reason: "+data.getReason());
				return;
			}
		}

		Set<Long> additionalRoles = db.getVerifySettings(guild).getAdditionalRoles();
		if (additionalRoles.isEmpty()) {
			guild.addRoleToMember(member, verifyRole).reason("Verification completed").queue(
				success -> event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue(),
				failure -> {
					sendError(event, "bot.verification.failed_role");
					log.warn("Was unable to add verify role to user in {}({})", guild.getName(), guild.getId(), failure);
				}
			);
		} else {
			List<Role> finalRoles = new ArrayList<>(member.getRoles());
			// add verify role
			finalRoles.add(verifyRole);
			// add each additional role
			additionalRoles.stream()
				.map(guild::getRoleById)
				.filter(Objects::nonNull)
				.forEach(finalRoles::add);
			// modify
			guild.modifyMemberRoles(member, finalRoles)
				.reason("Verification completed")
				.queue(
				success -> event.getHook().sendMessage(Constants.SUCCESS).setEphemeral(true).queue(),
				failure -> {
					sendError(event, "bot.verification.failed_role");
					log.warn("Was unable to add roles to user in {}({})", guild.getName(), guild.getId(), failure);
				}
			);
		}
	}

	// Role selection
	private void buttonRoleShowSelection(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();

		Long channelId = db.tickets.getOpenedChannel(event.getMember().getIdLong(), guild.getIdLong(), 0);
		if (channelId != null) {
			ThreadChannel channel = guild.getThreadChannelById(channelId);
			if (channel != null) {
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.ticket_exists", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			ignoreExc(() -> db.tickets.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)"));
		}

		List<ActionRow> actionRows = new ArrayList<>();
		// String select menu IDs "menu:role_row:1/2/3"
		for (int row = 1; row <= 3; row++) {
			ActionRow actionRow = createRoleRow(guild, row);
			if (actionRow != null) {
				actionRows.add(actionRow);
			}
		}
		if (db.getTicketSettings(guild).otherRoleEnabled()) {
			actionRows.add(ActionRow.of(Button.secondary("role:other", lu.getGuildText(event, "bot.ticketing.listener.request_other"))));
		}
		actionRows.add(ActionRow.of(Button.danger("role:clear", lu.getGuildText(event, "bot.ticketing.listener.request_clear")),
			Button.success("ticket:role_create", lu.getGuildText(event, "bot.ticketing.listener.request_continue"))));

		MessageEmbed embed = new EmbedBuilder()
			.setColor(Constants.COLOR_DEFAULT)
			.setDescription(lu.getGuildText(event, "bot.ticketing.listener.request_title"))
			.build();

		event.getHook().sendMessageEmbeds(embed).setComponents(actionRows).setEphemeral(true).queue();
	}

	private ActionRow createRoleRow(final Guild guild, int row) {
		List<RoleManager.RoleData> roles = bot.getDBUtil().roles.getAssignableByRow(guild.getIdLong(), row);
		if (roles.isEmpty()) return null;
		List<SelectOption> options = new ArrayList<>();
		for (RoleManager.RoleData data : roles) {
			if (options.size() >= 25) break;
			Role role = guild.getRoleById(data.getIdLong());
			if (role == null) continue;
			options.add(SelectOption.of(role.getName(), role.getId()).withDescription(data.getDescription(null)));
		}
		if (options.isEmpty()) return null;
		StringSelectMenu menu = StringSelectMenu.create("menu:role_row:"+row)
			.setPlaceholder(db.getTicketSettings(guild).getRowText(row))
			.setMaxValues(25)
			.addOptions(options)
			.build();
		return ActionRow.of(menu);
	}

	private void buttonRoleSelectionOther(ButtonInteractionEvent event) {
		List<Field> fields = event.getMessage().getEmbeds().getFirst().getFields();
		List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : fields.getFirst().getValue());
		if (roleIds.contains(0L))
			roleIds.remove(0L);
		else
			roleIds.add(0L);
		
		MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().getFirst())
			.clearFields()
			.addField(lu.getGuildText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(roleIds, lu.getLocale(event)), false)
			.build();
		event.getHook().editOriginalEmbeds(embed).queue();
	}

	private void buttonRoleSelectionClear(ButtonInteractionEvent event) {
		MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().getFirst())
			.clearFields()
			.addField(lu.getGuildText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(Collections.emptyList(), lu.getLocale(event)), false)
			.build();
		event.getHook().editOriginalEmbeds(embed).queue();
	}

	private void buttonRoleRemove(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		List<Role> currentRoles = event.getMember().getRoles();

		List<Role> allRoles = new ArrayList<>();
		db.roles.getAssignable(guild.getIdLong()).forEach(data -> allRoles.add(guild.getRoleById(data.getIdLong())));
		db.roles.getCustom(guild.getIdLong()).forEach(data -> allRoles.add(guild.getRoleById(data.getIdLong())));
		List<Role> roles = allRoles.stream().filter(currentRoles::contains).toList();
		if (roles.isEmpty()) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.no_assigned")).setEphemeral(true).queue();
			return;
		}

		List<SelectOption> options = roles.stream().map(role -> SelectOption.of(role.getName(), role.getId())).toList();	
		StringSelectMenu menu = StringSelectMenu.create("menu:role_remove")
			.setPlaceholder(lu.getGuildText(event, "bot.ticketing.listener.request_template"))
			.setMaxValues(options.size())
			.addOptions(options)
			.build();
		event.getHook()
			.sendMessageEmbeds(bot.getEmbedUtil()
				.getEmbed()
				.setDescription(lu.getGuildText(event, "bot.ticketing.listener.remove_title"))
				.build()
			).setActionRow(menu)
			.setEphemeral(true)
			.queue(msg ->
				waiter.waitForEvent(
					StringSelectInteractionEvent.class,
					e -> e.getComponentId().equals("menu:role_remove"),
					actionEvent -> {
						List<Role> remove = actionEvent.getSelectedOptions().stream().map(option -> guild.getRoleById(option.getValue())).toList();
						guild.modifyMemberRoles(event.getMember(), null, remove).reason("User request").queue(done -> {
							msg.editMessageEmbeds(bot.getEmbedUtil().getEmbed()
								.setDescription(lu.getGuildText(event, "bot.ticketing.listener.remove_done", remove.stream().map(Role::getAsMention).collect(Collectors.joining(", "))))
								.setColor(Constants.COLOR_SUCCESS)
								.build()
							).setComponents().queue();
						}, failure -> msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.remove_failed", failure.getMessage())).setComponents().queue());
					},
					40,
					TimeUnit.SECONDS,
					() -> msg.editMessageComponents(ActionRow.of(
						menu.createCopy().setPlaceholder(lu.getGuildText(event, "errors.timed_out")).setDisabled(true).build())
					).queue()
				)
			);
	}

	private void buttonRoleToggle(ButtonInteractionEvent event) {
		Long roleId = castLong(event.getButton().getId().split(":")[2]);
		Role role = event.getGuild().getRoleById(roleId);
		if (role == null || !db.roles.isToggleable(roleId)) {
			sendError(event, "bot.ticketing.listener.toggle_failed", "Role not found or can't be toggled");
			return;
		}

		if (event.getMember().getRoles().contains(role)) {
			event.getGuild().removeRoleFromMember(event.getMember(), role).queue(done -> {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.toggle_removed", role.getAsMention()))
					.setColor(Constants.COLOR_SUCCESS)
					.build()
				).setEphemeral(true).queue();
			}, failure -> sendError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage()));
		} else {
			event.getGuild().addRoleToMember(event.getMember(), role).queue(done -> {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.toggle_added", role.getAsMention()))
					.setColor(Constants.COLOR_SUCCESS)
					.build()
				).setEphemeral(true).queue();
			}, failure -> sendError(event, "bot.ticketing.listener.toggle_failed", failure.getMessage()));
		}
	}

	// Role ticket
	private void buttonRoleTicketCreate(ButtonInteractionEvent event) {
		Guild guild = event.getGuild();
		long guildId = guild.getIdLong();

		// Check if user has selected any role
		List<Field> fields = event.getMessage().getEmbeds().getFirst().getFields();
		List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : fields.getFirst().getValue());
		if (roleIds.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_none");
			return;
		}
		// Check if bot is able to give selected roles
		boolean otherRole = roleIds.contains(0L);
		List<Role> memberRoles = event.getMember().getRoles();
		List<Role> add = roleIds.stream()
			.filter(option -> !option.equals(0L))
			.map(guild::getRoleById)
			.filter(role -> role != null && !memberRoles.contains(role))
			.toList();
		if (!otherRole && add.isEmpty()) {
			sendError(event, "bot.ticketing.listener.request_empty");
			return;
		}

		// final role IDs list
		List<String> finalRoleIds = new ArrayList<>();
		add.forEach(role -> {
			if (db.roles.isTemp(role.getIdLong()))
				finalRoleIds.add("t"+role.getId());
			else
				finalRoleIds.add(role.getId());
		});

		int ticketId = 1 + db.tickets.lastIdByTag(guildId, 0);
		event.getChannel().asTextChannel().createThreadChannel(lu.getGuildText(event, "ticket.role")+"-"+ticketId, true).setInvitable(false).queue(
			channel -> {
				db.tickets.addRoleTicket(
					ticketId, event.getMember().getIdLong(), guildId, channel.getIdLong(),
					String.join(";", finalRoleIds), bot.getDBUtil().getTicketSettings(guild).getTimeToReply()
				);
				
				StringBuffer mentions = new StringBuffer(event.getMember().getAsMention());
				// Get either support roles or use mod roles
				mentions.append("||");
				List<Long> supportRoleIds = db.ticketSettings.getSettings(guild.getIdLong()).getRoleSupportIds();
				if (supportRoleIds.isEmpty()) supportRoleIds = db.access.getRoles(guild.getIdLong(), CmdAccessLevel.MOD);
				supportRoleIds.forEach(roleId -> mentions.append(" <@&").append(roleId).append(">"));
				mentions.append("||");
				// Send message
				channel.sendMessage(mentions.toString()).queue(msg -> {
					if (db.getTicketSettings(guild).deletePingsEnabled())
						msg.delete().queueAfter(5, TimeUnit.SECONDS, null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_CHANNEL));
				});
				
				String rolesString = String.join(" ", add.stream().map(Role::getAsMention).collect(Collectors.joining(" ")), (otherRole ? lu.getGuildText(event, "bot.ticketing.embeds.other") : ""));
				String proofString = add.stream().map(role -> db.roles.getDescription(role.getIdLong())).filter(Objects::nonNull).distinct().collect(Collectors.joining("\n- ", "- ", ""));
				MessageEmbed embed = new EmbedBuilder().setColor(db.getGuildSettings(guild).getColor())
					.setDescription(String.format("%s\n> %s\n\n%s, %s\n%s\n\n%s",
						lu.getGuildText(event, "ticket.role_title"),
						rolesString,
						event.getMember().getEffectiveName(),
						lu.getGuildText(event, "ticket.role_header"),
						(proofString.length() < 3 ? lu.getGuildText(event, "ticket.role_proof") : proofString),
						lu.getGuildText(event, "ticket.role_footer")
					))
					.build();
				Button approve = Button.success("ticket:role_approve", lu.getGuildText(event, "ticket.role_approve"));
				Button close = Button.danger("ticket:close", lu.getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled();
				channel.sendMessageEmbeds(embed).setAllowedMentions(Collections.emptyList()).addActionRow(approve, close).queue(msg -> {
					msg.editMessageComponents(ActionRow.of(approve, close.asEnabled())).queueAfter(10, TimeUnit.SECONDS);
				});

				// Log
				bot.getGuildLogger().ticket.onCreate(guild, channel, event.getUser());
				// Send reply
				event.getHook().editOriginalEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.created", channel.getAsMention()))
					.build()
				).setComponents().queue();
			}, failure -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create", failure.getMessage())).setComponents().queue()
		);
	}

	private void buttonRoleTicketApprove(ButtonInteractionEvent event) {
		List<Long> supportRoleIds = bot.getDBUtil().getTicketSettings(event.getGuild()).getRoleSupportIds();
		if (supportRoleIds.isEmpty()) {
			if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.MOD)) {
				// User has no Mod+ access to approve role request
				sendError(event, "errors.interaction.no_access", "Moderator+");
				return;
			}
		} else if (denyTicketAction(supportRoleIds, event.getMember())) {
			sendError(event, "errors.interaction.no_access", "Ticket support or Admin+");
			return;
		}
		long channelId = event.getChannel().getIdLong();
		if (db.tickets.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}
		Guild guild = event.getGuild();
		Long userId = db.tickets.getUserId(channelId);

		guild.retrieveMemberById(userId).queue(member -> {
			List<Role> tempRoles = new ArrayList<>();
			List<Role> roles = new ArrayList<>();
			db.tickets.getRoleIds(channelId).forEach(v -> {
				if (v.charAt(0) == 't') {
					long roleId = castLong(v.substring(1));
					Role role = guild.getRoleById(roleId);
					if (role != null) tempRoles.add(role);
				} else {
					long roleId = castLong(v);
					Role role = guild.getRoleById(roleId);
					if (role != null) roles.add(role);
				}
			});
			if (!tempRoles.isEmpty()) {
				// Has temp roles - send modal
				List<ActionRow> rows = new ArrayList<>();
				for (Role role : tempRoles) {
					if (rows.size() >= 5) continue;
					TextInput input = TextInput.create(role.getId(), role.getName(), TextInputStyle.SHORT)
						.setPlaceholder("1w - 1 Week, 30d - 30 Days, 0 - permanently")
						.setRequired(true)
						.setMaxLength(10)
						.build();
					rows.add(ActionRow.of(input));
				}
				Modal modal = Modal.create("role_temp:"+channelId, lu.getGuildText(event, "bot.ticketing.listener.temp_time"))
					.addComponents(rows)
					.build();
				String buttonUuid = UUID.randomUUID().toString();
				Button continueButton = Button.success(buttonUuid, "Continue");
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.temp_continue", rows.size()))
					.build()
				).setActionRow(continueButton).setEphemeral(true).queue(msg -> {
					waiter.waitForEvent(
						ButtonInteractionEvent.class,
						e -> e.getComponentId().equals(buttonUuid),
						buttonEvent -> {
							buttonEvent.replyModal(modal).queue();
							msg.delete().queue();
							// Maybe reply, that other mod started to fill modal
						},
						10,
						TimeUnit.SECONDS,
						() -> msg.delete().queue()
					);
				});
				return;
			}
			if (roles.isEmpty()) {
				event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_none"))
					.setColor(Constants.COLOR_WARNING)
					.build()
				).setEphemeral(true).queue();
				return;
			}

			final int ticketId = db.tickets.getTicketId(channelId);
			guild.modifyMemberRoles(member, roles, null)
				.reason("Request role-"+ticketId+" approved by "+event.getMember().getEffectiveName())
				.queue(done -> {
					bot.getGuildLogger().role.onApproved(member, event.getMember(), guild, roles, ticketId);
					db.tickets.setClaimed(channelId, event.getMember().getIdLong());
					event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_added"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK));
					member.getUser().openPrivateChannel().queue(dm -> {
						dm.sendMessage(lu.getGuildText(event, "bot.ticketing.listener.role_dm")
							.replace("{roles}", roles.stream().map(Role::getName).collect(Collectors.joining(" | ")))
							.replace("{server}", guild.getName())
							.replace("{id}", String.valueOf(ticketId))
							.replace("{mod}", event.getMember().getEffectiveName())
						).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});
				}, failure -> {
					sendError(event, "bot.ticketing.listener.role_failed", failure.getMessage());
				});
		}, failure -> sendError(event, "bot.ticketing.listener.no_member", failure.getMessage()));
	}

	private void buttonTicketClose(ButtonInteractionEvent event) {
		long channelId = event.getChannelIdLong();
		if (db.tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		// Check who can close tickets
		final boolean isAuthor = db.tickets.getUserId(channelId).equals(event.getUser().getIdLong());
		if (!isAuthor) {
			switch (db.getTicketSettings(event.getGuild()).getAllowClose()) {
				case EVERYONE -> {}
				case HELPER -> {
					// Check if user has Helper+ access
					if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
						// No access - reject
						sendError(event, "errors.interaction.no_access", "Helper+ access");
						return;
					}
				}
				case SUPPORT -> {
					// Check if user is ticket support(or mod if support empty) or has Admin+ access
					int tagId = db.tickets.getTag(channelId);
					if (tagId==0) {
						// Role request ticket
						List<Long> supportRoleIds = db.getTicketSettings(event.getGuild()).getRoleSupportIds();
						if (supportRoleIds.isEmpty()) supportRoleIds = db.access.getRoles(event.getGuild().getIdLong(), CmdAccessLevel.MOD);
						// Check
						if (denyTicketAction(supportRoleIds, event.getMember())) {
							sendError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
							return;
						}
					} else {
						// Standard ticket
						final List<Long> supportRoleIds = Stream.of(db.ticketTags.getSupportRolesString(tagId).split(";"))
							.map(Long::parseLong)
							.toList();
						// Check
						if (denyTicketAction(supportRoleIds, event.getMember())) {
							sendError(event, "errors.interaction.no_access", "'Support' for this ticket or Admin+ access");
							return;
						}
					}
				}
			}
		}
		// Close
		String reason = isAuthor
			? lu.getGuildText(event, "bot.ticketing.listener.closed_author")
			: lu.getGuildText(event, "bot.ticketing.listener.closed_support");
		event.editButton(Button.danger("ticket:close", bot.getLocaleUtil().getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒")).asDisabled()).queue();
		// Send message
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event).setDescription(lu.getGuildText(event, "bot.ticketing.listener.delete_countdown")).build()).queue(msg -> {
			bot.getTicketUtil().closeTicket(channelId, event.getUser(), reason,
				new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE)
					.andThen(t -> {
						log.error("Couldn't close ticket with channelID '{}'", channelId, t);
						msg.editMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.close_failed", t.getMessage())).queue();
					})
			);
		});
	}

	private boolean denyTicketAction(List<Long> roleIds, Member member) {
		if (roleIds.isEmpty()) return false; // No data to check against
		final List<Role> roles = member.getRoles(); // Check if user has any support role
		if (!roles.isEmpty() && roles.stream().anyMatch(r -> roleIds.contains(r.getIdLong()))) return false;
		return !bot.getCheckUtil().hasAccess(member, CmdAccessLevel.ADMIN); // if user has Admin access
	}

	private void buttonTicketCloseCancel(ButtonInteractionEvent event) {
		long channelId = event.getChannel().getIdLong();
		Guild guild = event.getGuild();
		if (db.tickets.isClosed(channelId)) {
			// Ticket is closed
			event.getChannel().delete().queue();
			return;
		}
		db.tickets.setRequestStatus(channelId, -1L);
		MessageEmbed embed = new EmbedBuilder()
			.setColor(db.getGuildSettings(guild).getColor())
			.setDescription(lu.getGuildText(event, "ticket.autoclose_cancel"))
			.build();
		event.getHook().editOriginalEmbeds(embed).setComponents().queue();
	}

	// Ticket management
	private void buttonTicketClaim(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper's access or higher to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		long channelId = event.getChannel().getIdLong();
		if (db.tickets.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.tickets.setClaimed(channelId, event.getUser().getIdLong());
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.ticketing.listener.claimed").replace("{user}", event.getUser().getAsMention()))
			.build()
		).queue();

		Button close = Button.danger("ticket:close", lu.getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒"));
		Button claimed = Button.primary("ticket:claimed", lu.getGuildText(event, "ticket.claimed", event.getUser().getName())).asDisabled();
		Button unclaim = Button.primary("ticket:unclaim", lu.getGuildText(event, "ticket.unclaim"));
		event.getMessage().editMessageComponents(ActionRow.of(close, claimed, unclaim)).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
	}

	private void buttonTicketUnclaim(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.HELPER)) {
			// User has no Helper's access or higher to approve role request
			sendError(event, "errors.interaction.no_access");
			return;
		}
		long channelId = event.getChannel().getIdLong();
		if (db.tickets.isClosed(channelId)) {
			sendError(event, "bot.ticketing.listener.is_closed");
			return;
		}

		db.tickets.setUnclaimed(channelId);
		event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.ticketing.listener.unclaimed"))
			.build()
		).queue();

		Button close = Button.danger("ticket:close", lu.getGuildText(event, "ticket.close")).withEmoji(Emoji.fromUnicode("🔒"));
		Button claim = Button.primary("ticket:claim", lu.getGuildText(event, "ticket.claim"));
		event.getMessage().editMessageComponents(ActionRow.of(close, claim)).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
	}

	// Tag, create ticket
	private void buttonTagCreateTicket(ButtonInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		int tagId = Integer.parseInt(event.getComponentId().split(":")[1]);

		Long channelId = db.tickets.getOpenedChannel(event.getMember().getIdLong(), guildId, tagId);
		if (channelId != null) {
			GuildChannel channel = event.getGuild().getGuildChannelById(channelId);
			if (channel != null) {
				event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_FAILURE)
					.setDescription(lu.getGuildText(event, "bot.ticketing.listener.ticket_exists", channel.getAsMention()))
					.build()
				).setEphemeral(true).queue();
				return;
			}
			ignoreExc(() -> db.tickets.closeTicket(Instant.now(), channelId, "BOT: Channel deleted (not found)"));
		}

		Tag tag = db.ticketTags.getTagInfo(tagId);
		if (tag == null) {
			sendTicketError(event, "Unknown tag with ID: "+tagId);
			return;
		}

		User user = event.getUser();

		// Pings text
		StringBuffer mentions = new StringBuffer(user.getAsMention());
		List<String> supportRoles = tag.getSupportRoles();
		mentions.append("||");
		supportRoles.forEach(roleId -> mentions.append(" <@&%s>".formatted(roleId)));
		mentions.append("||");

		// Ticket message
		String message = Optional.ofNullable(tag.getMessage())
			.map(text -> text.replace("{username}", user.getName()).replace("{tag_username}", user.getAsMention()))
			.orElse("Ticket's controls");

		int ticketId = 1 + db.tickets.lastIdByTag(guildId, tagId);
		String ticketName = (tag.getTicketName()+ticketId).replace("{username}", user.getName());
		if (tag.getTagType() == 1) {
			// Thread ticket
			event.getChannel().asTextChannel().createThreadChannel(ticketName, true).setInvitable(false).queue(channel -> {
				db.tickets.addTicket(
					ticketId, user.getIdLong(), guildId, channel.getIdLong(), tagId,
					bot.getDBUtil().getTicketSettings(event.getGuild()).getTimeToReply()
				);

				bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			},
			failure -> sendTicketError(event, "Unable to create new thread in this channel"));
		} else {
			// Channel ticket
			Category category = Optional.ofNullable(tag.getLocation()).map(id -> event.getGuild().getCategoryById(id)).orElse(event.getChannel().asTextChannel().getParentCategory());
			if (category == null) {
				sendTicketError(event, "Target category not found, with ID: "+tag.getLocation());
				return;
			}

			ChannelAction<TextChannel> action = category.createTextChannel(ticketName).clearPermissionOverrides();
			for (String roleId : supportRoles) action = action.addRolePermissionOverride(Long.parseLong(roleId), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null);
			action.addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
				.addMemberPermissionOverride(user.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
				.queue(channel -> {
					db.tickets.addTicket(
						ticketId, user.getIdLong(), guildId, channel.getIdLong(), tagId,
						bot.getDBUtil().getTicketSettings(event.getGuild()).getTimeToReply()
					);

					bot.getTicketUtil().createTicket(event, channel, mentions.toString(), message);
			},
			failure -> sendTicketError(event, "Unable to create new channel in target category, with ID: "+tag.getLocation()));
		}
	}

	private void sendTicketError(ButtonInteractionEvent event, String reason) {
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "bot.ticketing.listener.cant_create", reason)).setEphemeral(true).queue();
	}

	// Report
	private void buttonReportDelete(ButtonInteractionEvent event) {
		event.getHook().editOriginalComponents().queue();

		String channelId = event.getComponentId().split(":")[1];
		String messageId = event.getComponentId().split(":")[2];

		TextChannel channel = event.getGuild().getTextChannelById(channelId);
		if (channel == null) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "misc.unknown", "Unknown channel")).queue();
			return;
		}
		channel.deleteMessageById(messageId).reason("Deleted by %s".formatted(event.getMember().getEffectiveName())).queue(success ->
			event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, "menus.report.deleted", event.getMember().getAsMention()))
				.build()
			).queue(),
		failure -> event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getError(event, "misc.unknown", failure.getMessage())).queue()
		);
	}

	// Voice
	private void buttonVoiceLock(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).deny(Permission.VOICE_CONNECT).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VOICE_CONNECT).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.lock");
	}

	private void buttonVoiceUnlock(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).grant(Permission.VOICE_CONNECT).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VOICE_CONNECT).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.unlock");
	}

	private void buttonVoiceGhost(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).deny(Permission.VIEW_CHANNEL).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).deny(Permission.VIEW_CHANNEL).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.ghost");
	}

	private void buttonVoiceUnghost(ButtonInteractionEvent event, VoiceChannel vc) {
		// Verify role
		Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

		try {
			if (verifyRoleId != null) {
				Role verifyRole = event.getGuild().getRoleById(verifyRoleId);
				if (verifyRole != null) {
					vc.upsertPermissionOverride(verifyRole).grant(Permission.VIEW_CHANNEL).queue();
				}
			} else {
				vc.upsertPermissionOverride(event.getGuild().getPublicRole()).clear(Permission.VIEW_CHANNEL).queue();
			}
		} catch (InsufficientPermissionException ex) {
			event.getHook().sendMessage(bot.getEmbedUtil().createPermError(event, ex.getPermission(), true)).setEphemeral(true).queue();
			return;
		}
		sendSuccess(event, "bot.voice.listener.panel.unghost");
	}

	private void buttonVoicePermit(ButtonInteractionEvent event) {
		String text = lu.getGuildText(event, "bot.voice.listener.panel.permit_label");
		event.getHook().sendMessage(text).addActionRow(EntitySelectMenu.create("voice:permit", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
	}

	private void buttonVoiceReject(ButtonInteractionEvent event) {
		String text = lu.getGuildText(event, "bot.voice.listener.panel.reject_label");
		event.getHook().sendMessage(text).addActionRow(EntitySelectMenu.create("voice:reject", SelectTarget.USER, SelectTarget.ROLE).setMaxValues(10).build()).setEphemeral(true).queue();
	}

	private void buttonVoicePerms(ButtonInteractionEvent event, VoiceChannel vc) {
		Guild guild = event.getGuild();
		EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed()
			.setTitle(lu.getGuildText(event, "bot.voice.listener.panel.perms.title", vc.getAsMention()))
			.setDescription(lu.getGuildText(event, "bot.voice.listener.panel.perms.field")+"\n\n");

		//@Everyone
		PermissionOverride publicOverride = vc.getPermissionOverride(guild.getPublicRole());

		String view = contains(publicOverride, Permission.VIEW_CHANNEL);
		String join = contains(publicOverride, Permission.VOICE_CONNECT);

		embedBuilder = embedBuilder.appendDescription("> %s | %s | `%s`\n\n%s\n".formatted(view, join, lu.getGuildText(event, "bot.voice.listener.panel.perms.everyone"),
			lu.getGuildText(event, "bot.voice.listener.panel.perms.roles")));

		//Roles
		List<PermissionOverride> overrides = new ArrayList<>(vc.getRolePermissionOverrides()); // cause given override list is immutable
		try {
			overrides.remove(vc.getPermissionOverride(guild.getBotRole())); // removes bot's role
			overrides.remove(vc.getPermissionOverride(guild.getPublicRole())); // removes @everyone role
		} catch (NullPointerException ex) {
			log.warn("PermsCmd null pointer at role override remove");
		}

		if (overrides.isEmpty()) {
			embedBuilder.appendDescription(lu.getGuildText(event, "bot.voice.listener.panel.perms.none") + "\n");
		} else {
			for (PermissionOverride ov : overrides) {
				view = contains(ov, Permission.VIEW_CHANNEL);
				join = contains(ov, Permission.VOICE_CONNECT);

				embedBuilder.appendDescription("> %s | %s | `%s`\n".formatted(view, join, ov.getRole().getName()));
			}
		}
		embedBuilder.appendDescription("\n%s\n".formatted(lu.getGuildText(event, "bot.voice.listener.panel.perms.members")));

		//Members
		overrides = new ArrayList<>(vc.getMemberPermissionOverrides());
		try {
			overrides.remove(vc.getPermissionOverride(event.getMember())); // removes user
			overrides.remove(vc.getPermissionOverride(guild.getSelfMember())); // removes bot
		} catch (NullPointerException ex) {
			log.warn("PermsCmd null pointer at member override remove");
		}

		EmbedBuilder embedBuilder2 = embedBuilder;
		List<PermissionOverride> ovs = overrides;

		guild.retrieveMembersByIds(false, overrides.stream().map(PermissionOverride::getId).toArray(String[]::new)).onSuccess(
			members -> {
				if (members.isEmpty()) {
					embedBuilder2.appendDescription(lu.getGuildText(event, "bot.voice.listener.panel.perms.none") + "\n");
				} else {
					for (PermissionOverride ov : ovs) {
						String view2 = contains(ov, Permission.VIEW_CHANNEL);
						String join2 = contains(ov, Permission.VOICE_CONNECT);

						String name = members.stream()
							.filter(m -> m.getId().equals(ov.getId()))
							.findFirst()
							.map(Member::getEffectiveName)
							.orElse("Unknown");
						embedBuilder2.appendDescription("> %s | %s | `%s`\n".formatted(view2, join2, name));
					}
				}

				event.getHook().sendMessageEmbeds(embedBuilder2.build()).setEphemeral(true).queue();
			}
		);
	}

	private void buttonVoiceDelete(ButtonInteractionEvent event, VoiceChannel vc) {
		bot.getDBUtil().voice.remove(vc.getIdLong());

		vc.delete().reason("Channel owner request").queue();
		sendSuccess(event, "bot.voice.listener.panel.delete");
	}

	// Blacklist
	private void buttonBlacklist(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		String targetId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(targetId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.blacklist.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.blacklist.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.blacklist.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.blacklist.value"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> waiter.waitForEvent(
			StringSelectInteractionEvent.class,
			e -> e.getMessageId().equals(msg.getId()),
			selectEvent -> {
				selectEvent.deferEdit().queue();
				List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

				event.getJDA().retrieveUserById(targetId).queue(target -> {
					selected.forEach(groupId -> {
						if (!db.serverBlacklist.inGroupUser(groupId, caseData.getTargetId())) {
							db.serverBlacklist.add(selectEvent.getGuild().getIdLong(), groupId, target.getIdLong(), caseData.getReason(), selectEvent.getUser().getIdLong());
						}

						bot.getHelper().runBan(groupId, event.getGuild(), target, caseData.getReason(), event.getUser());
					});

					// Log to master
					bot.getGuildLogger().mod.onBlacklistAdded(event.getUser(), target, selected);
					// Reply
					selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
						.setColor(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, "bot.moderation.blacklist.done"))
						.build())
					.setComponents().queue();
				},
				failure -> selectEvent.getHook().editOriginalEmbeds(
					bot.getEmbedUtil().getError(selectEvent, "bot.moderation.blacklist.no_user", failure.getMessage())
				).setComponents().queue());
			},
			20,
			TimeUnit.SECONDS,
			() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
		));
	}

	private void buttonSyncBan(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		if (bot.getHelper() == null) {
			sendError(event, "errors.no_helper");
			return;
		}

		String targetId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(targetId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.sync.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.sync.ban.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> waiter.waitForEvent(
			StringSelectInteractionEvent.class,
			e -> e.getMessageId().equals(msg.getId()),
			selectEvent -> {
				selectEvent.deferEdit().queue();
				List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

				event.getJDA().retrieveUserById(targetId).queue(target -> {
					selected.forEach(groupId -> bot.getHelper().runBan(groupId, event.getGuild(), target, caseData.getReason(), event.getUser()));
					// Reply
					selectEvent.getHook().editOriginalEmbeds(
						bot.getEmbedUtil().getEmbed()
							.setColor(Constants.COLOR_SUCCESS)
							.setDescription(lu.getGuildText(event, "bot.moderation.sync.ban.done"))
							.build())
						.setComponents().queue();
				},
				failure -> selectEvent.getHook().editOriginalEmbeds(
					bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
				).setComponents().queue());
			},
			20,
			TimeUnit.SECONDS,
			() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
		));
	}

	private void buttonSyncUnban(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.sync.unban.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> waiter.waitForEvent(
			StringSelectInteractionEvent.class,
			e -> e.getMessageId().equals(msg.getId()),
			selectEvent -> {
				selectEvent.deferEdit().queue();
				List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

				event.getJDA().retrieveUserById(event.getComponentId().split(":")[1]).queue(target -> {
					selected.forEach(groupId -> {
						if (db.serverBlacklist.inGroupUser(groupId, target.getIdLong())) {
							ignoreExc(() -> db.serverBlacklist.removeUser(groupId, target.getIdLong()));
							bot.getGuildLogger().mod.onBlacklistRemoved(event.getUser(), target, groupId);
						}

						bot.getHelper().runUnban(groupId, event.getGuild(), target, "Sync group unban, by "+event.getUser().getName(), event.getUser());
					});

					// Reply
					selectEvent.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
						.setColor(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, "bot.moderation.sync.unban.done"))
						.build())
					.setComponents().queue();
				},
				failure -> selectEvent.getHook().editOriginalEmbeds(
					bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
				).setComponents().queue());
			},
			20,
			TimeUnit.SECONDS,
			() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
		));
	}

	private void buttonSyncKick(ButtonInteractionEvent event) {
		if (!bot.getCheckUtil().hasAccess(event.getMember(), CmdAccessLevel.OPERATOR)) {
			sendError(event, "errors.interaction.no_access");
			return;
		}

		if (bot.getHelper() == null) {
			sendError(event, "errors.no_helper");
			return;
		}

		String targetId = event.getComponentId().split(":")[1];
		CaseData caseData = db.cases.getMemberActive(Long.parseLong(targetId), event.getGuild().getIdLong(), CaseType.BAN);
		if (caseData == null || !caseData.getDuration().isZero()) {
			sendError(event, "bot.moderation.sync.expired");
			return;
		}

		long guildId = event.getGuild().getIdLong();
		List<Integer> groupIds = new ArrayList<>();
		groupIds.addAll(bot.getDBUtil().group.getOwnedGroups(guildId));
		groupIds.addAll(bot.getDBUtil().group.getManagedGroups(guildId));
		if (groupIds.isEmpty()) {
			sendError(event, "bot.moderation.sync.no_groups");
			return;
		}

		MessageEmbed embed = bot.getEmbedUtil().getEmbed()
			.setColor(Constants.COLOR_WARNING)
			.setDescription(lu.getGuildText(event, "bot.moderation.sync.kick.title"))
			.build();
		StringSelectMenu menu = StringSelectMenu.create("groupId")
			.setPlaceholder(lu.getGuildText(event, "bot.moderation.sync.select"))
			.addOptions(groupIds.stream().map(groupId ->
				SelectOption.of(bot.getDBUtil().group.getName(groupId), groupId.toString()).withDescription("ID: "+groupId)
			).collect(Collectors.toList()))
			.setMaxValues(MAX_GROUP_SELECT)
			.build();

		event.getHook().sendMessageEmbeds(embed).setActionRow(menu).setEphemeral(true).queue(msg -> {
			waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				selectEvent -> {
					selectEvent.deferEdit().queue();
					List<Integer> selected = selectEvent.getValues().stream().map(Integer::parseInt).toList();

					event.getJDA().retrieveUserById(targetId).queue(target -> {
						selected.forEach(groupId -> bot.getHelper().runKick(groupId, event.getGuild(), target, caseData.getReason(), event.getUser()));
						// Reply
						selectEvent.getHook().editOriginalEmbeds(
							bot.getEmbedUtil().getEmbed()
								.setColor(Constants.COLOR_SUCCESS)
								.setDescription(lu.getGuildText(event, "bot.moderation.sync.kick.done"))
								.build())
							.setComponents().queue();
					},
					failure -> selectEvent.getHook().editOriginalEmbeds(
						bot.getEmbedUtil().getError(selectEvent, "bot.moderation.sync.no_user", failure.getMessage())
					).setComponents().queue());
				},
				20,
				TimeUnit.SECONDS,
				() -> msg.editMessageComponents(ActionRow.of(menu.asDisabled())).queue()
			);
		});
	}

	// Strikes
	private void buttonShowStrikes(ButtonInteractionEvent event) {
		long guildId = Long.parseLong(event.getComponentId().split(":")[1]);
		Guild guild = event.getJDA().getGuildById(guildId);
		if (guild == null) {
			sendError(event, "errors.error", "Server not found.");
			return;
		}
		Pair<Integer, Integer> strikeData = bot.getDBUtil().strikes.getDataCountAndDate(guildId, event.getUser().getIdLong());
		if (strikeData == null) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
				.setDescription(lu.getText(event, "bot.moderation.no_strikes", guild.getName()))
				.build()).queue();
			return;
		}

		Instant time = Instant.ofEpochSecond(strikeData.getRight());
		event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed()
			.setDescription(lu.getText(event, "bot.moderation.strikes_embed", strikeData.getLeft(), TimeFormat.RELATIVE.atInstant(time)))
			.build()
		).setEphemeral(true).queue();
	}

	// Roles modify
	private void buttonModifyConfirm(ButtonInteractionEvent event) {
		long guildId = event.getGuild().getIdLong();
		long userId = event.getUser().getIdLong();
		long targetId = Long.parseLong(event.getComponentId().split(":")[2]);

		// If expired don't allow to modify embed
		if (db.modifyRole.isExpired(guildId, userId, targetId)) {
			event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.roles.role.modify.expired"))
				.setComponents().queue();
			return;
		}

		event.getGuild().retrieveMemberById(targetId).queue(target -> {
			List<Long> addIds = new ArrayList<>();
			List<Long> removeIds = new ArrayList<>();
			// Retrieve selected roles
			for (String line : db.modifyRole.getRoles(guildId, userId, targetId).split(":")) {
				if (line.isBlank()) continue;
				String[] roleIds = line.split(";");
				for (String roleId : roleIds) {
					// Check if first char is '+' add or '-' remove
					if (roleId.charAt(0) == '+') addIds.add(Long.parseLong(roleId.substring(1)));
					else removeIds.add(Long.parseLong(roleId.substring(1)));
				}
			}
			if (addIds.isEmpty() && removeIds.isEmpty()) {
				sendError(event, "bot.roles.role.modify.no_change");
				return;
			}

			Guild guild = target.getGuild();
			List<Role> finalRoles = new ArrayList<>(target.getRoles());
			finalRoles.addAll(addIds.stream().map(guild::getRoleById).toList());
			finalRoles.removeAll(removeIds.stream().map(guild::getRoleById).toList());

			guild.modifyMemberRoles(target, finalRoles).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				// Remove from DB
				db.modifyRole.remove(guildId, userId, targetId);
				// text
				StringBuilder builder = new StringBuilder();
				if (!addIds.isEmpty()) builder.append("\n**Added**: ")
					.append(addIds.stream().map(String::valueOf).collect(Collectors.joining(">, <@&", "<@&", ">")));
				if (!removeIds.isEmpty()) builder.append("\n**Removed**: ")
					.append(removeIds.stream().map(String::valueOf).collect(Collectors.joining(">, <@&", "<@&", ">")));
				String rolesString = builder.toString();
				// Log
				bot.getGuildLogger().role.onRolesModified(guild, event.getUser(), target.getUser(), rolesString);
				// Send reply
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, "bot.roles.role.modify.done", target.getAsMention(), rolesString))
					.build()
				).setComponents().queue();
			}, failure -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "errors.error", "Unable to modify roles, User ID: "+targetId))
				.setComponents().queue()
			);
		}, failure -> event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "errors.error", "Member not found, ID: "+targetId))
			.setComponents().queue()
		);
	}


	@Override
	public void onModalInteraction(@NotNull ModalInteractionEvent event) {
		// Check if blacklisted
		if (bot.getBlacklist().isBlacklisted(event)) return;

		event.deferEdit().queue();
		String[] modalId = event.getModalId().split(":");

		//noinspection SwitchStatementWithTooFewBranches
		switch (modalId[0]) {
			case "role_temp" -> modalTempRole(event, castLong(modalId[1]));
		}
	}

	private void modalTempRole(ModalInteractionEvent event, long channelId) {
		// Check if ticket is open
		if (db.tickets.isClosed(channelId)) {
			// Ignore
			return;
		}
		Guild guild = event.getGuild();
		final long userId = db.tickets.getUserId(channelId);

		// Get roles and tempRoles
		List<Role> roles = new ArrayList<>();
		db.tickets.getRoleIds(channelId).forEach(v -> {
			long roleId = castLong(v.charAt(0) == 't' ? v.substring(1) : v);
			Role role = guild.getRoleById(roleId);
			if (role != null) roles.add(role);
		});
		if (roles.isEmpty()) {
			event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
				.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_none"))
				.setColor(Constants.COLOR_WARNING)
				.build()
			).setEphemeral(true).queue();
			return;
		}

		// Get member add set roles
		event.getGuild().retrieveMemberById(userId).queue(member -> {
			// Add role durations to list
			Map<Long, Duration> roleDurations = new HashMap<>();
			for (ModalMapping map : event.getValues()) {
				final long roleId = castLong(map.getId());
				final String value = map.getAsString();
				// Check duration
				final Duration duration;
				try {
					duration = TimeUtil.stringToDuration(value, false);
				} catch (FormatterException ex) {
					sendError(event, ex.getPath());
					return;
				}
				// Add to temp only if duration not zero and between 10 minutes and MAX_DAYS days
				if (!duration.isZero()) {
					if (duration.toMinutes() < 10 || duration.toDays() > TempRoleCmd.MAX_DAYS) {
						sendError(event, "bot.ticketing.listener.time_limit", "Received: "+duration);
						return;
					}
					roleDurations.put(roleId, duration);
				}
			}

			final int ticketId = db.tickets.getTicketId(channelId);
			// Modify roles
			event.getGuild().modifyMemberRoles(member, roles, null)
				.reason("Request role-" + ticketId + " approved by " + event.getMember().getEffectiveName())
				.queue(done -> {
					// Set claimed
					db.tickets.setClaimed(channelId, event.getMember().getIdLong());
					// Add tempRoles to db and log them
					roleDurations.forEach((id, duration) -> {
						ignoreExc(() ->
							bot.getDBUtil().tempRoles.add(guild.getIdLong(), id, userId, false, Instant.now().plus(duration))
						);
						// Log
						bot.getGuildLogger().role.onTempRoleAdded(guild, event.getUser(), member.getUser(), id, duration, false);
					});
					// Log approval
					bot.getGuildLogger().role.onApproved(member, event.getMember(), guild, roles, ticketId);
					// Reply and send DM to the target member
					event.getHook().sendMessageEmbeds(bot.getEmbedUtil().getEmbed(event)
						.setDescription(lu.getGuildText(event, "bot.ticketing.listener.role_added"))
						.setColor(Constants.COLOR_SUCCESS)
						.build()
					).queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_WEBHOOK));
					member.getUser().openPrivateChannel().queue(dm -> {
						dm.sendMessage(lu.getGuildText(event, "bot.ticketing.listener.role_dm")
							.replace("{roles}", roles.stream().map(Role::getName).collect(Collectors.joining(" | ")))
							.replace("{server}", guild.getName())
							.replace("{id}", String.valueOf(ticketId))
							.replace("{mod}", event.getMember().getEffectiveName())
						).queue(null, new ErrorHandler().ignore(ErrorResponse.CANNOT_SEND_TO_USER));
					});
				}, failure -> {
					sendError(event, "bot.ticketing.listener.role_failed", failure.getMessage());
				});
		}, failure -> {
			sendError(event, "bot.ticketing.listener.no_member", failure.getMessage());
		});
	}

	@Override
	public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
		// Check if blacklisted
		if (bot.getBlacklist().isBlacklisted(event)) return;
		
		String menuId = event.getComponentId();

		if (menuId.startsWith("menu:role_row")) {
			event.deferEdit().queue(null, new ErrorHandler().ignore(ErrorResponse.UNKNOWN_INTERACTION));

			List<Field> fields = event.getMessage().getEmbeds().getFirst().getFields();
			List<Long> roleIds = MessageUtil.getRoleIdsFromString(fields.isEmpty() ? "" : fields.getFirst().getValue());
			event.getSelectedOptions().forEach(option -> {
				Long value = castLong(option.getValue());
				if (!roleIds.contains(value)) roleIds.add(value);
			});

			MessageEmbed embed = new EmbedBuilder(event.getMessage().getEmbeds().getFirst())
				.clearFields()
				.addField(lu.getGuildText(event, "bot.ticketing.listener.request_selected"), selectedRolesString(roleIds, lu.getLocale(event)), false)
				.build();
			event.getHook().editOriginalEmbeds(embed).queue();
		} else if (menuId.startsWith("role:manage-select")) {
			listModifySelect(event);
		}
	}

	private final Pattern splitPattern = Pattern.compile(":");

	// Roles modify
	private void listModifySelect(StringSelectInteractionEvent event) {
		event.deferEdit().queue();
		try {
			long guildId = event.getGuild().getIdLong();
			long userId = event.getUser().getIdLong();
			long targetId = Long.parseLong(event.getComponentId().split(":")[3]);

			// If expired don't allow to modify
			if (db.modifyRole.isExpired(guildId, userId, targetId)) {
				event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.roles.role.modify.expired"))
					.setComponents().queue();
				return;
			}

			List<String> changes = new ArrayList<>();

			List<SelectOption> defaultOptions = event.getSelectMenu().getOptions().stream().filter(SelectOption::isDefault).toList();
			List<SelectOption> selectedOptions = event.getSelectedOptions();
			// if default is not in selected - role is removed
			for (SelectOption option : defaultOptions) {
				if (!selectedOptions.contains(option)) changes.add("-"+option.getValue());
			}
			// if selected is not in default - role is added
			for (SelectOption option : selectedOptions) {
				if (!defaultOptions.contains(option)) changes.add("+"+option.getValue());
			}

			String newValue = String.join(";", changes);

			// "1:2:3:4"
			// each section stores changes for each menu
			int menuId = Integer.parseInt(event.getComponentId().split(":")[2]);
			String[] data = splitPattern.split(db.modifyRole.getRoles(guildId, userId, targetId), 4);
			if (data.length != 4) data = new String[]{"", "", "", ""};
			data[menuId-1] = newValue;
			db.modifyRole.update(guildId, userId, targetId, String.join(":", data), Instant.now().plus(2, ChronoUnit.MINUTES));
		} catch(Throwable t) {
			// Log throwable and try to respond to the user with the error
			// Thrown errors are not user's error, but code's fault as such things should be caught earlier and replied properly
			log.error("Role modify Exception", t);
			bot.getEmbedUtil().sendUnknownError(event.getHook(), lu.getLocale(event), t.getMessage());
		}
	}

	@Override
	public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event) {
		String menuId = event.getComponentId();
		if (menuId.startsWith("voice")) {
			event.deferEdit().queue();

			Member author = event.getMember();
			if (!author.getVoiceState().inAudioChannel()) {
				sendError(event, "bot.voice.listener.not_in_voice");
				return;
			}
			Long channelId = db.voice.getChannel(author.getIdLong());
			if (channelId == null) {
				sendError(event, "errors.no_channel");
				return;
			}
			Guild guild = event.getGuild();
			VoiceChannel vc = guild.getVoiceChannelById(channelId);
			if (vc == null) return;
			String action = menuId.split(":")[1];
			if (action.equals("permit") || action.equals("reject")) {
				Mentions mentions = event.getMentions();

				List<Member> members = mentions.getMembers();
				List<Role> roles = mentions.getRoles();
				if (members.isEmpty() && roles.isEmpty()) {
					return;
				}
				if (members.contains(author) || members.contains(guild.getSelfMember())) {
					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getError(event, "bot.voice.listener.panel.not_self"))
						.setContent("").setComponents().queue();
					return;
				}

				List<String> mentionStrings = new ArrayList<>();
				String text;

				VoiceChannelManager manager = vc.getManager();

				if (action.equals("permit")) {
					for (Member member : members) {
						manager = manager.putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
						mentionStrings.add(member.getEffectiveName());
					}

					for (Role role : roles) {
						EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
						rolePerms.retainAll(adminPerms);
						if (rolePerms.isEmpty()) {
							manager = manager.putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
							mentionStrings.add(role.getName());
						}
					}

					text = lu.getTargetText(event, "bot.voice.listener.panel.permit_done", mentionStrings);
				} else {
					for (Member member : members) {
						manager = manager.putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
						if (vc.getMembers().contains(member)) {
							guild.kickVoiceMember(member).queue();
						}
						mentionStrings.add(member.getEffectiveName());
					}

					for (Role role : roles) {
						EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
						rolePerms.retainAll(adminPerms);
						if (rolePerms.isEmpty()) {
							manager = manager.putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
							mentionStrings.add(role.getName());
						}
					}

					text = lu.getTargetText(event, "bot.voice.listener.panel.reject_done", mentionStrings);
				}

				final MessageEmbed embed = bot.getEmbedUtil().getEmbed(event).setDescription(text).build();
				manager.queue(done -> {
					event.getHook()
						.editOriginalEmbeds(embed)
						.setContent("")
						.setComponents().queue();
				}, failure -> event.getHook()
					.editOriginal(MessageEditData.fromCreateData(bot.getEmbedUtil().createPermError(event, Permission.MANAGE_PERMISSIONS, true)))
					.setContent("")
					.setComponents().queue());
			}
		}
	}


	// TOOLS
	private String selectedRolesString(List<Long> roleIds, DiscordLocale locale) {
		if (roleIds.isEmpty()) return "None";
		return roleIds.stream()
			.map(id -> (id.equals(0L) ? "+"+lu.getLocalized(locale, "bot.ticketing.embeds.other") : "<@&%s>".formatted(id)))
			.collect(Collectors.joining(", "));
	}

	private String contains(PermissionOverride override, Permission perm) {
		if (override != null) {
			if (override.getAllowed().contains(perm))
				return Emote.CHECK_C.getEmote();
			else if (override.getDenied().contains(perm))
				return Emote.CROSS_C.getEmote();
		}
		return Emote.NONE.getEmote();
	}

	// Cooldown objects
	private enum Cooldown {
		BUTTON_VERIFY(10, CooldownScope.USER),
		BUTTON_ROLE_SHOW(20, CooldownScope.USER),
		BUTTON_ROLE_OTHER(2, CooldownScope.USER),
		BUTTON_ROLE_CLEAR(4, CooldownScope.USER),
		BUTTON_ROLE_REMOVE(10, CooldownScope.USER),
		BUTTON_ROLE_TOGGLE(2, CooldownScope.USER),
		BUTTON_ROLE_TICKET(30, CooldownScope.USER),
		BUTTON_ROLE_APPROVE(10, CooldownScope.CHANNEL),
		BUTTON_TICKET_CLOSE(10, CooldownScope.CHANNEL),
		BUTTON_TICKET_CANCEL(4, CooldownScope.CHANNEL),
		BUTTON_TICKET_CLAIM(20, CooldownScope.USER_CHANNEL),
		BUTTON_TICKET_UNCLAIM(20, CooldownScope.USER_CHANNEL),
		BUTTON_TICKET_CREATE(30, CooldownScope.USER),
		BUTTON_REPORT_DELETE(3, CooldownScope.GUILD),
		BUTTON_SHOW_STRIKES(30, CooldownScope.USER),
		BUTTON_SYNC_ACTION(10, CooldownScope.CHANNEL),
		BUTTON_MODIFY_CONFIRM(10, CooldownScope.USER);

		private final int time;
		private final CooldownScope scope;

		Cooldown(int time, @NotNull CooldownScope scope) {
			this.time = time;
			this.scope = scope;
		}

		public int getTime() {
			return this.time;
		}

		public CooldownScope getScope() {
			return this.scope;
		}
	}

	private String getCooldownKey(Cooldown cooldown, GenericInteractionCreateEvent event) {
		String name = cooldown.toString();
		CooldownScope cooldownScope = cooldown.getScope();
		return switch (cooldown.getScope()) {
			case USER -> cooldownScope.genKey(name, event.getUser().getIdLong());
			case USER_GUILD ->
				Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name, event.getUser().getIdLong(), g.getIdLong()))
					.orElse(CooldownScope.USER_CHANNEL.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong()));
			case USER_CHANNEL ->
				cooldownScope.genKey(name, event.getUser().getIdLong(), event.getChannel().getIdLong());
			case GUILD -> Optional.of(event.getGuild()).map(g -> cooldownScope.genKey(name, g.getIdLong()))
				.orElse(CooldownScope.CHANNEL.genKey(name, event.getChannel().getIdLong()));
			case CHANNEL -> cooldownScope.genKey(name, event.getChannel().getIdLong());
			case SHARD -> cooldownScope.genKey(name, event.getJDA().getShardInfo().getShardId());
			case USER_SHARD ->
				cooldownScope.genKey(name, event.getUser().getIdLong(), event.getJDA().getShardInfo().getShardId());
			case GLOBAL -> cooldownScope.genKey(name, 0);
		};
	}

	@NotNull
	private String getCooldownErrorString(Cooldown cooldown, GenericInteractionCreateEvent event, int remaining) {
		CooldownScope scope = cooldown.getScope();
		String descriptor;
		if (scope.equals(CooldownScope.USER_GUILD) && event.getGuild()==null)
			descriptor = lu.getLocalized(event.getUserLocale(), CooldownScope.USER_CHANNEL.getErrorPath());
		else if (scope.equals(CooldownScope.GUILD) && event.getGuild()==null)
			descriptor = lu.getLocalized(event.getUserLocale(), CooldownScope.CHANNEL.getErrorPath());
		else if (!scope.equals(CooldownScope.USER))
			descriptor = lu.getLocalized(event.getUserLocale(), scope.getErrorPath());
		else
			descriptor = null;

		return lu.getLocalized(event.getUserLocale(), "errors.cooldown.cooldown_button")
			.formatted(descriptor == null ? "" : descriptor, TimeFormat.RELATIVE.after(remaining));
	}


	private void ignoreExc(RunnableExc runnable) {
		try {
			runnable.run();
		} catch (SQLException ignored) {}
	}

	@FunctionalInterface public interface RunnableExc { void run() throws SQLException; }
}

package dev.fireatom.FABI.commands.moderation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.base.waiter.EventWaiter;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.Limits;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class GroupCmd extends SlashCommand {
	
	private final EventWaiter waiter;

	public GroupCmd() {
		this.name = "group";
		this.path = "bot.moderation.group";
		this.children = new SlashCommand[]{
			new Create(), new Delete(), new Remove(), new GenerateInvite(),
			new Join(), new Leave(), new Modify(), new Manage(), new View()
		};
		this.category = CmdCategory.MODERATION;
		this.module = CmdModule.MODERATION;
		this.accessLevel = CmdAccessLevel.OPERATOR;
		this.waiter = App.getInstance().getEventWaiter();
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Create extends SlashCommand {
		public Create() {
			this.name = "create";
			this.path = "bot.moderation.group.create";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(100),
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help"))
					.setRequiredLength(12, 20)
			);
			addMiddlewares(
				"throttle:guild,1,30"
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long guildId = event.getGuild().getIdLong();

			if (bot.getDBUtil().group.countOwnedGroups(guildId) >= Limits.OWNED_GROUPS) {
				editErrorLimit(event, "owned groups", Limits.OWNED_GROUPS);
				return;
			}

			String groupName = event.optString("name");

			long appealGuildId = 0L;
			if (event.hasOption("appeal_server")) {
				try {
					appealGuildId = Long.parseLong(event.optString("appeal_server"));
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
				if (appealGuildId != 0L && event.getJDA().getGuildById(appealGuildId) == null) {
					editErrorOther(event, "Unknown appeal server ID.\nReceived: "+appealGuildId);
					return;
				}
			}

			final int groupId;
			try {
				groupId = bot.getDBUtil().group.create(guildId, groupName, appealGuildId);
			} catch (SQLException e) {
				editErrorOther(event, "Failed to create new group.");
				return;
			}
			bot.getGuildLogger().group.onCreation(event, groupId, groupName);

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", groupName, groupId))
				.build()
			);
		}
	}

	private class Delete extends SlashCommand {
		public Delete() {
			this.name = "delete";
			this.path = "bot.moderation.group.delete";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true)
					.setMinValue(1)
			);
			addMiddlewares(
				"throttle:guild,1,30"
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			try {
				bot.getDBUtil().group.deleteGroup(groupId);
				bot.getGuildLogger().group.onDeletion(event, groupId, groupName);
				bot.getDBUtil().group.clearGroup(groupId);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "delete group");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", groupName, groupId))
				.build()
			);
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.moderation.group.remove";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true)
					.setMinValue(1)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream()
				.map(event.getJDA()::getGuildById)
				.filter(Objects::nonNull)
				.toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".embed_title"))
				.setDescription(lu.getGuildText(event, path+".embed_value", groupName))
				.build();
			
			List<ActionRow> rows = new ArrayList<>();
			List<SelectOption> options = new ArrayList<>();
			for (Guild guild : guilds) {
				options.add(SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()));
				if (options.size() >= 25) {
					rows.add(ActionRow.of(
							StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
								.setPlaceholder("Select")
								.setMaxValues(1)
								.addOptions(options)
								.build()
						)
					);
					options = new ArrayList<>();
				}
			}
			if (!options.isEmpty()) {
				rows.add(ActionRow.of(
						StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
							.setPlaceholder("Select")
							.setMaxValues(1)
							.addOptions(options)
							.build()
					)
				);
			}
			event.getHook().editOriginalEmbeds(embed).setComponents(rows).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()),
				actionMenu -> {
					long targetId = Long.parseLong(actionMenu.getSelectedOptions().getFirst().getValue());
					Guild targetGuild = event.getJDA().getGuildById(targetId);

					try {
						bot.getDBUtil().group.remove(groupId, targetId);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "remove group member");
						return;
					}
					if (targetGuild != null)
						bot.getGuildLogger().group.onGuildRemoved(event, targetGuild, groupId, groupName);

					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, path+".done", Optional.ofNullable(targetGuild).map(Guild::getName).orElse("*Unknown*"), groupName))
						.build()
					).setComponents().queue();
				},
				30,
				TimeUnit.SECONDS,
				() -> {
					event.getHook().editOriginalComponents(
						ActionRow.of(StringSelectMenu.create("timed_out").setPlaceholder(lu.getGuildText(event, "errors.timed_out")).setDisabled(true).build())
					).queue();
				}
			));
		}
	}

	private class GenerateInvite extends SlashCommand {
		public GenerateInvite() {
			this.name = "generateinvite";
			this.path = "bot.moderation.group.generateinvite";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true)
					.setMinValue(1)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			int newInvite = ThreadLocalRandom.current().nextInt(100_000, 1_000_000); // 100000 - 999999

			try {
				bot.getDBUtil().group.setInvite(groupId, newInvite);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set group invite");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", groupName, newInvite))
				.build()
			);
		}
	}

	private class Modify extends SlashCommand {
		public Modify() {
			this.name = "modify";
			this.path = "bot.moderation.group.modify";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true)
					.setMinValue(1),
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"))
					.setMaxLength(100),
				new OptionData(OptionType.STRING, "appeal_server", lu.getText(path+".appeal_server.help"))
					.setRequiredLength(12, 20)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			int groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			String currentGroupName = bot.getDBUtil().group.getName(groupId);
			StringBuilder builder = new StringBuilder();

			if (event.hasOption("name")) {
				String newName = event.optString("name");

				try {
					bot.getDBUtil().group.rename(groupId, newName);
				} catch (SQLException e) {
					editErrorDatabase(event, e, "group rename");
					return;
				}
				bot.getGuildLogger().group.onRenamed(event, currentGroupName, groupId, newName);

				builder.append(lu.getGuildText(event, path+".changed_name", newName))
					.append("\n");
			}
			if (event.hasOption("appeal_server")) {
				long appealGuildId;

				try {
					appealGuildId = Long.parseLong(event.optString("appeal_server"));
				} catch (NumberFormatException ex) {
					editErrorOther(event, ex.getMessage());
					return;
				}
				if (appealGuildId != 0L && event.getJDA().getGuildById(appealGuildId) == null) {
					editErrorOther(event, "Unknown appeal server ID.\nReceived: "+appealGuildId);
					return;
				}

				try {
					bot.getDBUtil().group.setAppealGuildId(groupId, appealGuildId);
				} catch (SQLException ex) {
					editErrorDatabase(event, ex, "set appeal guild id");
					return;
				}

				builder.append(lu.getGuildText(event, path+".changed_appeal", appealGuildId))
					.append("\n");
			}

			if (builder.isEmpty()) {
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_WARNING)
					.setDescription(lu.getGuildText(event, path+".no_options"))
					.setFooter("Group ID: `%s`".formatted(groupId))
					.build()
				);
			} else {
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setTitle(lu.getGuildText(event, path+".done_title", currentGroupName))
					.setDescription(builder.toString())
					.setFooter("Group ID: #%s".formatted(groupId))
					.build()
				);
			}
		}
	}

	private class Manage extends SlashCommand {
		public Manage() {
			this.name = "manage";
			this.path = "bot.moderation.group.manage";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), true, true)
					.setMinValue(1),
				new OptionData(OptionType.BOOLEAN, "manage", lu.getText(path+".manage.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_owned");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null) {
				editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
				return;
			}
			if (event.getGuild().getIdLong() != ownerId) {
				editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
				return;
			}

			boolean canManage = event.optBoolean("manage");

			List<Guild> guilds = bot.getDBUtil().group.getGroupMembers(groupId).stream()
				.map(event.getJDA()::getGuildById)
				.filter(Objects::nonNull)
				.toList();
			if (guilds.isEmpty()) {
				editError(event, path+".no_guilds");
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);
			MessageEmbed embed = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".embed_title"))
				.setDescription(lu.getGuildText(event, path+".embed_value", groupName))
				.build();
			
			List<ActionRow> rows = new ArrayList<>();
			List<SelectOption> options = new ArrayList<>();
			for (Guild guild : guilds) {
				options.add(SelectOption.of("%s (%s)".formatted(guild.getName(), guild.getId()), guild.getId()));
				if (options.size() >= 25) {
					rows.add(ActionRow.of(
							StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
								.setPlaceholder("Select")
								.setMaxValues(1)
								.addOptions(options)
								.build()
						)
					);
					options = new ArrayList<>();
				}
			}
			if (!options.isEmpty()) {
				rows.add(ActionRow.of(
						StringSelectMenu.create("menu:select-guild:"+(rows.size()+1))
							.setPlaceholder("Select")
							.setMaxValues(1)
							.addOptions(options)
							.build()
					)
				);
			}
			event.getHook().editOriginalEmbeds(embed).setComponents(rows).queue(msg -> waiter.waitForEvent(
				StringSelectInteractionEvent.class,
				e -> e.getMessageId().equals(msg.getId()) && e.getUser().getIdLong() == event.getUser().getIdLong(),
				actionMenu -> {
					long targetId = Long.parseLong(actionMenu.getSelectedOptions().getFirst().getValue());
					Guild targetGuild = event.getJDA().getGuildById(targetId);

					StringBuilder builder = new StringBuilder(lu.getGuildText(event, path+".done",
						targetGuild.getName(), groupName));

					try {
						bot.getDBUtil().group.setManage(groupId, targetId, canManage);
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "set group manager");
						return;
					}
					builder.append(lu.getGuildText(event, path+".manage_change",
						canManage ? Constants.SUCCESS : Constants.FAILURE));

					event.getHook().editOriginalEmbeds(bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(builder.toString())
						.build()
					).setComponents().queue();
				},
				30,
				TimeUnit.SECONDS,
				() -> {
					event.getHook().editOriginalComponents(
						ActionRow.of(StringSelectMenu.create("timed_out").setPlaceholder(lu.getGuildText(event, "errors.timed_out")).setDisabled(true).build())
					).queue();
				}
			));
		}
	}

	private class Join extends SlashCommand {
		public Join() {
			this.name = "join";
			this.path = "bot.moderation.group.join";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "invite", lu.getText(path+".invite.help"), true)
					.setRequiredRange(100_000, 999_999)
			);
			addMiddlewares(
				"throttle:guild,1,30"
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long guildId = event.getGuild().getIdLong();

			if (bot.getDBUtil().group.countJoinedGroups(guildId) >= Limits.JOINED_GROUPS) {
				editErrorLimit(event, "joined groups", Limits.JOINED_GROUPS);
				return;
			}

			Integer invite = event.optInteger("invite");
			Integer groupId = bot.getDBUtil().group.getGroupByInvite(invite);
			if (groupId == null) {
				editError(event, path+".no_group");
				return;
			}

			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (guildId == ownerId) {
				editError(event, path+".failed_join", "This server is this Group's owner.\nGroup ID: `%s`".formatted(groupId));
				return;
			}
			if (bot.getDBUtil().group.isMember(groupId, guildId)) {
				editError(event, path+".is_member", "Group ID: `%s`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			try {
				bot.getDBUtil().group.add(groupId, guildId, false);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "add group member");
				return;
			}
			bot.getGuildLogger().group.onGuildJoined(event, groupId, groupName);

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", groupName))
				.build()
			);
		}		
	}

	private class Leave extends SlashCommand {
		public Leave() {
			this.name = "leave";
			this.path = "bot.moderation.group.leave";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), true, true)
					.setMinValue(1)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer groupId = event.optInteger("group_joined");
			Long ownerId = bot.getDBUtil().group.getOwner(groupId);
			if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, event.getGuild().getIdLong())) {
				editError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
				return;
			}

			String groupName = bot.getDBUtil().group.getName(groupId);

			try {
				bot.getDBUtil().group.remove(groupId, event.getGuild().getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "remove group member");
				return;
			}
			bot.getGuildLogger().group.onGuildLeft(event, groupId, groupName);

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", groupName))
				.build()
			);
		}
	}

	private class View extends SlashCommand {
		public View() {
			this.name = "view";
			this.path = "bot.moderation.group.view";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "group_owned", lu.getText(path+".group_owned.help"), false, true)
					.setMinValue(1),
				new OptionData(OptionType.INTEGER, "group_joined", lu.getText(path+".group_joined.help"), false, true)
					.setMinValue(1)
			);
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			long guildId = event.getGuild().getIdLong();
			if (event.hasOption("group_owned")) {
				// View owned Group information - name, every guild info (name, ID, member count)
				Integer groupId = event.optInteger("group_owned");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null) {
					editError(event, path+".no_group", "Group ID: `%d`".formatted(groupId));
					return;
				}
				if (event.getGuild().getIdLong() != ownerId) {
					editError(event, path+".not_owned", "Group ID: `%d`".formatted(groupId));
					return;
				}

				String groupName = bot.getDBUtil().group.getName(groupId);
				List<Long> memberIds = bot.getDBUtil().group.getGroupMembers(groupId);
				int groupSize = memberIds.size();
				String invite = Optional.ofNullable(bot.getDBUtil().group.getInvite(groupId)).map(o -> "||`"+o+"`||").orElse("-");

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getGuildText(event, path+".embed_title",
						groupName, groupId
					))
					.setDescription(lu.getGuildText(event, path+".embed_full",
						event.getGuild().getName(), event.getGuild().getId(), groupSize,
						Optional.ofNullable(bot.getDBUtil().group.getAppealGuildId(groupId)).map(String::valueOf).orElse("-")
					))
					.addField(lu.getGuildText(event, path+".embed_invite"), invite, false);
				
				if (groupSize > 0) {
					String fieldLabel = lu.getGuildText(event, path+".embed_guilds");
					StringBuilder stringBuilder = new StringBuilder();
					String format = "%s | %s | `%s`";
					for (Long memberId : memberIds) {
						Guild guild = event.getJDA().getGuildById(memberId);
						if (guild == null) continue;
	
						String line = format.formatted(guild.getName(), guild.getMemberCount(), guild.getId());
						if (stringBuilder.length() + line.length() + 2 > 1000) {
							builder.addField(fieldLabel, stringBuilder.toString(), false);
							stringBuilder.setLength(0);
							stringBuilder.append(line).append("\n");
							fieldLabel = "";
						} else {
							stringBuilder.append(line).append("\n");
						}
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}
				editEmbed(event, builder.build());
			} else if (event.hasOption("group_joined")) {
				// View joined Group information - name, master name/ID, guild count
				Integer groupId = event.optInteger("group_joined");
				Long ownerId = bot.getDBUtil().group.getOwner(groupId);
				if (ownerId == null || !bot.getDBUtil().group.isMember(groupId, guildId)) {
					editError(event, path+".no_group", "Group ID: `%s`".formatted(groupId));
					return;
				}
				
				String groupName = bot.getDBUtil().group.getName(groupId);
				String masterName = event.getJDA().getGuildById(ownerId).getName();
				int groupSize = bot.getDBUtil().group.countMembers(groupId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setAuthor(lu.getGuildText(event, "logger.groups.title",
						groupName, groupId
					)).setDescription(lu.getGuildText(event, path+".embed_short",
						masterName, ownerId, groupSize
					));
				editEmbed(event, builder.build());
			} else {
				// No options provided - reply with all groups that this guild is connected
				List<Integer> ownedGroups = bot.getDBUtil().group.getOwnedGroups(guildId);
				List<Integer> joinedGroupIds = bot.getDBUtil().group.getGuildGroups(guildId);

				EmbedBuilder builder = bot.getEmbedUtil().getEmbed()
					.setDescription("Group name | #ID");
				
				String fieldLabel = lu.getGuildText(event, path+".embed_owned");
				if (ownedGroups.isEmpty()) {
					builder.addField(fieldLabel, lu.getGuildText(event, path+".none"), false);
				} else {
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : ownedGroups) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				fieldLabel = lu.getGuildText(event, path+".embed_member");
				if (joinedGroupIds.isEmpty()) {
					builder.addField(fieldLabel, lu.getGuildText(event, path+".none"), false);
				} else {
					StringBuilder stringBuilder = new StringBuilder();
					for (Integer groupId : joinedGroupIds) {
						stringBuilder.append("%s | #%s\n".formatted(bot.getDBUtil().group.getName(groupId), groupId));
					}
					builder.addField(fieldLabel, stringBuilder.toString(), false);
				}

				editEmbed(event, builder.build());
			}
		}

	}

}

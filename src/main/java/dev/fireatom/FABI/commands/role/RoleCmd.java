package dev.fireatom.FABI.commands.role;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

@SuppressWarnings("Duplicates")
public class RoleCmd extends SlashCommand {
	
	public RoleCmd() {
		this.name = "role";
		this.path = "bot.roles.role";
		this.children = new SlashCommand[]{
			new Add(), new Remove(), new RemoveAll(),
			new Modify()
		};
		this.category = CmdCategory.ROLES;
		this.module = CmdModule.ROLES;
		this.accessLevel = CmdAccessLevel.HELPER;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}
	
	private class Add extends SlashCommand {
		public Add() {
			this.name = "add";
			this.path = "bot.roles.role.add";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.ROLE, "role1", lu.getText(path+".role1.help"), true),
				new OptionData(OptionType.ROLE, "role2", lu.getText(path+".role2.help"), false),
				new OptionData(OptionType.ROLE, "role3", lu.getText(path+".role3.help"), false)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			// Get roles
			List<Role> roles = new ArrayList<>(3);

			Optional.ofNullable(event.optRole("role1")).ifPresent(roles::add);
			Optional.ofNullable(event.optRole("role2")).ifPresent(roles::add);
			Optional.ofNullable(event.optRole("role3")).ifPresent(roles::add);

			if (roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}

			// Check roles
			final boolean whitelistEnabled = bot.getDBUtil().getGuildSettings(guild).isRoleWhitelistEnabled();
			for (Role r : roles) {
				String denyReason = bot.getCheckUtil().denyRole(r, event.getGuild(), event.getMember(), true);
				if (denyReason != null) {
					editError(event, path+".incorrect_role", "Role: %s\n> %s".formatted(r.getAsMention(), denyReason));
					return;
				}
				// Check if role whitelisted
				if (whitelistEnabled) {
					if (!bot.getDBUtil().roles.existsRole(r.getIdLong())) {
						// Not whitelisted
						editError(event, path+".not_whitelisted", "Role: %s".formatted(r.getAsMention()));
						return;
					}
				}
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}
			List<Role> finalRoles = new ArrayList<>(member.getRoles());
			finalRoles.addAll(roles);

			guild.modifyMemberRoles(member, finalRoles).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				String rolesString = roles.stream().map(Role::getAsMention).collect(Collectors.joining(", "));
				// Log
				bot.getGuildLogger().role.onRolesAdded(guild, event.getUser(), member.getUser(), rolesString);
				// Send reply
				editEmbed(event, bot.getEmbedUtil().getEmbed()
					.setColor(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, path+".done", rolesString, member.getAsMention()))
					.build());
			}, failure -> editError(event, path+".failed", failure.getMessage()));
		}
	}

	private class Remove extends SlashCommand {
		public Remove() {
			this.name = "remove";
			this.path = "bot.roles.role.remove";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path+".user.help"), true),
				new OptionData(OptionType.ROLE, "role1", lu.getText(path+".role1.help"), true),
				new OptionData(OptionType.ROLE, "role2", lu.getText(path+".role2.help"), false),
				new OptionData(OptionType.ROLE, "role3", lu.getText(path+".role3.help"), false)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());

			// Get roles
			List<Role> roles = new ArrayList<>(3);

			Optional.ofNullable(event.optRole("role1")).ifPresent(roles::add);
			Optional.ofNullable(event.optRole("role2")).ifPresent(roles::add);
			Optional.ofNullable(event.optRole("role3")).ifPresent(roles::add);

			if (roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}

			// Check roles
			final boolean whitelistEnabled = bot.getDBUtil().getGuildSettings(guild).isRoleWhitelistEnabled();
			for (Role r : roles) {
				String denyReason = bot.getCheckUtil().denyRole(r, event.getGuild(), event.getMember(), true);
				if (denyReason != null) {
					editError(event, path+".incorrect_role", "Role: %s\n> %s".formatted(r.getAsMention(), denyReason));
					return;
				}
				// Check if role whitelisted
				if (whitelistEnabled) {
					if (!bot.getDBUtil().roles.existsRole(r.getIdLong())) {
						// Not whitelisted
						editError(event, path+".not_whitelisted", "Role: %s".formatted(r.getAsMention()));
						return;
					}
				}
			}
			// Check member
			Member member = event.optMember("user");
			if (member == null) {
				editError(event, path+".no_member");
				return;
			}

			List<Role> finalRoles = new ArrayList<>(member.getRoles());
			finalRoles.removeAll(roles);

			guild.modifyMemberRoles(member, finalRoles).reason("by "+event.getMember().getEffectiveName()).queue(done -> {
				String rolesString = roles.stream().map(Role::getAsMention).collect(Collectors.joining(", "));
				// Log
				bot.getGuildLogger().role.onRolesRemoved(guild, event.getUser(), member.getUser(), rolesString);
				// Send reply
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getGuildText(event, path+".done", rolesString, member.getAsMention()))
					.build());
			}, failure -> editError(event, path+".failed", failure.getMessage()));
		}
	}

	private class RemoveAll extends SlashCommand {
		public RemoveAll() {
			this.name = "removeall";
			this.path = "bot.roles.role.removeall";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
			this.accessLevel = CmdAccessLevel.ADMIN;
			addMiddlewares(
				"throttle:guild,1,30"
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = Objects.requireNonNull(event.getGuild());
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			String denyReason = bot.getCheckUtil().denyRole(role, event.getGuild(), event.getMember(), false);
			if (denyReason != null) {
				editError(event, path+".incorrect_role", "Role: %s\n> %s".formatted(role.getAsMention(), denyReason));
				return;
			}

			EmbedBuilder builder = bot.getEmbedUtil().getEmbed().setDescription(lu.getGuildText(event, path+".started"));
			editEmbed(event, builder.build());

			event.getGuild().findMembersWithRoles(role).setTimeout(4, TimeUnit.SECONDS).onSuccess(members -> {
				int maxSize = members.size();
				if (maxSize == 0) {
					editError(event, path+".empty");
					return;
				}
				if (maxSize > 400) {
					editErrorOther(event, "Amount of members to be processed reached maximum limit of **400**! Manually clear the selected role.");
					return;
				}
				editEmbed(event, builder.appendDescription(lu.getGuildText(event, path+".estimate", maxSize)).build());

				String reason = "by "+event.getMember().getEffectiveName();
				List<CompletableFuture<Void>> completableFutures = new ArrayList<>();
				for (Member member : members) {
					completableFutures.add(guild.removeRoleFromMember(member, role).reason(reason).submit().exceptionally(ex -> null));
				}

				CompletableFuture.allOf(completableFutures.toArray(new CompletableFuture[0]))
					.whenComplete((done, exception) -> {
						if (exception != null) {
							editErrorUnknown(event, exception.getMessage());
						} else {
							int removed = 0;
							for (CompletableFuture<Void> future : completableFutures) {
								if (!future.isCompletedExceptionally()) removed++;
							}
							// Log
							bot.getGuildLogger().role.onRoleRemovedAll(guild, event.getUser(), role);
							// Send reply
							editEmbed(event, builder.setColor(Constants.COLOR_SUCCESS).setDescription(lu.getGuildText(event, path+".done",
								role.getName(), Integer.toString(removed), Integer.toString(maxSize))
							).build());
						}
					}).thenRun(guild::pruneMemberCache); // Prune member cache
			}).onError(failure -> editErrorOther(event, failure.getMessage()));
		}
	}

	private class Modify extends SlashCommand {
		public Modify() {
			this.name = "modify";
			this.path = "bot.roles.role.modify";
			this.options = List.of(
				new OptionData(OptionType.USER, "user", lu.getText(path + ".user.help"), true)
			);
			addMiddlewares(
				"throttle:user,1,20"
			);
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Member target = event.optMember("user");
			if (target == null) {
				editError(event, path+".no_user");
				return;
			}
			if (!event.getMember().canInteract(target) || target.getUser().isBot()) {
				editError(event, path+".incorrect_user");
				return;
			}
			List<Role> userRoles = target.getRoles();
			List<Role> allRoles = event.getGuild().getRoles();

			List<ActionRow> actionRows = new ArrayList<>();
			StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("role:manage-select:1:"+target.getId()).setRequiredRange(0, 25);

			List<SelectOption> roleOptions = new ArrayList<>();
			List<String> defaultValues = new ArrayList<>();
			int nextMenuId = 2;

			final boolean whitelistEnabled = bot.getDBUtil().getGuildSettings(event.getGuild()).isRoleWhitelistEnabled();
			for (Role role : allRoles) {
				String denyReason = bot.getCheckUtil().denyRole(role, event.getGuild(), event.getMember(), true);

				if (denyReason != null) continue;
				// Check if role whitelisted
				if (whitelistEnabled) {
					if (!bot.getDBUtil().roles.existsRole(role.getIdLong())) continue;
				}
				SelectOption option = SelectOption.of(role.getName(), role.getId());

				if (roleOptions.size() >= 25) {
					// Append builder to the list and create new builder
					menuBuilder.addOptions(roleOptions).setDefaultValues(defaultValues);
					actionRows.add(ActionRow.of(menuBuilder.build()));
					// Clear options and default values
					roleOptions.clear();
					defaultValues.clear();
					// Allow maximum 4 rows (fifth for button)
					if (actionRows.size() >= 4) break;
					// Else create new builder
					menuBuilder = StringSelectMenu.create("role:manage-select:"+nextMenuId+":"+target.getId()).setRequiredRange(0, 25);
					nextMenuId++;
				}
				roleOptions.add(option);
				if (userRoles.contains(role)) defaultValues.add(role.getId());
			}
			if (!roleOptions.isEmpty()) {
				menuBuilder.addOptions(roleOptions).setDefaultValues(defaultValues);
				actionRows.add(ActionRow.of(menuBuilder.build()));
			}
			if (actionRows.isEmpty()) {
				editError(event, path+".no_roles");
				return;
			}
			actionRows.add(ActionRow.of(Button.primary("role:manage-confirm:"+target.getId(), lu.getGuildText(event, path+".button"))));

			try {
				bot.getDBUtil().modifyRole.create(event.getGuild().getIdLong(), event.getMember().getIdLong(),
					target.getIdLong(), Instant.now().plus(2, ChronoUnit.MINUTES));
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "start modify role");
				return;
			}

			event.getHook()
				.editOriginalEmbeds(bot.getEmbedUtil().getEmbed()
					.setDescription(lu.getGuildText(event, path+".title", target.getAsMention()))
					.build()
				)
				.setComponents(actionRows)
				.queue();
		}
	}

}

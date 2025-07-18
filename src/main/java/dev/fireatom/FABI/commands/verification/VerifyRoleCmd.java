package dev.fireatom.FABI.commands.verification;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;

@SuppressWarnings("InnerClassMayBeStatic")
public class VerifyRoleCmd extends SlashCommand {
	
	public VerifyRoleCmd() {
		this.name = "verifyrole";
		this.path = "bot.verification.verifyrole";
		this.children = new SlashCommand[]{
			new Verify(),
			new SetAdditional(), new ClearAdditional(), new ViewAdditional()
		};
		this.module = CmdModule.VERIFICATION;
		this.category = CmdCategory.VERIFICATION;
		this.accessLevel = CmdAccessLevel.ADMIN;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Verify extends SlashCommand {
		public Verify() {
			this.name = "verify";
			this.path = "bot.verification.verifyrole.verify";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role", lu.getText(path+".role.help"), true)
			);
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Guild guild = event.getGuild();
			// Check role
			Role role = event.optRole("role");
			if (role == null) {
				editError(event, path+".no_role");
				return;
			}
			String denyReason = bot.getCheckUtil().denyRole(role, event.getGuild(), event.getMember(), true);
			if (denyReason != null) {
				editError(event, path+".incorrect_role", "Role: %s\n> %s".formatted(role.getAsMention(), denyReason));
				return;
			}

			try {
				bot.getDBUtil().verifySettings.setVerifyRole(guild.getIdLong(), role.getIdLong());
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set verify role");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done", role.getAsMention()))
				.build());
		}
	}

	private class SetAdditional extends SlashCommand {
		public SetAdditional() {
			this.name = "set";
			this.path = "bot.verification.verifyrole.additional.set";
			this.options = List.of(
				new OptionData(OptionType.ROLE, "role1", lu.getText(path+".role1.help"), true),
				new OptionData(OptionType.ROLE, "role2", lu.getText(path+".role2.help")),
				new OptionData(OptionType.ROLE, "role3", lu.getText(path+".role3.help"))
			);
			this.subcommandGroup = new SubcommandGroupData("additional", lu.getText("bot.verification.verifyrole.additional.help"));
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
			for (Role r : roles) {
				String denyReason = bot.getCheckUtil().denyRole(r, event.getGuild(), event.getMember(), true);
				if (denyReason != null) {
					editError(event, path+".incorrect_role", "Role: %s\n> %s".formatted(r.getAsMention(), denyReason));
					return;
				}
			}

			String roleIds = roles.stream().map(Role::getId).collect(Collectors.joining(";"));
			try {
				bot.getDBUtil().verifySettings.setAdditionalRoles(guild.getIdLong(), roleIds);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "set additional roles");
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done",
					roles.stream().map(Role::getAsMention).collect(Collectors.joining(" ")))
				).build());
		}
	}

	private class ClearAdditional extends SlashCommand {
		public ClearAdditional() {
			this.name = "clear";
			this.path = "bot.verification.verifyrole.additional.clear";
			this.subcommandGroup = new SubcommandGroupData("additional", lu.getText("bot.verification.verifyrole.additional.help"));
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			try {
				bot.getDBUtil().verifySettings.setAdditionalRoles(event.getGuild().getIdLong(), null);
			} catch (SQLException ex) {
				editErrorDatabase(event, ex, "Failed to update database");
			}
			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build()
			);
		}
	}

	private class ViewAdditional extends SlashCommand {
		public ViewAdditional() {
			this.name = "view";
			this.path = "bot.verification.verifyrole.additional.view";
			this.subcommandGroup = new SubcommandGroupData("additional", lu.getText("bot.verification.verifyrole.additional.help"));
			this.ephemeral = true;
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Set<Long> roleIds = bot.getDBUtil().getVerifySettings(event.getGuild()).getAdditionalRoles();

			if (roleIds.isEmpty()) {
				editMsg(event, lu.getGuildText(event, path+".no_roles"));
			} else {
				StringBuilder stringBuilder = new StringBuilder("**Roles:**");
				for (Long roleId : roleIds) {
					stringBuilder.append("\n> <@&").append(roleId).append(">");
				}
				editMsg(event, stringBuilder.toString());
			}
		}
	}

}

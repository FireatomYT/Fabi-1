package dev.fireatom.FABI.commands.voice;

import java.sql.SQLException;
import java.util.*;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.Emote;
import dev.fireatom.FABI.objects.constants.CmdCategory;
import dev.fireatom.FABI.objects.constants.Constants;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Mentions;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData;
import net.dv8tion.jda.api.managers.channel.concrete.VoiceChannelManager;
import org.jetbrains.annotations.NotNull;

import static dev.fireatom.FABI.listeners.VoiceListener.ownerPerms;

public class VoiceCmd extends SlashCommand {
	
	public VoiceCmd() {
		this.name = "voice";
		this.path = "bot.voice.voice";
		this.children = new SlashCommand[]{
			new Lock(), new Unlock(), new Ghost(), new Unghost(),
			new NameSet(), new NameReset(), new LimitSet(), new LimitReset(),
			new Claim(), new Permit(), new Reject(), new PermsView(), new PermsReset()
		};
		this.category = CmdCategory.VOICE;
		this.module = CmdModule.VOICE;
		this.ephemeral = true;
	}

	@Override
	protected void execute(SlashCommandEvent event) {}

	private class Lock extends SlashCommand {
		public Lock() {
			this.name = "lock";
			this.path = "bot.voice.voice.lock";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Long channelId = bot.getDBUtil().voice.getChannel(event.getMember().getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			// Verify role
			Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
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
				editPermError(event, ex.getPermission(), true);
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build()
			);
		}
	}

	private class Unlock extends SlashCommand {
		public Unlock() {
			this.name = "unlock";
			this.path = "bot.voice.voice.unlock";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Long channelId = bot.getDBUtil().voice.getChannel(event.getMember().getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			// Verify role
			Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
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
				editPermError(event, ex.getPermission(), true);
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build()
			);
		}
	}

	private class Ghost extends SlashCommand {
		public Ghost() {
			this.name = "ghost";
			this.path = "bot.voice.voice.ghost";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Long channelId = bot.getDBUtil().voice.getChannel(event.getMember().getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			// Verify role
			Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
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
				editPermError(event, ex.getPermission(), true);
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build()
			);
		}
	}

	private class Unghost extends SlashCommand {
		public Unghost() {
			this.name = "unghost";
			this.path = "bot.voice.voice.unghost";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Long channelId = bot.getDBUtil().voice.getChannel(event.getMember().getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			// Verify role
			Long verifyRoleId = bot.getDBUtil().getVerifySettings(event.getGuild()).getRoleId();

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
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
				editPermError(event, ex.getPermission(), true);
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build()
			);
		}
	}

	private class NameSet extends SlashCommand {
		public NameSet() {
			this.name = "set";
			this.path = "bot.voice.voice.name.set";
			this.options = List.of(
				new OptionData(OptionType.STRING, "name", lu.getText(path+".name.help"), true)
					.setMaxLength(100)
			);
			this.subcommandGroup = new SubcommandGroupData("name", lu.getText("bot.voice.voice.name.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String name = event.optString("name");
			sendNameReply(event, name);
		}
	}

	private class NameReset extends SlashCommand {
		public NameReset() {
			this.name = "reset";
			this.path = "bot.voice.voice.name.reset";
			this.subcommandGroup = new SubcommandGroupData("name", lu.getText("bot.voice.voice.name.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			String name = Optional
				.ofNullable(bot.getDBUtil().getVoiceSettings(event.getGuild()).getDefaultName())
				.orElse(lu.getGuildText(event, "bot.voice.listener.default_name"));
			sendNameReply(event, name);
		}
	}

	private void sendNameReply(SlashCommandEvent event, String name) {
		long userId = event.getMember().getIdLong();
		Long channelId = bot.getDBUtil().voice.getChannel(userId);
		if (channelId == null) {
			editError(event, "errors.no_channel");
			return;
		}

		name = name.replace("{user}", event.getMember().getEffectiveName());
		event.getGuild().getVoiceChannelById(channelId).getManager().setName(name.substring(0, Math.min(100, name.length()))).queue();

		try {
			bot.getDBUtil().user.setName(userId, name);
		} catch (SQLException ex) {
			editErrorDatabase(event, ex, "set voice name");
			return;
		}

		editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.voice.voice.name.done", name))
			.build()
		);
	}

	private class LimitSet extends SlashCommand {
		public LimitSet() {
			this.name = "set";
			this.path = "bot.voice.voice.limit.set";
			this.options = List.of(
				new OptionData(OptionType.INTEGER, "limit", lu.getText(path+".limit.help"), true)
					.setRequiredRange(0, 99)
			);
			this.subcommandGroup = new SubcommandGroupData("limit", lu.getText("bot.voice.voice.limit.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer limit = event.optInteger("limit");
			sendLimitReply(event, limit);
		}
	}

	private class LimitReset extends SlashCommand {
		public LimitReset() {
			this.name = "reset";
			this.path = "bot.voice.voice.limit.reset";
			this.subcommandGroup = new SubcommandGroupData("limit", lu.getText("bot.voice.voice.limit.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_CHANNEL};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Integer limit = Optional
				.ofNullable(bot.getDBUtil().getVoiceSettings(event.getGuild()).getDefaultLimit())
				.orElse(0);
			sendLimitReply(event, limit);			
		}
	}

	private void sendLimitReply(SlashCommandEvent event, Integer limit) {
		long userId = event.getMember().getIdLong();
		Long channelId = bot.getDBUtil().voice.getChannel(userId);
		if (channelId == null) {
			editError(event, "errors.no_channel");
			return;
		}

		event.getGuild().getVoiceChannelById(channelId).getManager().setUserLimit(limit).queue();

		try {
			bot.getDBUtil().user.setLimit(userId, limit);
		} catch (SQLException ex) {
			editErrorDatabase(event, ex, "set voice limit");
			return;
		}

		editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
			.setDescription(lu.getGuildText(event, "bot.voice.voice.limit.done", limit))
			.build()
		);
	}

	private class Claim extends SlashCommand {
		public Claim() {
			this.name = "claim";
			this.path = "bot.voice.voice.claim";
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS};
		}

		protected void execute(SlashCommandEvent event) {
			Member author = event.getMember();

			if (!author.getVoiceState().inAudioChannel()) {
				editError(event, "bot.voice.listener.not_in_voice");
				return;
			}
			if (bot.getDBUtil().voice.existsUser(author.getIdLong())) {
				editError(event, path+".has_channel");
				return;
			}

			VoiceChannel vc = author.getVoiceState().getChannel().asVoiceChannel();
			Long ownerId = bot.getDBUtil().voice.getUser(vc.getIdLong());
			if (ownerId == null) {
				editError(event, path+".not_custom");
				return;
			}
			
			event.getGuild().retrieveMemberById(ownerId).queue(
				owner -> {
					for (Member vcMember : vc.getMembers()) {
						if (vcMember == owner) {
							editMsg(event, lu.getGuildText(event, path+".has_owner"));
							return;
						}
					}

					try {
						vc.getManager()
							.removePermissionOverride(owner)
							.putPermissionOverride(author, ownerPerms, null)
							.queue();

						bot.getDBUtil().voice.setUser(author.getIdLong(), vc.getIdLong());
					} catch (InsufficientPermissionException ex) {
						editPermError(event, ex.getPermission(), true);
						return;
					} catch (SQLException ex) {
						editErrorDatabase(event, ex, "set new voice owner");
						return;
					}

					editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
						.setDescription(lu.getGuildText(event, path+".done", vc.getAsMention()))
						.build()
					);
				}, failure -> editErrorOther(event, failure.getMessage())
			);
		}
	}

	private class Permit extends SlashCommand {
		private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES);

		public Permit() {
			this.name = "permit";
			this.path = "bot.voice.voice.permit";
			this.options = List.of(
				new OptionData(OptionType.STRING, "mentions", lu.getText(path+".mentions.help"), true)
					.setMaxLength(200)
			);
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Member author = event.getMember();

			Long channelId = bot.getDBUtil().voice.getChannel(author.getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			Mentions mentions = event.optMentions("mentions");
			if (mentions == null) {
				editError(event, path+".invalid_args");
				return;
			}

			List<Member> members = mentions.getMembers();
			List<Role> roles = mentions.getRoles();
			if (members.isEmpty() && roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}
			if (members.contains(author) || members.contains(event.getGuild().getSelfMember())) {
				editError(event, path+".not_self");
				return;
			}

			List<String> mentionStrings = new ArrayList<>();
			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			VoiceChannelManager vcManager = vc.getManager();

			for (Member member : members) {
				vcManager = vcManager.putPermissionOverride(member, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
				mentionStrings.add(member.getEffectiveName());
			}

			for (Role role : roles) {
				EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
				rolePerms.retainAll(adminPerms);
				if (rolePerms.isEmpty()) {
					vcManager = vcManager.putPermissionOverride(role, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL), null);
					mentionStrings.add(role.getName());
				}
			}

			vcManager.queue(done -> {
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getTargetText(event, path+".done", mentionStrings))
					.build()
				);
			}, failure -> editPermError(event, Permission.MANAGE_PERMISSIONS, true));
		}

	}

	private class Reject extends SlashCommand {
		private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_SERVER, Permission.MANAGE_PERMISSIONS, Permission.MANAGE_ROLES);

		public Reject() {
			this.name = "reject";
			this.path = "bot.voice.voice.reject";
			this.options = List.of(
				new OptionData(OptionType.STRING, "mentions", lu.getText(path+".mentions.help"), true)
					.setMaxLength(200)
			);
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Member author = event.getMember();

			Long channelId = bot.getDBUtil().voice.getChannel(author.getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			Mentions mentions = event.optMentions("mentions");
			if (mentions == null) {
				editError(event, path+".invalid_args");
				return;
			}

			List<Member> members = mentions.getMembers();
			List<Role> roles = mentions.getRoles();
			if (members.isEmpty() && roles.isEmpty()) {
				editError(event, path+".invalid_args");
				return;
			}
			if (members.contains(author) || members.contains(event.getGuild().getSelfMember())) {
				editError(event, path+".not_self");
				return;
			}

			List<String> mentionStrings = new ArrayList<>();
			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			VoiceChannelManager vcManager = vc.getManager();

			for (Member member : members) {
				vcManager = vcManager.putPermissionOverride(member, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
				if (vc.getMembers().contains(member)) {
					event.getGuild().kickVoiceMember(member).queue();
				}
				mentionStrings.add(member.getEffectiveName());
			}

			for (Role role : roles) {
				EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
				rolePerms.retainAll(adminPerms);
				if (rolePerms.isEmpty()) {
					vcManager = vcManager.putPermissionOverride(role, null, EnumSet.of(Permission.VOICE_CONNECT, Permission.VIEW_CHANNEL));
					mentionStrings.add(role.getName());
				}
			}

			vcManager.queue(done -> {
				editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
					.setDescription(lu.getTargetText(event, path+".done", mentionStrings))
					.build()
				);
			}, failure -> editPermError(event, Permission.MANAGE_PERMISSIONS, true));
		}
	}

	private class PermsView extends SlashCommand {
		public PermsView() {
			this.name = "view";
			this.path = "bot.voice.voice.perms.view";
			this.subcommandGroup = new SubcommandGroupData("perms", lu.getText("bot.voice.voice.perms.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_PERMISSIONS};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Member author = event.getMember();

			Long channelId = bot.getDBUtil().voice.getChannel(author.getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			Guild guild = event.getGuild();
			VoiceChannel vc = guild.getVoiceChannelById(channelId);

			EmbedBuilder embedBuilder = bot.getEmbedUtil().getEmbed()
				.setTitle(lu.getGuildText(event, path+".embed.title", vc.getAsMention()))
				.setDescription(lu.getGuildText(event, path+".embed.field")+"\n\n");

			//@Everyone
			PermissionOverride publicOverride = vc.getPermissionOverride(guild.getPublicRole());

			String view = contains(publicOverride, Permission.VIEW_CHANNEL);
			String join = contains(publicOverride, Permission.VOICE_CONNECT);
			
			embedBuilder = embedBuilder.appendDescription(formatHolder(lu.getGuildText(event, path+".embed.everyone"), view, join))
				.appendDescription("\n\n" + lu.getGuildText(event, path+".embed.roles") + "\n");

			//Roles
			List<PermissionOverride> overrides = new ArrayList<>(vc.getRolePermissionOverrides()); // cause given override list is immutable
			try {
				overrides.remove(vc.getPermissionOverride(guild.getBotRole())); // removes bot's role
				overrides.remove(vc.getPermissionOverride(guild.getPublicRole())); // removes @everyone role
			} catch (NullPointerException ex) {
				App.getLogger().warn("PermsCmd null pointer at role override remove");
			}
			
			if (overrides.isEmpty()) {
				embedBuilder.appendDescription(lu.getGuildText(event, path+".embed.none") + "\n");
			} else {
				for (PermissionOverride ov : overrides) {
					view = contains(ov, Permission.VIEW_CHANNEL);
					join = contains(ov, Permission.VOICE_CONNECT);

					embedBuilder.appendDescription(formatHolder(ov.getRole().getName(), view, join) + "\n");
				}
			}
			embedBuilder.appendDescription("\n" + lu.getGuildText(event, path+".embed.members") + "\n");

			//Members
			overrides = new ArrayList<>(vc.getMemberPermissionOverrides());
			try {
				overrides.remove(vc.getPermissionOverride(author)); // removes user
				overrides.remove(vc.getPermissionOverride(guild.getSelfMember())); // removes bot
			} catch (NullPointerException ex) {
				App.getLogger().warn("PermsCmd null pointer at member override remove");
			}

			EmbedBuilder embedBuilder2 = embedBuilder;
			List<PermissionOverride> ovs = overrides;

			guild.retrieveMembersByIds(false, overrides.stream().map(PermissionOverride::getId).toArray(String[]::new)).onSuccess(
				members -> {
					if (members.isEmpty()) {
						embedBuilder2.appendDescription(lu.getGuildText(event, path+".embed.none") + "\n");
					} else {
						for (PermissionOverride ov : ovs) {
							String view2 = contains(ov, Permission.VIEW_CHANNEL);
							String join2 = contains(ov, Permission.VOICE_CONNECT);

							String name = members.stream()
								.filter(m -> m.getId().equals(ov.getId()))
								.findFirst()
								.map(Member::getEffectiveName)
								.orElse("Unknown");
							embedBuilder2.appendDescription(formatHolder(name, view2, join2) + "\n");
						}
					}

					editEmbed(event, embedBuilder2.build());
				}
			);
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

		@NotNull
		private String formatHolder(String holder, String view, String join) {
			return "> " + view + " | " + join + " | `" + holder + "`";
		}
	}

	private class PermsReset extends SlashCommand {
		public PermsReset() {
			this.name = "reset";
			this.path = "bot.voice.voice.perms.reset";
			this.subcommandGroup = new SubcommandGroupData("perms", lu.getText("bot.voice.voice.perms.help"));
			this.botPermissions = new Permission[]{Permission.MANAGE_ROLES, Permission.MANAGE_PERMISSIONS, Permission.VIEW_CHANNEL, Permission.VOICE_CONNECT};
		}

		@Override
		protected void execute(SlashCommandEvent event) {
			Member author = event.getMember();
			Long channelId = bot.getDBUtil().voice.getChannel(author.getIdLong());
			if (channelId == null) {
				editError(event, "errors.no_channel");
				return;
			}

			VoiceChannel vc = event.getGuild().getVoiceChannelById(channelId);
			try {
				vc.getManager()
					.sync()
					.putPermissionOverride(author, ownerPerms, null)
					.queue();
			} catch (InsufficientPermissionException ex) {
				editPermError(event, ex.getPermission(), true);
				return;
			}

			editEmbed(event, bot.getEmbedUtil().getEmbed(Constants.COLOR_SUCCESS)
				.setDescription(lu.getGuildText(event, path+".done"))
				.build()
			);
		}
	}

}

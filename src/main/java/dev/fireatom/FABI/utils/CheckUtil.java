package dev.fireatom.FABI.utils;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.objects.constants.Constants;
import dev.fireatom.FABI.utils.exception.CheckException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("UnusedReturnValue")
public class CheckUtil {

	private final App bot;
	private final long ownerId;

	public CheckUtil(App bot, long ownerId) {
		this.bot = bot;
		this.ownerId = ownerId;
	}

	public boolean isDeveloper(UserSnowflake user) {
		return user.getIdLong() == Constants.DEVELOPER_ID;
	}

	public boolean isBotOwner(UserSnowflake user) {
		return user.getIdLong() == ownerId;
	}

	public CmdAccessLevel getAccessLevel(Member member) {
		// Is bot developer
		if (isDeveloper(member) || isBotOwner(member))
			return CmdAccessLevel.DEV;
		
		// Is guild's owner
		if (member.isOwner())
			return CmdAccessLevel.OWNER;

		// Check if is operator
		if (bot.getDBUtil().access.isOperator(member.getGuild().getIdLong(), member.getIdLong()))
			return CmdAccessLevel.OPERATOR;
		
		// Check if user has Administrator privileges
		if (member.hasPermission(Permission.ADMINISTRATOR))
			return CmdAccessLevel.ADMIN;

		// Check for role level
		Map<Long, CmdAccessLevel> roleIds = bot.getDBUtil().access.getAllRoles(member.getGuild().getIdLong());
		if (roleIds.isEmpty()) return CmdAccessLevel.ALL;

		return member.getRoles()
			.stream()
			.filter(role -> roleIds.containsKey(role.getIdLong()))
			.map(role -> roleIds.get(role.getIdLong()))
			.max(CmdAccessLevel::compareTo)
			.orElse(CmdAccessLevel.ALL);
	}

	public boolean isOperatorPlus(Guild guild, UserSnowflake user) {
		// Is bot developer
		if (isDeveloper(user) || isBotOwner(user))
			return true;

		// Is guild's owner
		if (guild.getOwnerIdLong() == user.getIdLong())
			return true;

		// Check if is operator
		return bot.getDBUtil().access.isOperator(guild.getIdLong(), user.getIdLong());
	}

	public boolean hasHigherAccess(Member who, Member than) {
		return getAccessLevel(who).isHigherThan(getAccessLevel(than));
	}

	public boolean hasAccess(Member member, CmdAccessLevel accessLevel) {
		if (accessLevel.equals(CmdAccessLevel.ALL)) return true;
		return getAccessLevel(member).satisfies(accessLevel);
	}

	public CheckUtil moduleEnabled(IReplyCallback replyCallback, Guild guild, CmdModule module) throws CheckException {
		if (module == null)
			return this;
		if (bot.getDBUtil().getGuildSettings(guild).isDisabled(module)) 
			throw new CheckException(bot.getEmbedUtil().getError(replyCallback, "modules.module_disabled"));
		return this;
	}

	public CheckUtil hasPermissions(IReplyCallback replyCallback, Permission[] permissions) throws CheckException {
		return hasPermissions(replyCallback, permissions, null, null, true);
	}

	public CheckUtil hasPermissions(IReplyCallback replyCallback, Permission[] permissions, @NotNull Member member) throws CheckException {
		return hasPermissions(replyCallback, permissions, null, member, false);
	}

	public CheckUtil hasPermissions(IReplyCallback replyCallback, Permission[] permissions, GuildChannel channel) throws CheckException {
		return hasPermissions(replyCallback, permissions, channel, null, true);
	}

	public CheckUtil hasPermissions(IReplyCallback replyCallback, Permission[] permissions, GuildChannel channel, Member member) throws CheckException {
		return hasPermissions(replyCallback, permissions, channel, member, false);
	}

	public CheckUtil hasPermissions(@NotNull IReplyCallback replyCallback, @Nullable Permission[] permissions, @Nullable GuildChannel channel, @Nullable Member member, boolean isSelf) throws CheckException {
		if (permissions == null || permissions.length == 0)
			return this;
		if (!isSelf && member == null)
			throw new IllegalArgumentException("You must specify a member if not self.");

		final Guild guild = replyCallback.getGuild();
		if (guild == null)
			return this;

		MessageCreateData msg = null;
		if (isSelf) {
			Member self = guild.getSelfMember();
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!self.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, perm, true);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!self.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, channel, perm, true);
						break;
					}
				}
			}
		} else {
			if (channel == null) {
				for (Permission perm : permissions) {
					if (!member.hasPermission(perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, perm, false);
						break;
					}
				}
			} else {
				for (Permission perm : permissions) {
					if (!member.hasPermission(channel, perm)) {
						msg = bot.getEmbedUtil().createPermError(replyCallback, channel, perm, false);
						break;
					}
				}
			}
		}
		if (msg != null) {
			throw new CheckException(msg);
		}
		return this;
	}

	private final Set<Permission> adminPerms = Set.of(Permission.ADMINISTRATOR, Permission.MANAGE_CHANNEL, Permission.MANAGE_ROLES, Permission.MANAGE_SERVER, Permission.BAN_MEMBERS);

	@Nullable
	public String denyRole(@NotNull Role role, @NotNull Guild guild, @NotNull Member member, boolean checkPerms) {
		if (role.isPublicRole()) return "`@everyone` is public";
		else if (role.isManaged()) return "Bot's role";
		else if (!member.canInteract(role)) return "You can't interact with this role";
		else if (!guild.getSelfMember().canInteract(role)) return "Bot can't interact with this role";
		else if (checkPerms) {
			EnumSet<Permission> rolePerms = EnumSet.copyOf(role.getPermissions());
			rolePerms.retainAll(adminPerms);
			if (!rolePerms.isEmpty()) return "This role has Administrator/Manager permissions";
		}
		return null;
	}

}

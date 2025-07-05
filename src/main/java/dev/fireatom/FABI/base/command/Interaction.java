/*
 * Copyright 2016-2018 John Grosh (jagrosh) & Kaidan Gustave (TheMonitorLizard)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fireatom.FABI.base.command;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.middleware.Middleware;
import dev.fireatom.FABI.contracts.reflection.Reflectionable;
import dev.fireatom.FABI.middleware.MiddlewareHandler;
import dev.fireatom.FABI.middleware.ThrottleMiddleware;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import dev.fireatom.FABI.objects.CmdModule;
import dev.fireatom.FABI.utils.file.lang.LocaleUtil;

import net.dv8tion.jda.api.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A class that represents an interaction with a user.
 * <p>
 * This is all information used for all forms of interactions.
 * <p>
 * Any content here is safely functionality equivalent regardless of the source of the interaction.
 */
public abstract class Interaction extends Reflectionable {
	public Interaction() {
		super(App.getInstance());
	}

	/**
	 * {@code true} if the command may only be used in a {@link net.dv8tion.jda.api.entities.Guild Guild},
	 * {@code false} if it may be used in both a Guild and a DM.
	 * <br>Default {@code true}.
	 */
	protected boolean guildOnly = true;

	/**
	 * Any {@link Permission Permissions} a Member must have to use this interaction.
	 * <br>These are only checked in a {@link net.dv8tion.jda.api.entities.Guild server} environment.
	 * <br>To disable the command for everyone (for interactions), set this to {@code null}.
	 * <br>Keep in mind, commands may still show up if the channel permissions are updated in settings.
	 * Otherwise, commands will automatically be hidden unless a user has these perms.
	 * However, permissions are always checked, just in case. A user must have these permissions regardless.
	 */
	@NotNull
	protected Permission[] userPermissions = new Permission[0];

	/**
	 * Any {@link Permission Permissions} the bot must have to use a command.
	 * <br>These are only checked in a {@link net.dv8tion.jda.api.entities.Guild server} environment.
	 */
	@NotNull
	protected Permission[] botPermissions = new Permission[0];

	protected String throttle = null;

	/**
	 * Gets the {@link Interaction#userPermissions userPermissions} for the Interaction.
	 *
	 * @return The userPermissions for the Interaction
	 */
	@NotNull
	public Permission[] getUserPermissions() {
		return userPermissions;
	}

	/**
	 * Gets the {@link Interaction#botPermissions botPermissions} for the Interaction.
	 *
	 * @return The botPermissions for the Interaction
	 */
	@NotNull
	public Permission[] getBotPermissions() {
		return botPermissions;
	}
	
	/**
	 * Gets the help text path based on {@link Interaction#path Interaction.path}.
	 *
	 * @return The path for command's help string in locale file.
	 */
	@NotNull
	public String getHelpPath() {
		return path+".help";
	}

	/**
	 * Gets the usage text path based on {@link Interaction#path Interaction.path}.
	 *
	 * @return The path for command's usage description string in locale file.
	 */
	@NotNull
	public String getUsagePath() {
		return path+".usage";
	}

	/**
	 * Path to the command strings. Must be set, otherwise will display Unknown text.
	 */
	@NotNull
	protected String path = "misc.command";
	
	/**
	 * Gets the {@link Interaction#path Interaction.path} for the Command.
	 *
	 * @return The path for command's string in locale file.
	 */
	@NotNull
	public String getPath() {
		return path;
	}

	protected List<String> middlewares = new ArrayList<>();

	public List<String> getMiddlewares() {
		return middlewares;
	}

	protected boolean hasMiddleware(@NotNull Class<? extends Middleware> clazz) {
		String key = MiddlewareHandler.getName(clazz);
		if (key == null) {
			return false;
		}

		for (String middleware : middlewares) {
			if (middleware.toLowerCase().startsWith(key)) {
				return true;
			}
		}
		return false;
	}

	protected void addMiddlewares(String... data) {
		middlewares.addAll(Arrays.asList(data));
	}

	private static final String DEFAULT_GUILD_LIMIT = "throttle:guild,20,15";
	private static final String DEFAULT_USER_LIMIT = "throttle:user,2,5";

	protected void registerThrottleMiddleware() {
		if (!hasMiddleware(ThrottleMiddleware.class)) {
			middlewares.add(DEFAULT_GUILD_LIMIT);
			middlewares.add(DEFAULT_USER_LIMIT);
			return;
		}

		boolean addUser = true;
		boolean addGuild = true;

		List<String> addMiddlewares = new ArrayList<>();
		for (String middlewareName : middlewares) {
			String[] parts = middlewareName.split(":");

			Middleware middleware = MiddlewareHandler.getMiddleware(parts[0]);
			if (!(middleware instanceof ThrottleMiddleware)) {
				continue;
			}

			var type = ThrottleMiddleware.ThrottleType.fromName(parts[1].split(",")[0]);

			switch (type) {
				case USER -> addUser = false;
				case GUILD, CHANNEL -> addGuild = false;
			}

			if (addUser) {
				addMiddlewares.add(DEFAULT_USER_LIMIT);
			}
			if (addGuild) {
				addMiddlewares.add(DEFAULT_GUILD_LIMIT);
			}
		}
		middlewares.addAll(addMiddlewares);
	}

	protected final LocaleUtil lu = bot.getLocaleUtil();

	protected CmdModule module = null;

	protected CmdAccessLevel accessLevel = CmdAccessLevel.ALL;

	public CmdAccessLevel getAccessLevel() {
		return accessLevel;
	}

	public CmdModule getModule() {
		return module;
	}

	/**
	 * {@code true} if the command should reply with deferred ephemeral reply.
	 * {@code false} if it should send normal deferred reply.
	 * <br>Default: {@code false}
	 */
	protected boolean ephemeral = false;

	/**
	 * @return If deferred reply will be ephemeral.
	 */
	public boolean isEphemeralReply() {
		return ephemeral;
	}
	
}

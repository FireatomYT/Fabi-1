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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.concurrent.ScheduledExecutorService;

import dev.fireatom.FABI.base.command.impl.CommandClientImpl;

import dev.fireatom.FABI.blacklist.Blacklist;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;

/**
 * A simple builder used to create a {@link CommandClientImpl CommandClientImpl}.
 *
 * <p>Once built, add the {@link CommandClient CommandClient} as an EventListener to
 * {@link net.dv8tion.jda.api.JDA JDA} and it will automatically handle commands with ease!
 *
 * @author John Grosh (jagrosh)
 */
@SuppressWarnings("unused")
public class CommandClientBuilder {
	private Activity activity = Activity.playing("default");
	private OnlineStatus status = OnlineStatus.ONLINE;
	private Long ownerId;
	private final LinkedList<SlashCommand> slashCommands = new LinkedList<>();
	private final LinkedList<ContextMenu> contextMenus = new LinkedList<>();
	private String forcedGuildId = null;
	private String[] devGuildIds;
	private boolean manualUpsert = false;
	private CommandListener listener;
	private boolean shutdownAutomatically = true;
	private ScheduledExecutorService executor;
	private Blacklist blacklist;

	/**
	 * Builds a {@link CommandClientImpl CommandClientImpl}
	 * with the provided settings.
	 * <br>Once built, only the {@link dev.fireatom.FABI.base.command.CommandListener CommandListener}.
	 *
	 * @return The CommandClient built.
	 */
	public CommandClient build() {
		CommandClient client = new CommandClientImpl(
			ownerId, activity, status,
			new ArrayList<>(slashCommands), new ArrayList<>(contextMenus),
			forcedGuildId, devGuildIds, manualUpsert,
			shutdownAutomatically, executor, blacklist
		);

		if (listener!=null)
			client.setListener(listener);
		return client;
	}

	/**
	 * Sets the owner for the bot.
	 * <br>Make sure to verify that the ID provided is ISnowflake compatible when setting this.
	 * If it is not, this will warn the developer.
	 *
	 * @param  ownerId
	 *         The ID of the owner.
	 *
	 * @return This builder
	 */
	public CommandClientBuilder setOwnerId(long ownerId) {
		this.ownerId = ownerId;
		return this;
	}

	/**
	 * Sets the {@link net.dv8tion.jda.api.entities.Activity Game} to use when the bot is ready.
	 * <br>Can be set to {@code null} for JDA Utilities to not set it.
	 *
	 * @param  activity
	 *         The Game to use when the bot is ready
	 *
	 * @return This builder
	 */
	public CommandClientBuilder setActivity(Activity activity) {
		this.activity = activity;
		return this;
	}

	/**
	 * Sets the {@link net.dv8tion.jda.api.entities.Activity Game} the bot will use as the default:
	 * 'Playing <b>Type /help</b>'
	 *
	 * @return This builder
	 */
	public CommandClientBuilder useDefaultGame() {
		this.activity = Activity.playing("default");
		return this;
	}

	/**
	 * Sets the {@link net.dv8tion.jda.api.OnlineStatus OnlineStatus} the bot will use once Ready
	 * This defaults to ONLINE
	 *
	 * @param  status
	 *         The status to set
	 *
	 * @return This builder
	 */
	public CommandClientBuilder setStatus(OnlineStatus status) {
		this.status = status;
		return this;
	}

	/**
	 * Adds a {@link SlashCommand SlashCommand} and registers it to the
	 * {@link CommandClientImpl CommandClientImpl} for this session.
	 *
	 * @param  command
	 *         The SlashCommand to add
	 *
	 * @return This builder
	 */
	public CommandClientBuilder addSlashCommand(SlashCommand command) {
		slashCommands.add(command);
		return this;
	}

	/**
	 * Adds and registers multiple {@link SlashCommand SlashCommand}s to the
	 * {@link CommandClientImpl CommandClientImpl} for this session.
	 * <br>This is the same as calling {@link CommandClientBuilder#addSlashCommand(SlashCommand) addSlashCommand(SlashCommand)} multiple times.
	 *
	 * @param  commands
	 *         The Commands to add
	 *
	 * @return This builder
	 */
	public CommandClientBuilder addSlashCommands(SlashCommand... commands) {
		for(SlashCommand command: commands)
			this.addSlashCommand(command);
		return this;
	}

	/**
	 * Adds a {@link ContextMenu ContextMenu} and registers it to the
	 * {@link CommandClientImpl CommandClientImpl} for this session.
	 *
	 * @param  contextMenu
	 *         The Context Menu to add
	 *
	 * @return This builder
	 */
	public CommandClientBuilder addContextMenu(ContextMenu contextMenu) {
		contextMenus.add(contextMenu);
		return this;
	}

	/**
	 * Adds and registers multiple {@link ContextMenu ContextMenu}s to the
	 * {@link CommandClientImpl CommandClientImpl} for this session.
	 * <br>This is the same as calling {@link CommandClientBuilder#addContextMenu(ContextMenu) addContextMenu(ContextMenu)} multiple times.
	 *
	 * @param  contextMenus
	 *         The Context Menus to add
	 *
	 * @return This builder
	 */
	public CommandClientBuilder addContextMenus(ContextMenu... contextMenus) {
		for(ContextMenu contextMenu: contextMenus)
			this.addContextMenu(contextMenu);
		return this;
	}

	/**
	 * Forces Guild Only for SlashCommands.
	 * Setting this to null disables the feature, but it is off by default.
	 *
	 * @param guildId the guild ID.
	 * @return This Builder
	 */
	public CommandClientBuilder forceGuildOnly(String guildId) {
		this.forcedGuildId = guildId;
		return this;
	}

	/**
	 * Forces Guild Only for SlashCommands.
	 * Setting this to null disables the feature, but it is off by default.
	 *
	 * @param guildId the guild ID.
	 * @return This Builder
	 */
	public CommandClientBuilder forceGuildOnly(long guildId)
	{
		this.forcedGuildId = String.valueOf(guildId);
		return this;
	}

	/**
	 * Set owner/developer Guild that will have access to owner set SlashCommands.
	 * By default, all SlashCommands/ContextMenus will be added globally (except if used {@link CommandClientBuilder#forceGuildOnly forceGuildOnly})
	 *
	 * @param guildIds the guild IDs.
	 * @return This Builder
	 */
	public CommandClientBuilder setDevGuildIds(String... guildIds) {
		this.devGuildIds = guildIds;
		return this;
	}

	/**
	 * Set owner/developer Guild that will have access to owner set SlashCommands.
	 * By default, all SlashCommands/ContextMenus will be added globally (except if used {@link CommandClientBuilder#forceGuildOnly forceGuildOnly})
	 *
	 * @param guildIds the guild IDs.
	 * @return This Builder
	 */
	public CommandClientBuilder setDevGuildIds(long... guildIds)
	{
		this.devGuildIds = Arrays.stream(guildIds).mapToObj(String::valueOf).toArray(String[]::new);
		return this;
	}

	/**
	 * Whether to manually upsert slash commands.
	 * This is designed if you want to handle upserting, instead of doing it every boot.
	 * False by default.
	 *
	 * @param manualUpsert your option.
	 * @return This Builder
	 */
	public CommandClientBuilder setManualUpsert(boolean manualUpsert) {
		this.manualUpsert = manualUpsert;
		return this;
	}

	/**
	 * Sets the {@link dev.fireatom.FABI.base.command.CommandListener CommandListener} for the
	 * {@link CommandClientImpl CommandClientImpl}.
	 *
	 * @param  listener
	 *         The CommandListener for the CommandClientImpl
	 *
	 * @return This builder
	 */
	public CommandClientBuilder setListener(CommandListener listener) {
		this.listener = listener;
		return this;
	}

	/**
	 * Sets the {@link java.util.concurrent.ScheduledExecutorService ScheduledExecutorService} for the
	 * {@link CommandClientImpl CommandClientImpl}.
	 *
	 * @param  executor
	 *         The ScheduledExecutorService for the CommandClientImpl
	 *
	 * @return This builder
	 */
	public CommandClientBuilder setScheduleExecutor(ScheduledExecutorService executor) {
		this.executor = executor;
		return this;
	}

	/**
	 * Sets the Command Client to shut down internals automatically when a
	 * {@link net.dv8tion.jda.api.events.session.ShutdownEvent ShutdownEvent} is received.
	 *
	 * @param shutdownAutomatically
	 *        {@code false} to disable calling the shutdown method when a ShutdownEvent is received
	 * @return This builder
	 */
	public CommandClientBuilder setShutdownAutomatically(boolean shutdownAutomatically) {
		this.shutdownAutomatically = shutdownAutomatically;
		return this;
	}

	public CommandClientBuilder setBlacklist(Blacklist blacklist) {
		this.blacklist = blacklist;
		return this;
	}
}

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
package dev.fireatom.FABI.base.command.impl;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.fireatom.FABI.base.command.CommandClient;
import dev.fireatom.FABI.base.command.CommandListener;
import dev.fireatom.FABI.base.command.ContextMenu;
import dev.fireatom.FABI.base.command.MessageContextMenu;
import dev.fireatom.FABI.base.command.MessageContextMenuEvent;
import dev.fireatom.FABI.base.command.SlashCommand;
import dev.fireatom.FABI.base.command.SlashCommandEvent;
import dev.fireatom.FABI.base.command.UserContextMenu;
import dev.fireatom.FABI.base.command.UserContextMenuEvent;

import dev.fireatom.FABI.blacklist.Blacklist;
import dev.fireatom.FABI.middleware.MiddlewareStack;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import net.dv8tion.jda.internal.utils.Checks;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of {@link CommandClient CommandClient} to be used by a bot.
 *
 * <p>This is a listener usable with {@link net.dv8tion.jda.api.JDA JDA}, as it implements
 * {@link net.dv8tion.jda.api.hooks.EventListener EventListener} in order to catch and use different kinds of
 * {@link net.dv8tion.jda.api.events.Event Event}s.
 *
 * @author John Grosh (jagrosh)
 */
public class CommandClientImpl implements CommandClient, EventListener {
	private static final Logger LOG = LoggerFactory.getLogger(CommandClientImpl.class);

	private final OffsetDateTime start;
	private final Activity activity;
	private final OnlineStatus status;
	private final long ownerId;
	private final HashMap<String, Integer> slashCommandIndex;
	private final ArrayList<SlashCommand> slashCommands;
	private final ArrayList<ContextMenu> contextMenus;
	private final HashMap<String, Integer> contextMenuIndex;
	private final String forcedGuildId;
	private final String[] devGuildIds;
	private final boolean manualUpsert;
	private final HashMap<String,OffsetDateTime> cooldowns;
	private final boolean shutdownAutomatically;
	private final ExecutorService commandService;
	private final Blacklist blacklist;

	private CommandListener listener = null;

	public CommandClientImpl(
		long ownerId, Activity activity, OnlineStatus status,
		ArrayList<SlashCommand> slashCommands, ArrayList<ContextMenu> contextMenus,
		String forcedGuildId, String[] devGuildIds, boolean manualUpsert,
		boolean shutdownAutomatically, ExecutorService executor, Blacklist blacklist
	) {
		Checks.check(ownerId > 0L, "Provided owner ID is incorrect (<0).");

		this.start = OffsetDateTime.now();

		this.ownerId = ownerId;

		this.activity = activity;
		this.status = status;
		this.slashCommandIndex = new HashMap<>();
		this.slashCommands = new ArrayList<>();
		this.contextMenus = new ArrayList<>();
		this.contextMenuIndex = new HashMap<>();
		this.forcedGuildId = forcedGuildId;
		this.devGuildIds = devGuildIds==null || devGuildIds.length==0 ? null : devGuildIds;
		this.manualUpsert = manualUpsert;
		this.cooldowns = new HashMap<>();
		this.shutdownAutomatically = shutdownAutomatically;
		this.commandService = executor!=null ? executor : Executors.newVirtualThreadPerTaskExecutor();
		this.blacklist = blacklist;

		// Load slash commands
		for (SlashCommand command : slashCommands) {
			addSlashCommand(command);
		}

		// Load context menus
		for (ContextMenu menu : contextMenus) {
			addContextMenu(menu);
		}
	}

	@Override
	public void setListener(CommandListener listener) {
		this.listener = listener;
	}

	@Override
	public CommandListener getListener() {
		return listener;
	}

	@Override
	public List<SlashCommand> getSlashCommands() {
		return slashCommands;
	}

	@Override
	public List<ContextMenu> getContextMenus() {
		return contextMenus;
	}

	@Override
	public boolean isManualUpsert() {
		return manualUpsert;
	}

	@Override
	public String forcedGuildId() {
		return forcedGuildId;
	}

	@Override
	public String[] devGuildIds() {
		return devGuildIds;
	}

	@Override
	public OffsetDateTime getStartTime() {
		return start;
	}

	@Override
	public int getRemainingCooldown(String name) {
		if (cooldowns.containsKey(name)) {
			int time = (int) Math.ceil(OffsetDateTime.now().until(cooldowns.get(name), ChronoUnit.MILLIS) / 1000D);
			if (time<=0) {
				cooldowns.remove(name);
				return 0;
			}
			return time;
		}
		return 0;
	}

	@Override
	public void applyCooldown(String name, int seconds) {
		cooldowns.put(name, OffsetDateTime.now().plusSeconds(seconds));
	}

	@Override
	public void addSlashCommand(SlashCommand command) {
		addSlashCommand(command, slashCommands.size());
	}

	@Override
	public void addSlashCommand(SlashCommand command, int index) {
		if (index>slashCommands.size() || index<0)
			throw new ArrayIndexOutOfBoundsException("Index specified is invalid: ["+index+"/"+slashCommands.size()+"]");
		synchronized (slashCommandIndex) {
			String name = command.getName().toLowerCase(Locale.ROOT);
			//check for collision
			if (slashCommandIndex.containsKey(name))
				throw new IllegalArgumentException("Command added has a name that has already been indexed: \""+name+"\"!");
			//shift if not append
			if (index<slashCommands.size()) {
				slashCommandIndex.entrySet().stream().filter(entry -> entry.getValue()>=index).toList()
					.forEach(entry -> slashCommandIndex.put(entry.getKey(), entry.getValue()+1));
			}
			//add
			slashCommandIndex.put(name, index);
		}
		slashCommands.add(index,command);
	}

	@Override
	public void addContextMenu(ContextMenu menu) {
		addContextMenu(menu, contextMenus.size());
	}

	@Override
	public void addContextMenu(ContextMenu menu, int index) {
		if (index>contextMenus.size() || index<0)
			throw new ArrayIndexOutOfBoundsException("Index specified is invalid: ["+index+"/"+contextMenus.size()+"]");
		synchronized (contextMenuIndex) {
			String name = menu.getName();
			//check for collision
			if (contextMenuIndex.containsKey(name)) {
				// Compare the existing menu's class to the new menu's class
				if (contextMenuIndex.get(name).getClass().getName().equals(menu.getClass().getName())) {
					throw new IllegalArgumentException("Context Menu added has a name and class that has already been indexed: \"" + name + "\"!");
				}
			}
			//shift if not append
			if (index<contextMenuIndex.size()) {
				contextMenuIndex.entrySet().stream().filter(entry -> entry.getValue()>=index).toList()
					.forEach(entry -> contextMenuIndex.put(entry.getKey(), entry.getValue()+1));
			}
			//add
			contextMenuIndex.put(name, index);
		}
		contextMenus.add(index,menu);
	}

	@Override
	public long getOwnerIdLong() {
		return ownerId;
	}

	@Override
	public void shutdown() {
		commandService.shutdown();
	}

	@Override
	public void onEvent(@NotNull GenericEvent event) {
		switch (event) {
			case SlashCommandInteractionEvent slashCommandInteractionEvent ->
				onSlashCommand(slashCommandInteractionEvent);
			case MessageContextInteractionEvent messageContextInteractionEvent ->
				onMessageContextMenu(messageContextInteractionEvent);
			case UserContextInteractionEvent userContextInteractionEvent ->
				onUserContextMenu(userContextInteractionEvent);
			case CommandAutoCompleteInteractionEvent commandAutoCompleteInteractionEvent ->
				onCommandAutoComplete(commandAutoCompleteInteractionEvent);
			case ReadyEvent readyEvent -> onReady(readyEvent);
			case ShutdownEvent ignored -> {
				if (shutdownAutomatically)
					shutdown();
			}
			default -> {}
		}
	}

	private void onReady(ReadyEvent event) {
		if (!event.getJDA().getSelfUser().isBot()) {
			LOG.error("JDA-Utilities does not support CLIENT accounts.");
			event.getJDA().shutdown();
			return;
		}

		if (activity != null)
			event.getJDA().getPresence().setPresence(status==null ? OnlineStatus.ONLINE : status,
				"default".equals(activity.getName()) ? Activity.playing("Type /help") : activity);

		// Upsert slash commands, if not manual
		if (!manualUpsert) {
			upsertInteractions(event.getJDA());
		}
	}

	@Override
	public void upsertInteractions(JDA jda) {
		if (devGuildIds == null) {
			upsertInteractions(jda, forcedGuildId);
		} else {
			upsertInteractions(jda, devGuildIds);
		}
		
	}

	@Override
	public void upsertInteractions(JDA jda, String serverId) {
		// Get all commands
		List<CommandData> data = new ArrayList<>();
		List<SlashCommand> slashCommands = getSlashCommands();
		List<ContextMenu> contextMenus = getContextMenus();

		// Build the command and privilege data
		for (SlashCommand command : slashCommands) {
			data.add(command.buildCommandData());
		}

		for (ContextMenu menu : contextMenus) {
			data.add(menu.buildCommandData());
		}

		// Upsert the commands
		if (serverId != null) {
			// Attempt to retrieve the provided guild
			Guild server = jda.getGuildById(serverId);
			if (server == null) {
				LOG.error("Specified forced guild is null! Slash Commands will NOT be added! Is the bot added?");
				return;
			}
			// Upsert the commands + their privileges
			server.updateCommands().addCommands(data)
				.queue(
					done -> LOG.debug("Successfully added {} slash commands and {} menus to server {}", slashCommands.size(), contextMenus.size(), server.getName()),
					error -> LOG.error("Could not upsert commands! Does the bot have the applications.commands scope?", error)
				);
		}
		else
			jda.updateCommands().addCommands(data)
				.queue(commands -> LOG.debug("Successfully added {} slash commands!", commands.size()));
	}

	@Override
	public void upsertInteractions(JDA jda, String[] devGuildIds) {
		// Get all commands
		List<CommandData> data = new ArrayList<>();
		List<CommandData> dataDev = new ArrayList<>();

		// Build the command and privilege data
		for (SlashCommand command : getSlashCommands()) {
			if (command.getAccessLevel().satisfies(CmdAccessLevel.DEV)) {
				dataDev.add(command.buildCommandData());
			} else {
				data.add(command.buildCommandData());
			}
		}
		for (ContextMenu menu : getContextMenus()) {
			data.add(menu.buildCommandData());
		}

		jda.updateCommands().addCommands(data)
			.queue(commands -> LOG.debug("Successfully added {} slash commands globally!", commands.size()));

		// Upsert the commands
		for (String guildId : devGuildIds) {
			// Attempt to retrieve the provided guild
			if (guildId == null) {
				LOG.error("One of the specified developer guild id is null! Check provided values.");
				return;
			}
			Guild guild = jda.getGuildById(guildId);
			if (guild == null) {
				LOG.error("Specified dev guild is null! Slash Commands will NOT be added! Is the bot added?");
				return;
			}
			// Upsert the commands + their privileges
			guild.updateCommands().addCommands(dataDev)
				.queue(
					done -> LOG.debug("Successfully added {} slash commands to server {}", dataDev.size(), guild.getName()),
					error -> LOG.error("Could not upsert commands! Does the bot have the applications.commands scope?", error)
				);
		}
	}

	private void onSlashCommand(SlashCommandInteractionEvent event) {
		if (blacklist != null && blacklist.isBlacklisted(event)) return;

		// this will be null if it's not a command
		final SlashCommand command = findSlashCommand(event.getFullCommandName());

		// Wrap the event in a SlashCommandEvent
		final SlashCommandEvent commandEvent = new SlashCommandEvent(event, this);

		if (command != null) {
			if (listener != null)
				listener.onSlashCommand(commandEvent, command);
			// Start middleware stack
			invokeMiddlewareStack(new MiddlewareStack(command, commandEvent));
		}
	}

	private void onCommandAutoComplete(CommandAutoCompleteInteractionEvent event) {
		if (blacklist != null && blacklist.isBlacklisted(event)) return;
		// this will be null if it's not a command
		final SlashCommand command = findSlashCommand(event.getFullCommandName());

		if (command != null) {
			command.onAutoComplete(event);
		}
	}

	private SlashCommand findSlashCommand(String path) {
		String[] parts = path.split(" ");

		final SlashCommand command; // this will be null if it's not a command
		synchronized (slashCommandIndex) {
			int i = slashCommandIndex.getOrDefault(parts[0].toLowerCase(Locale.ROOT), -1);
			command = i != -1? slashCommands.get(i) : null;
		}

		if (command == null)
			return null;

		return switch (parts.length) {
			case 1 -> // Slash command with no children
				command;
			case 2 -> {
				for (SlashCommand cmd : command.getChildren())
					if (cmd.isCommandFor(parts[1]))
						yield cmd;
				yield null; // Slash command with children
				// child check
			}
			case 3 -> {
				for (SlashCommand cmd : command.getChildren())
					if (cmd.isCommandFor(parts[2]) && cmd.getSubcommandGroup().getName().equals(parts[1]))
						yield cmd;
				yield null; // Slash command with a group and a child
			}
			default ->
				// How did we get here?
				null;
		};

	}

	private void onUserContextMenu(UserContextInteractionEvent event) {
		if (blacklist != null && blacklist.isBlacklisted(event)) return;

		final UserContextMenu menu; // this will be null if it's not a command
		synchronized (contextMenuIndex) {
			ContextMenu c;
			int i = contextMenuIndex.getOrDefault(event.getName(), -1);
			c = i != -1 ? contextMenus.get(i) : null;

			if (c instanceof UserContextMenu)
				menu = (UserContextMenu) c;
			else
				menu = null;
		}

		final UserContextMenuEvent menuEvent = new UserContextMenuEvent(event.getJDA(), event.getResponseNumber(), event,this);

		if (menu != null) {
			if (listener != null)
				listener.onUserContextMenu(menuEvent, menu);
			// Start middleware stack
			invokeMiddlewareStack(new MiddlewareStack(menu, menuEvent));
		}
	}

	private void onMessageContextMenu(MessageContextInteractionEvent event) {
		if (blacklist != null && blacklist.isBlacklisted(event)) return;

		final MessageContextMenu menu; // this will be null if it's not a command
		synchronized (contextMenuIndex) {
			ContextMenu c;
			// Do not lowercase, as there could be 2 menus with the same name, but different letter cases
			int i = contextMenuIndex.getOrDefault(event.getName(), -1);
			c = i != -1 ? contextMenus.get(i) : null;

			if (c instanceof MessageContextMenu)
				menu = (MessageContextMenu) c;
			else
				menu = null;
		}

		final MessageContextMenuEvent menuEvent = new MessageContextMenuEvent(event.getJDA(), event.getResponseNumber(), event,this);

		if (menu != null) {
			if (listener != null)
				listener.onMessageContextMenu(menuEvent, menu);
			// Start middleware stack
			invokeMiddlewareStack(new MiddlewareStack(menu, menuEvent));
		}
	}

	private void invokeMiddlewareStack(MiddlewareStack stack) {
		commandService.submit(stack::next);
	}

}

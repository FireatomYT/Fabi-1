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

import dev.fireatom.FABI.base.command.impl.CommandClientImpl;
import net.dv8tion.jda.api.JDA;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * A Bot Client interface implemented on objects used to hold bot data.
 *
 * <p>This is implemented in {@link CommandClientImpl CommandClientImpl}
 * alongside implementation of {@link net.dv8tion.jda.api.hooks.EventListener EventListener} to create a
 * compounded "Client Listener" which catches specific kinds of events thrown by JDA and processes them
 * automatically to handle and execute {@link SlashCommand SlashCommand}s.
 *
 * <p>Implementations also serve as a useful platforms, carrying reference info such as the bot's
 * {@linkplain #getOwnerIdLong() Owner ID}.
 *
 * <p>For the CommandClientImpl, once initialized, only the following can be modified:
 * <ul>
 *     <li>{@link SlashCommand SlashCommand}s may be added or removed.</li>
 *     <li>The {@link dev.fireatom.FABI.base.command.CommandListener CommandListener} may be set.</li>
 * </ul>
 *
 * @author John Grosh (jagrosh)
 *
 * @implNote
 *         While typically safe, there are a few ways to misuse the standard implementation of this interface:
 *         the CommandClientImpl.
 *         <br>Because of this the following should <b>ALWAYS</b> be followed to avoid such errors:
 *
 *         <p><b>1)</b> Do not build and add more than one CommandClient to an instance JDA, <b>EVER</b>.
 *
 *         <p><b>2)</b> Always create and add the CommandClientImpl to JDA <b>BEFORE</b> you build it, or there is a
 *                      chance some minor errors will occur, <b>especially</b> if JDA has already fired a {@link
 *                      net.dv8tion.jda.api.events.session.ReadyEvent ReadyEvent}.
 *
 *         <p><b>3)</b> Do not provide anything other than a String representing a long (and furthermore a User ID) as
 *                      an Owner ID or a CoOwner ID.  This will generate errors, but not stop the creation of the
 *                      CommandClientImpl which will cause several errors to occur very quickly after startup (except
 *                      if you provide {@code null} for the Owner ID, that'll just flat out throw an {@link
 *                      java.lang.IllegalArgumentException IllegalArgumentException}).
 */
@SuppressWarnings("unused")
public interface CommandClient {
	/**
	 * Adds a single {@link Interaction Interaction} to this CommandClient's
	 * registered SlashCommand.
	 *
	 * <p>For CommandClient's containing 20 commands or fewer, command calls by users will have the bot iterate
	 * through the entire {@link java.util.ArrayList ArrayList} to find the command called. As expected, this
	 * can get fairly hefty if a bot has a lot of Commands registered to it.
	 *
	 * <p>To prevent delay a CommandClient that has more than 20 Commands registered to it will begin to use
	 * <b>indexed calls</b>.
	 * <br>Indexed calls use a {@link java.util.HashMap HashMap} which links their
	 * {@link SlashCommand#name name} to the index that which they
	 * are located at in the ArrayList they are stored.
	 *
	 * <p>This means that all insertion and removal of SlashCommands must reorganize the index maintained by the HashMap.
	 * <br>For this particular insertion, the SlashCommand provided is inserted at the end of the index, meaning it will
	 * become the "rightmost" Command in the ArrayList.
	 *
	 * @param  command
	 *         The Command to add
	 *
	 * @throws java.lang.IllegalArgumentException
	 *         If the SlashCommand provided has a name or alias that has already been registered
	 */
	void addSlashCommand(SlashCommand command);

	/**
	 * Adds a single {@link SlashCommand SlashCommand} to this CommandClient's
	 * registered Commands at the specified index.
	 *
	 * <p>For CommandClient's containing 20 commands or fewer, command calls by users will have the bot iterate
	 * through the entire {@link java.util.ArrayList ArrayList} to find the command called. As expected, this
	 * can get fairly hefty if a bot has a lot of Commands registered to it.
	 *
	 * <p>To prevent delay a CommandClient that has more than 20 Commands registered to it will begin to use
	 * <b>indexed calls</b>.
	 * <br>Indexed calls use a {@link java.util.HashMap HashMap} which links their
	 * {@link SlashCommand#name name} to the index that which they
	 * are located at in the ArrayList they are stored.
	 *
	 * <p>This means that all insertion and removal of Commands must reorganize the index maintained by the HashMap.
	 * <br>For this particular insertion, the Command provided is inserted at the index specified, meaning it will
	 * become the Command located at that index in the ArrayList. This will shift the Command previously located at
	 * that index as well as any located at greater indices, right one index ({@code size()+1}).
	 *
	 * @param  command
	 *         The Command to add
	 * @param  index
	 *         The index to add the Command at (must follow the specifications {@code 0<=index<=size()})
	 *
	 * @throws java.lang.ArrayIndexOutOfBoundsException
	 *         If {@code index < 0} or {@code index > size()}
	 * @throws java.lang.IllegalArgumentException
	 *         If the Command provided has a name or alias that has already been registered to an index
	 */
	void addSlashCommand(SlashCommand command, int index);

	/**
	 * Adds a single {@link ContextMenu} to this CommandClient's registered Context Menus.
	 *
	 * @param  menu
	 *         The menu to add
	 *
	 * @throws java.lang.IllegalArgumentException
	 *         If the Context Menu provided has a name that has already been registered
	 */
	void addContextMenu(ContextMenu menu);

	/**
	 * Adds a single {@link ContextMenu} to this CommandClient's registered Context Menus.
	 *
	 * @param  menu
	 *         The menu to add
	 * @param  index
	 *         The index to add the Context Menu at (must follow the specifications {@code 0<=index<=size()})
	 *
	 * @throws java.lang.IllegalArgumentException
	 *         If the Context Menu provided has a name that has already been registered
	 */
	void addContextMenu(ContextMenu menu, int index);

	/**
	 * Sets the {@link dev.fireatom.FABI.base.command.CommandListener CommandListener} to catch
	 * command-related events thrown by this {@link CommandClient CommandClient}.
	 *
	 * @param  listener
	 *         The CommandListener
	 */
	void setListener(CommandListener listener);

	/**
	 * Returns the current {@link dev.fireatom.FABI.base.command.CommandListener CommandListener}.
	 *
	 * @return A possibly-null CommandListener
	 */
	CommandListener getListener();

	/**
	 * Returns the list of registered {@link SlashCommand SlashCommand}s
	 * during this session.
	 *
	 * @return A never-null List of Slash Commands registered during this session
	 */
	List<SlashCommand> getSlashCommands();

	/**
	 * Returns the list of registered {@link ContextMenu}s during this session.
	 *
	 * @return A never-null List of Context Menus registered during this session
	 */
	List<ContextMenu> getContextMenus();

	/**
	 * Returns whether manual upsertion is enabled
	 *
	 * @return The manual upsertion status
	 */
	boolean isManualUpsert();

	/**
	 * Returns the forced Guild ID for automatic slash command upserts
	 *
	 * @return A possibly-null forcedGuildId set in the builder
	 */
	String forcedGuildId();

	/**
	 * Returns list of guild IDs, which will have owner commands
	 * 
	 * @return A possibly-empty list devGuildIds in the builder
	 */
	String[] devGuildIds();

	/**
	 * Gets the time this {@link CommandClient CommandClient}
	 * implementation was created.
	 *
	 * @return The start time of this CommandClient implementation
	 */
	OffsetDateTime getStartTime();

	/**
	 * Gets the remaining number of seconds on the specified cooldown.
	 *
	 * @param  name
	 *         The cooldown name
	 *
	 * @return The number of seconds remaining
	 */
	int getRemainingCooldown(String name);

	/**
	 * Applies the specified cooldown with the provided name.
	 *
	 * @param  name
	 *         The cooldown name
	 * @param  seconds
	 *         The time to make the cooldown last
	 */
	void applyCooldown(String name, int seconds);

	/**
	 * Gets the ID of the owner of this bot as a {@code long}.
	 *
	 * @return The {@code long} ID of the owner of the bot
	 */
	long getOwnerIdLong();

	/**
	 * Shuts down internals of the Command Client, such as the threadpool and guild settings manager
	 */
	void shutdown();

	/**
	 * Upserts all interactions to the provided {@link #forcedGuildId() forced server}.
	 * <br>This runs after the {@link net.dv8tion.jda.api.events.session.ReadyEvent ReadyEvent} has been fired
	 * if {@link #isManualUpsert()} is {@code false}.
	 * <br>If {@link #forcedGuildId()} is {@code null}, commands will upsert globally.
	 * <b>This may take up to an hour.</b>
	 *
	 * @param jda The JDA instance to use
	 */
	void upsertInteractions(JDA jda);

	/**
	 * Upserts all interactions to the provided server.
	 * <br>This runs after the {@link net.dv8tion.jda.api.events.session.ReadyEvent ReadyEvent} has been fired
	 * if {@link #isManualUpsert()} is {@code false}.
	 * <br>If {@code null} is passed for the server, commands will upsert globally.
	 * <b>This may take up to an hour.</b>
	 *
	 * @param jda The JDA instance to use
	 * @param serverId The server to upsert interactions for
	 */
	void upsertInteractions(JDA jda, String serverId);

	/**
	 * Upserts all interactions to the provided server.
	 * <br>This runs after the {@link net.dv8tion.jda.api.events.session.ReadyEvent ReadyEvent} has been fired
	 * if {@link #isManualUpsert()} is {@code false}.
	 * <br>If {@code null} is passed for the server, commands will upsert globally.
	 * <b>This may take up to an hour.</b>
	 *
	 * @param jda The JDA instance to use
	 * @param devServerIds Developer/Owner server ids to have owner commands be upserted
	 */
	void upsertInteractions(JDA jda, String[] devServerIds);
}

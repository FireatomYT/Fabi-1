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

import dev.fireatom.FABI.contracts.reflection.Reflectional;
import dev.fireatom.FABI.objects.CmdAccessLevel;

import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Set;

/**
 * <h2><b>User Context Menus In JDA-Chewtils</b></h2>
 *
 * <p>The internal inheritance for User Context Menus used in JDA-Chewtils is that of the object.
 *
 * <p>Classes created inheriting this class gain the unique traits of commands operated using the menu Extension.
 * <br>Using several fields, a menu can define properties that make it unique and complex while maintaining
 * a low level of development.
 * <br>All classes extending this class can define any number of these fields in an object constructor and then
 * create the menu action/response in the abstract {@link UserContextMenu#execute(UserContextMenuEvent)} body:
 *
 * <pre><code> public class ExampleCmd extends UserContextMenu {
 *
 *      public ExampleCmd() {
 *          this.name = "Example";
 *      }
 *
 *      {@literal @Override}
 *      protected void execute(UserContextMenu event) {
 *          event.reply("Hey look! This would be the bot's reply if this was a command!");
 *      }
 *
 * }</code></pre>
 *
 * Execution is with the provision of a UserContextInteractionEvent-CommandClient wrapper called a
 * {@link UserContextMenuEvent} and is performed in two steps:
 * <ul>
 *     <li>{@link UserContextMenu#run(UserContextMenuEvent) run} - The menu runs
 *     through a series of conditionals, automatically terminating the command instance if one is not met,
 *     and possibly providing an error response.</li>
 *
 *     <li>{@link UserContextMenu#execute(UserContextMenuEvent) execute} - The menu,
 *     now being cleared to run, executes and performs whatever lies in the abstract body method.</li>
 * </ul>
 *
 * @author Olivia (Chew)
 */
public abstract class UserContextMenu extends ContextMenu implements Reflectional {
	/**
	 * Runs checks for the {@link UserContextMenu} with the given {@link MessageContextMenuEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The UserContextMenuEvent that triggered this Context Menu
	 */
	public final boolean run(UserContextMenuEvent event) {
		// client 
		final CommandClient client = event.getClient();

		// run
		try {
			execute(event);
		} catch (Throwable t) {
			if (client.getListener() != null) {
				client.getListener().onUserContextMenuException(event, this, t);
				return false;
			}
			// otherwise we rethrow
			throw t;
		}

		if (client.getListener() != null)
			client.getListener().onCompletedUserContextMenu(event, this);

		return true;
	}

	/**
	 * The main body method of a {@link UserContextMenu}.
	 * <br>This is the "response" for a successful
	 * {@link UserContextMenu#run(UserContextMenuEvent)}
	 *
	 * @param  event
	 *         The {@link UserContextMenuEvent} that triggered this menu.
	 */
	protected abstract void execute(UserContextMenuEvent event);

	@Override
	public CommandData buildCommandData() {
		// Set attributes
		this.nameLocalization = lu.getFullLocaleMap(getPath()+".name", lu.getText(getPath()+".name"));

		// Make the command data
		CommandData data = Commands.user(getName());

		// Check name localizations
		if (!getNameLocalization().isEmpty()) {
			//Add localizations
			data.setNameLocalizations(getNameLocalization());
		}

		if (getAccessLevel().isLowerThan(CmdAccessLevel.ADMIN)) {
			data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(getUserPermissions()));
		}
		else {
			data.setDefaultPermissions(DefaultMemberPermissions.DISABLED);
		}

		data.setContexts(this.guildOnly ? Set.of(InteractionContextType.GUILD) : Set.of(InteractionContextType.GUILD, InteractionContextType.BOT_DM));

		// Register middlewares
		registerThrottleMiddleware();
		if (accessLevel.isHigherThan(CmdAccessLevel.ALL)) {
			middlewares.add("hasAccess");
		}
		if (botPermissions.length > 0 || userPermissions.length > 0) {
			this.middlewares.add("permissions");
		}

		return data;
	}
}

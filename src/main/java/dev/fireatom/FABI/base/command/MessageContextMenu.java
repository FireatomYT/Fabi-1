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

public abstract class MessageContextMenu extends ContextMenu implements Reflectional {
	/**
	 * Runs checks for the {@link MessageContextMenu} with the given {@link MessageContextMenuEvent} that called it.
	 * <br>Will terminate, and possibly respond with a failure message, if any checks fail.
	 *
	 * @param  event
	 *         The MessageContextMenuEvent that triggered this menu
	 */
	public final boolean run(MessageContextMenuEvent event) {
		// client 
		final CommandClient client = event.getClient();

		// run
		try {
			execute(event);
		} catch (Throwable t) {
			if (client.getListener() != null) {
				client.getListener().onMessageContextMenuException(event, this, t);
				return false;
			}
			// otherwise we rethrow
			throw t;
		}

		if (client.getListener() != null)
			client.getListener().onCompletedMessageContextMenu(event, this);

		return true;
	}

	/**
	 * The main body method of a {@link MessageContextMenu}.
	 * <br>This is the "response" for a successful
	 * {@link MessageContextMenu#run(MessageContextMenuEvent)}
	 *
	 * @param  event
	 *         The {@link MessageContextMenuEvent} that triggered this menu.
	 */
	protected abstract void execute(MessageContextMenuEvent event);

	@Override
	public CommandData buildCommandData() {
		// Set attributes
		this.nameLocalization = lu.getFullLocaleMap(getPath()+".name", lu.getText(getPath()+".name"));

		// Make the command data
		CommandData data = Commands.message(getName());

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

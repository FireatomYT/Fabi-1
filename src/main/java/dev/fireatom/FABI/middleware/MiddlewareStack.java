package dev.fireatom.FABI.middleware;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.*;
import dev.fireatom.FABI.base.command.Interaction;
import dev.fireatom.FABI.contracts.middleware.Middleware;
import dev.fireatom.FABI.middleware.global.IsModuleEnabled;
import dev.fireatom.FABI.middleware.global.RunCommand;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class MiddlewareStack {

	private static IsModuleEnabled isModuleEnabled;
	private static RunCommand runCommand;

	private final Interaction interaction;
	private final GenericCommandInteractionEvent event;
	private final List<MiddlewareContainer> middlewares = new ArrayList<>();

	private int index = -1;

	public MiddlewareStack(Interaction interaction, GenericCommandInteractionEvent event) {
		this.interaction = interaction;
		this.event = event;

		event.deferReply(interaction.isEphemeralReply()).queue();

		middlewares.add(new MiddlewareContainer(runCommand));

		buildMiddlewareStack();

		middlewares.add(new MiddlewareContainer(isModuleEnabled));
	}

	static void buildGlobalMiddlewares(App bot) {
		isModuleEnabled = new IsModuleEnabled(bot);
		runCommand = new RunCommand(bot);
	}

	private void buildMiddlewareStack() {
		List<String> middleware = interaction.getMiddlewares();
		if (middleware.isEmpty()) {
			return;
		}

		ListIterator<String> middlewareIterator = middleware.listIterator(middleware.size());
		while (middlewareIterator.hasPrevious()) {
			String previous = middlewareIterator.previous();
			String[] split = previous.split(":");

			Middleware middlewareReference = MiddlewareHandler.getMiddleware(split[0]);
			if (middlewareReference == null) {
				continue;
			}

			if (split.length == 1) {
				middlewares.add(new MiddlewareContainer(middlewareReference));
			} else {
				middlewares.add(new MiddlewareContainer(middlewareReference, split[1].split(",")));
			}
		}
	}

	public boolean next() {
		if (index <= -1) {
			index = middlewares.size();
		}

		MiddlewareContainer middlewareContainer = middlewares.get(--index);

		return middlewareContainer.getMiddleware()
			.handle(event, this, middlewareContainer.getArguments());
	}

	public Interaction getInteraction() {
		return interaction;
	}

	public GenericCommandInteractionEvent getEvent() {
		return event;
	}
}

package dev.fireatom.FABI.middleware.global;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.base.command.*;
import dev.fireatom.FABI.base.command.*;
import dev.fireatom.FABI.contracts.middleware.Middleware;
import dev.fireatom.FABI.middleware.MiddlewareStack;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class RunCommand extends Middleware {

	public RunCommand(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		return switch (stack.getInteraction()) {
			case SlashCommand command -> command.run((SlashCommandEvent) event);
			case MessageContextMenu menu -> menu.run((MessageContextMenuEvent) event);
			case UserContextMenu menu -> menu.run((UserContextMenuEvent) event);
			default -> false;
		};
	}

}

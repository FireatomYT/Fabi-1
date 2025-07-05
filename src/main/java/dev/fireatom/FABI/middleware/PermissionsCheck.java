package dev.fireatom.FABI.middleware;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.middleware.Middleware;
import dev.fireatom.FABI.utils.exception.CheckException;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class PermissionsCheck extends Middleware {

	public PermissionsCheck(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (!event.isFromGuild()) {
			return stack.next();
		}

		try {
			bot.getCheckUtil()
				.hasPermissions(event, stack.getInteraction().getBotPermissions())
				.hasPermissions(event, stack.getInteraction().getUserPermissions(), event.getMember());
		} catch (CheckException e) {
			return runErrorCheck(event, () -> {
				sendError(event, e.getEditData());
				return false;
			});
		}

		return stack.next();
	}

}

package dev.fireatom.FABI.middleware;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.middleware.Middleware;
import dev.fireatom.FABI.objects.CmdAccessLevel;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

public class HasAccess extends Middleware {
	public HasAccess(App bot) {
		super(bot);
	}

	@Override
	public boolean handle(@NotNull GenericCommandInteractionEvent event, @NotNull MiddlewareStack stack, String... args) {
		if (!event.isFromGuild()) {
			return stack.next();
		}

		if (stack.getInteraction().getAccessLevel().equals(CmdAccessLevel.ALL)) {
			return stack.next();
		}

		if (bot.getCheckUtil().getAccessLevel(event.getMember()).isLowerThan(stack.getInteraction().getAccessLevel())) {
			return runErrorCheck(event, () -> {
				sendError(event, "errors.interaction.no_access", "Required access: "+stack.getInteraction().getAccessLevel().getName());
				return false;
			});
		}

		return stack.next();
	}
}

package dev.fireatom.FABI.middleware;

import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.middleware.Middleware;
import net.dv8tion.jda.internal.utils.Checks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MiddlewareHandler {

	private static final Map<String, Middleware> middlewares = new HashMap<>();

	@Nullable
	public static Middleware getMiddleware(@NotNull String name) {
		return middlewares.getOrDefault(name.toLowerCase(), null);
	}

	@Nullable
	public static String getName(@NotNull Class<? extends Middleware> clazz) {
		for (var middleware : middlewares.entrySet()) {
			if (middleware.getValue().getClass().getSimpleName().equalsIgnoreCase(clazz.getSimpleName())) {
				return middleware.getKey();
			}
		}
		return null;
	}

	public static void register(@NotNull String name, @NotNull Middleware middleware) {
		Checks.notNull(name, "Middleware name");
		Checks.notNull(middleware, "Middleware");

		if (middlewares.containsKey(name.toLowerCase())) {
			throw new IllegalArgumentException("Middleware already registered: " + name);
		}
		middlewares.put(name.toLowerCase(), middleware);
	}

	public static void initialize(App bot) {
		MiddlewareStack.buildGlobalMiddlewares(bot);
	}

}

package dev.fireatom.FABI.utils;

import ch.qos.logback.classic.Logger;
import dev.fireatom.FABI.App;
import dev.fireatom.FABI.contracts.reflection.Reflectional;
import org.reflections.Reflections;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.function.Consumer;

public class AutoloaderUtil {

	private final static Logger LOG = (Logger) LoggerFactory.getLogger(AutoloaderUtil.class);

	public static void load(String path, Consumer<Reflectional> callback) {
		load(path, callback, true);
	}

	public static void load(String path, Consumer<Reflectional> callback, boolean parseAppInstance) {
		Set<Class<? extends Reflectional>> types = new Reflections(path).getSubTypesOf(Reflectional.class);

		for (Class<? extends Reflectional> reflectionClass : types) {
			// Skip inner/nested classes
			if (reflectionClass.isMemberClass() || reflectionClass.getEnclosingClass() != null) {
				continue;
			}

			// Skip abstract classes
			if (Modifier.isAbstract(reflectionClass.getModifiers())) {
				continue;
			}

			if (reflectionClass.getPackage().getName().contains("contracts")) {
				continue;
			}

			try {
				if (parseAppInstance) {
					//noinspection rawtypes
					Class[] arguments = new Class[1];
					arguments[0] = App.class;

					callback.accept(reflectionClass.getDeclaredConstructor(arguments).newInstance(
						App.getInstance()
					));
				} else {
					callback.accept(reflectionClass.getDeclaredConstructor().newInstance());
				}
			} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
				LOG.error("Failed to create new instance of {}", reflectionClass.getName(), e);
			}
		}
	}

}

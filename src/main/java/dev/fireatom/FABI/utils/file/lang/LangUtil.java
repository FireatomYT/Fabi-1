package dev.fireatom.FABI.utils.file.lang;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Logger;
import com.jayway.jsonpath.JsonPath;
import dev.fireatom.FABI.utils.RandomUtil;
import dev.fireatom.FABI.utils.file.FileManager;

import net.dv8tion.jda.api.interactions.DiscordLocale;
import net.dv8tion.jda.api.interactions.commands.Command;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;

@SuppressWarnings("SwitchStatementWithTooFewBranches")
public final class LangUtil {

	private final Logger log = (Logger) LoggerFactory.getLogger(LangUtil.class);

	private final FileManager fileManager;
	private final Map<String, Object> translations = new HashMap<>();
	public static final EnumSet<DiscordLocale> locales = EnumSet.noneOf(DiscordLocale.class);

	public LangUtil(FileManager fileManager) {
		for (DiscordLocale locale : fileManager.getLanguages()) {
			try {
				File file = fileManager.getFile(locale.getLocale());
				if (file==null) continue;
				translations.put(locale.getLocale(), JsonPath.parse(file).json());
				locales.add(locale);
			} catch (IOException e) {
				log.warn(e.getMessage(), e);
			}
		}
		this.fileManager = fileManager;
	}

	@NotNull
	public String getString(@NotNull DiscordLocale locale, @NotNull String path) {
		return getString(languageSelector(locale), path);
	}

	@Nullable
	public String getNullableString(@NotNull DiscordLocale locale, @NotNull String path) {
		return getNullableString(languageSelector(locale), path);
	}

	@NotNull
	public String getRandomString(@NotNull DiscordLocale locale, @NotNull String path) {
		return (String) RandomUtil.pickRandom(getStringList(languageSelector(locale), path));
	}

	private String languageSelector(@NotNull DiscordLocale locale) {
		return switch (locale) {
			case RUSSIAN -> locale.getLocale();
			default -> "en-GB";
		};
	}

	/**
	 * @param lang - language to be used
	 * @param path - string's json path
	 * @return Returns not-null string. If search returns null string, returns provided path.
	 */
	@NotNull
	public String getString(@NotNull String lang, @NotNull String path) {
		String result = getNullableString(lang, path);
		if (result == null) {
			log.warn("Couldn't find \"{}\" in file {}.json", path, lang);
			return "path_error_invalid";
		}
		return result;
	}

	/**
	 * @param lang - language to be used
	 * @param path - string's json path
	 * @return Returns null-able string.
	 */
	@Nullable
	private String getNullableString(String lang, String path) {
		final String text;

		if (translations.containsKey(lang)) {
			text = JsonPath.using(FileManager.CONF)
				.parse(translations.get(lang))
				.read("$." + path);
			if (text == null || text.isBlank()) return null;
			return text;
		} else {
			return fileManager.getNullableString(lang, path);
		}
	}

	/**
	 * @param lang - language to be used
	 * @param path - string's json path
	 * @return Returns string list, or empty if not found.
	 */
	@NotNull
	public List<String> getStringList(String lang, String path) {
		List<String> result;

		if (translations.containsKey(lang)) {
			result = JsonPath.using(FileManager.CONF)
				.parse(translations.get(lang))
				.read("$." + path);
		} else {
			result = fileManager.getStringList(lang, path);
		}

		if (result == null || result.isEmpty()) {
			log.warn("Couldn't find \"{}\" in file {}.json", path, lang);
			result = List.of("path_error_invalid");
		}
		return result;
	}

	public static List<Command.Choice> getLocaleChoices() {
		return locales.stream()
			.map(l -> new Command.Choice(l.getNativeName(), l.getLocale()))
			.toList();
	}

}

package dev.fireatom.FABI.objects.constants;

import java.io.File;
import java.nio.file.Paths;

@SuppressWarnings("UnnecessaryUnicodeEscape")
public final class Constants {
	private Constants() {
		throw new IllegalStateException("Utility class");
	}

	public static final String SEPAR = File.separator;

	public static final String DATA_PATH = Paths.get("." + SEPAR + "data") + SEPAR;

	public static final String SUCCESS = "\u2611\uFE0F";
	public static final String WARNING = "\u26A0\uFE0F";
	public static final String FAILURE = "\u274C";
	public static final String NONE    = "\u25AA\uFE0F";

	public static final int COLOR_DEFAULT = 0x112E51;
	public static final int COLOR_SUCCESS = 0x266E35;
	public static final int COLOR_FAILURE = 0xB31E22;
	public static final int COLOR_WARNING = 0xFDB81E;

	public static final String DEVELOPER_TAG = "@fireatomyt";
	public static final long DEVELOPER_ID = 755390579252133929L;

	public static final int DEFAULT_CACHE_SIZE = 50;
}

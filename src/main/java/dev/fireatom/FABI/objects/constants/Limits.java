package dev.fireatom.FABI.objects.constants;

public final class Limits {
	private Limits() {
		throw new IllegalStateException("Utility class");
	}

	public static final int GAME_CHANNELS = 10;
	public static final int ACCESS_ROLES = 20;
	public static final int ACCESS_USERS = 10;
	public static final int AUTOPUNISHMENTS = 20;
	public static final int LOG_EXEMPTIONS = 30;
	public static final int PERSISTENT_ROLES = 3;
	public static final int LEVEL_EXEMPTIONS = 30;
	public static final int LEVEL_ROLES = 10;
	public static final int OWNED_GROUPS = 2;
	public static final int JOINED_GROUPS = 3;
	public static final int CUSTOM_ROLES = 20;
	public static final int TICKET_PANELS = 10;
	public static final int WEBHOOKS = 10;

	public static final int REASON_CHARS = 300;
}

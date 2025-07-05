package dev.fireatom.FABI.objects;

public enum ExitCodes {
	NORMAL(0),
	ERROR(1),
	RESTART(10),
	UPDATE(11);

	public final int code;

	ExitCodes(int i) {
		this.code = i;
	}

	public static ExitCodes fromInt(int i) {
		return switch (i) {
			case 1 -> ERROR;
			case 10 -> RESTART;
			case 11 -> UPDATE;
			default -> NORMAL;
		};
	}
}

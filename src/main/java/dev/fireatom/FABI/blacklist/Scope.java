package dev.fireatom.FABI.blacklist;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static dev.fireatom.FABI.utils.CastUtil.getOrDefault;

public enum Scope {
	USER(0, 'U', "User"),
	GUILD(1, 'G', "Guild");

	private final int id;
	private final char prefix;
	private final String name;

	Scope(int id, char prefix, String name) {
		this.id = id;
		this.prefix = prefix;
		this.name = name;
	}

	@Nullable
	public static Scope fromId(int id) {
		for (Scope s : Scope.values()) {
			if (s.id == id) {
				return s;
			}
		}
		return null;
	}

	@Nullable
	public static Scope parse(@NotNull String string) {
		int parsedInt = getOrDefault(string, -1);
		for (Scope s : Scope.values()) {
			if (string.toUpperCase().charAt(0) == s.prefix) {
				return s;
			}
			if (parsedInt == s.id) {
				return s;
			}
		}
		return null;
	}

	public int getId() {
		return id;
	}

	public char getPrefix() {
		return prefix;
	}

	public String getName() {
		return name;
	}
}

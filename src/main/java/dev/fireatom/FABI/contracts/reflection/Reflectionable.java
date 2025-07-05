package dev.fireatom.FABI.contracts.reflection;

import dev.fireatom.FABI.App;

public abstract class Reflectionable implements Reflectional {
	protected final App bot;

	public Reflectionable(final App bot) {
		this.bot = bot;
	}
}

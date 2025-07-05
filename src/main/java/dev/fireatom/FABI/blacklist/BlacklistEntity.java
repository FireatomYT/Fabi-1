package dev.fireatom.FABI.blacklist;

import org.jetbrains.annotations.Nullable;

import java.time.OffsetDateTime;

public class BlacklistEntity {

	private final Scope scope;
	private final long id;
	private final OffsetDateTime expiresIn;
	private final String reason;

	BlacklistEntity(Scope scope, long id, @Nullable String reason, @Nullable OffsetDateTime expiresIn) {
		this.scope = scope;
		this.id = id;
		this.expiresIn = expiresIn;
		this.reason = reason;
	}

	public BlacklistEntity(Scope scope, long id, @Nullable String reason) {
		this(scope, id, reason, null);
	}

	public Scope getScope() {
		return scope;
	}

	public long getId() {
		return id;
	}

	public boolean isBlacklisted() {
		return expiresIn == null || OffsetDateTime.now().isBefore(expiresIn);
	}

	@Nullable
	public String getReason() {
		return reason;
	}

}

package dev.fireatom.FABI.contracts.blacklist;

import java.time.OffsetDateTime;

public interface PunishmentLevel {
	OffsetDateTime generateTime();
}

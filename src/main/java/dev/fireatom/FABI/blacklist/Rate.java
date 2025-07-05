package dev.fireatom.FABI.blacklist;

import org.jetbrains.annotations.Nullable;

public class Rate {

	private final long userId;

	private int index;

	private final Long[] timestamps;

	public Rate(long userId) {
		this.userId = userId;
		this.index = 0;
		this.timestamps = new Long[Ratelimit.hitLimit];
	}

	void hit() {
		timestamps[index++] = System.currentTimeMillis();
		if (index >= Ratelimit.hitLimit) {
			index = 0;
		}
	}

	int getHits() {
		int hits = 0;
		for (Long time : timestamps) {
			if (time != null && (time + Ratelimit.hitTime) > System.currentTimeMillis()) {
				hits++;
			}
		}
		return hits;
	}

	@Nullable
	Long getLast() {
		int i = index - 1;
		if (i < 0) {
			i = Ratelimit.hitLimit - 1;
		}
		return timestamps[i];
	}

	@Override
	public int hashCode() {
		return Long.hashCode(userId);
	}

}

package com.lab.orders.outbox;

import com.lab.orders.config.LabProperties;

/**
 * TEST / DESIGN CONFIGURATION backoff: base * 2^(attempt-1), capped.
 */
public final class OutboxBackoff {

	private OutboxBackoff() {
	}

	public static long delayMs(LabProperties.Outbox properties, int attemptCount) {
		int exp = Math.max(0, attemptCount - 1);
		long raw = properties.getBackoffBaseMs() * (1L << Math.min(exp, 20));
		return Math.min(raw, properties.getBackoffCapMs());
	}

	public static long consumerDelayMs(LabProperties.Consumer properties, int attemptCount) {
		int exp = Math.max(0, attemptCount - 1);
		long raw = properties.getBackoffBaseMs() * (1L << Math.min(exp, 20));
		return Math.min(raw, properties.getBackoffCapMs());
	}
}

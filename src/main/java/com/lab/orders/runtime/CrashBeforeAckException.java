package com.lab.orders.runtime;

/**
 * Lab hook: local consumer TX already committed; process dies before broker ACK.
 * The broker will redeliver. Inbox must make the replay a no-op.
 */
public class CrashBeforeAckException extends RuntimeException {

	public CrashBeforeAckException(String message) {
		super(message);
	}
}

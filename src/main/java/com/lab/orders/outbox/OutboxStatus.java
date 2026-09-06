package com.lab.orders.outbox;

public enum OutboxStatus {
	PENDING,
	IN_FLIGHT,
	PUBLISHED,
	FAILED
}

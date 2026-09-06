package com.lab.orders.messaging;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.lab.orders.runtime.PermanentProcessingException;

public final class EventValidator {

	private static final Set<String> KNOWN_TYPES = Set.of(
			EventTypes.ORDER_CREATED,
			EventTypes.INVENTORY_RESERVED,
			EventTypes.INVENTORY_REJECTED,
			EventTypes.PAYMENT_COMPLETED,
			EventTypes.PAYMENT_FAILED,
			EventTypes.SHIPMENT_CREATED);

	private EventValidator() {
	}

	public static void requireWellFormed(EventEnvelope event) {
		if (event == null) {
			throw new PermanentProcessingException("event is null");
		}
		if (event.eventId() == null) {
			throw new PermanentProcessingException("missing event_id");
		}
		if (event.eventType() == null || event.eventType().isBlank()) {
			throw new PermanentProcessingException("missing event_type");
		}
		if (!KNOWN_TYPES.contains(event.eventType())) {
			throw new PermanentProcessingException("unknown event_type " + event.eventType());
		}
		if (event.eventVersion() != EventTypes.CURRENT_VERSION) {
			throw new PermanentProcessingException("unsupported event_version " + event.eventVersion());
		}
		if (event.aggregateType() == null || event.aggregateId() == null) {
			throw new PermanentProcessingException("missing aggregate identity");
		}
		if (event.correlationId() == null || event.correlationId().isBlank()) {
			throw new PermanentProcessingException("missing correlation_id");
		}
		if (event.occurredAt() == null) {
			throw new PermanentProcessingException("missing occurred_at");
		}
		Map<String, Object> payload = event.payload();
		if (payload == null) {
			throw new PermanentProcessingException("missing payload");
		}
		require(payload, "order_id");
	}

	public static void require(Map<String, Object> payload, String field) {
		if (payload == null || payload.get(field) == null || payload.get(field).toString().isBlank()) {
			throw new PermanentProcessingException("missing payload." + field);
		}
	}

	public static UUID uuid(Map<String, Object> payload, String field) {
		require(payload, field);
		try {
			return UUID.fromString(payload.get(field).toString());
		} catch (IllegalArgumentException e) {
			throw new PermanentProcessingException("invalid payload." + field);
		}
	}

	public static String text(Map<String, Object> payload, String field) {
		require(payload, field);
		return payload.get(field).toString();
	}

	public static int integer(Map<String, Object> payload, String field) {
		require(payload, field);
		try {
			return Integer.parseInt(payload.get(field).toString());
		} catch (NumberFormatException e) {
			throw new PermanentProcessingException("invalid payload." + field);
		}
	}
}

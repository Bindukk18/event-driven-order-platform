package com.lab.orders.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stable event envelope. Not a JPA entity. Payload is a compact map,
 * never a serialized persistence object.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
		@JsonProperty("event_id") UUID eventId,
		@JsonProperty("event_type") String eventType,
		@JsonProperty("event_version") int eventVersion,
		@JsonProperty("aggregate_type") String aggregateType,
		@JsonProperty("aggregate_id") UUID aggregateId,
		@JsonProperty("aggregate_version") int aggregateVersion,
		@JsonProperty("correlation_id") String correlationId,
		@JsonProperty("causation_id") UUID causationId,
		@JsonProperty("occurred_at") Instant occurredAt,
		@JsonProperty("payload") Map<String, Object> payload) {
}

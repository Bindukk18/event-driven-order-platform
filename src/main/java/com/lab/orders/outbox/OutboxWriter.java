package com.lab.orders.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.orders.messaging.EventEnvelope;

@Component
public class OutboxWriter {

	private final OutboxRepository outboxRepository;
	private final ObjectMapper objectMapper;

	public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
		this.outboxRepository = outboxRepository;
		this.objectMapper = objectMapper;
	}

	public OutboxEvent append(String topic, EventEnvelope event, Instant now) {
		OutboxEvent row = new OutboxEvent(
				event.eventId(),
				topic,
				event.aggregateType(),
				event.aggregateId(),
				event.aggregateVersion(),
				event.eventType(),
				event.eventVersion(),
				event.correlationId(),
				event.causationId(),
				writeJson(event),
				OutboxStatus.PENDING,
				0,
				now,
				now);
		return outboxRepository.save(row);
	}

	private String writeJson(EventEnvelope event) {
		try {
			return objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("failed to serialize event envelope", e);
		}
	}
}

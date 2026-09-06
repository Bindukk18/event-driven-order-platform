package com.lab.orders.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lab.orders.config.LabProperties;

@Service
public class OutboxCompletionService {

	private final OutboxRepository outboxRepository;
	private final LabProperties properties;
	private final Clock clock;

	public OutboxCompletionService(OutboxRepository outboxRepository, LabProperties properties, Clock clock) {
		this.outboxRepository = outboxRepository;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPublished(UUID eventId) {
		OutboxEvent event = outboxRepository.findById(eventId).orElseThrow();
		event.markPublished(clock.instant());
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markRetryOrFailed(UUID eventId, String error) {
		OutboxEvent event = outboxRepository.findById(eventId).orElseThrow();
		if (event.getAttemptCount() >= properties.getOutbox().getMaxAttempts()) {
			event.markFailed(safeError(error));
			return;
		}
		long delayMs = OutboxBackoff.delayMs(properties.getOutbox(), event.getAttemptCount());
		event.markRetry(clock.instant().plusMillis(delayMs), safeError(error));
	}

	private static String safeError(String error) {
		if (error == null) {
			return "publish_failed";
		}
		String trimmed = error.replaceAll("(?i)(password|secret|token)=\\S+", "$1=redacted");
		return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
	}
}

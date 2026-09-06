package com.lab.orders.outbox;

import java.util.List;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.orders.messaging.EventEnvelope;
import com.lab.orders.messaging.LabBroker;
import com.lab.orders.messaging.PublishFailedException;

/**
 * Claim TX commits first. broker.publish is outside any DB transaction.
 * Complete TX is separate. This is the durable handoff — never DB commit
 * then broker.publish in the producer path.
 */
@Component
public class OutboxRelay {

	private final OutboxClaimService claimService;
	private final OutboxCompletionService completionService;
	private final LabBroker broker;
	private final ObjectMapper objectMapper;

	public OutboxRelay(
			OutboxClaimService claimService,
			OutboxCompletionService completionService,
			LabBroker broker,
			ObjectMapper objectMapper) {
		this.claimService = claimService;
		this.completionService = completionService;
		this.broker = broker;
		this.objectMapper = objectMapper;
	}

	public int tick() {
		List<OutboxEvent> claimed = claimService.claimBatch();
		int published = 0;
		for (OutboxEvent row : claimed) {
			try {
				EventEnvelope event = objectMapper.readValue(row.getPayload(), EventEnvelope.class);
				broker.publish(row.getTopic(), event);
				completionService.markPublished(row.getEventId());
				published++;
			} catch (JsonProcessingException e) {
				completionService.markRetryOrFailed(row.getEventId(), "malformed_payload");
			} catch (PublishFailedException e) {
				completionService.markRetryOrFailed(row.getEventId(), e.getClass().getSimpleName());
			}
		}
		return published;
	}
}

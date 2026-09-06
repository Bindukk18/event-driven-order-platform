package com.lab.orders.shipping;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lab.orders.inbox.ConsumerInboxRepository;
import com.lab.orders.messaging.EventEnvelope;
import com.lab.orders.messaging.EventTypes;
import com.lab.orders.messaging.EventValidator;
import com.lab.orders.messaging.Topics;
import com.lab.orders.metrics.LabMetrics;
import com.lab.orders.outbox.OutboxWriter;

@Service
public class ShippingConsumer {

	public static final String NAME = "shipping-consumer";

	private final ConsumerInboxRepository inboxRepository;
	private final ShipmentRepository shipmentRepository;
	private final OutboxWriter outboxWriter;
	private final LabMetrics metrics;

	public ShippingConsumer(
			ConsumerInboxRepository inboxRepository,
			ShipmentRepository shipmentRepository,
			OutboxWriter outboxWriter,
			LabMetrics metrics) {
		this.inboxRepository = inboxRepository;
		this.shipmentRepository = shipmentRepository;
		this.outboxWriter = outboxWriter;
		this.metrics = metrics;
	}

	@Transactional
	public void consume(EventEnvelope event) {
		EventValidator.requireWellFormed(event);
		if (!EventTypes.PAYMENT_COMPLETED.equals(event.eventType())) {
			return;
		}

		Instant now = Instant.now();
		int inserted = inboxRepository.insertIgnore(NAME, event.eventId(), Timestamp.from(now));
		if (inserted == 0) {
			metrics.consumerDuplicateTotal.incrementAndGet();
			return;
		}

		UUID orderId = EventValidator.uuid(event.payload(), "order_id");
		UUID shipmentId = UUID.randomUUID();
		shipmentRepository.save(new Shipment(shipmentId, orderId, "CREATED", now));

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("order_id", orderId.toString());
		payload.put("shipment_id", shipmentId.toString());

		EventEnvelope outgoing = new EventEnvelope(
				UUID.randomUUID(),
				EventTypes.SHIPMENT_CREATED,
				EventTypes.CURRENT_VERSION,
				"Order",
				orderId,
				4,
				event.correlationId(),
				event.eventId(),
				now,
				payload);
		outboxWriter.append(Topics.SHIPPING, outgoing, now);
		metrics.recordProcessingLatency(event.occurredAt(), now);
	}
}

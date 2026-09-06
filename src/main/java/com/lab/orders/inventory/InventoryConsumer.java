package com.lab.orders.inventory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lab.orders.inbox.ConsumerInboxRepository;
import com.lab.orders.messaging.EventEnvelope;
import com.lab.orders.messaging.EventTypes;
import com.lab.orders.messaging.EventValidator;
import com.lab.orders.messaging.Topics;
import com.lab.orders.metrics.LabMetrics;
import com.lab.orders.outbox.OutboxWriter;
import com.lab.orders.runtime.RetryableProcessingException;

@Service
public class InventoryConsumer {

	public static final String NAME = "inventory-consumer";
	public static final String UNAVAILABLE_SKU = "UNAVAILABLE";

	private final ConsumerInboxRepository inboxRepository;
	private final InventoryReservationRepository reservationRepository;
	private final OutboxWriter outboxWriter;
	private final LabMetrics metrics;
	private final AtomicInteger failNext = new AtomicInteger(0);
	private volatile String failSku;

	public InventoryConsumer(
			ConsumerInboxRepository inboxRepository,
			InventoryReservationRepository reservationRepository,
			OutboxWriter outboxWriter,
			LabMetrics metrics) {
		this.inboxRepository = inboxRepository;
		this.reservationRepository = reservationRepository;
		this.outboxWriter = outboxWriter;
		this.metrics = metrics;
	}

	public void failNext(int n) {
		this.failSku = null;
		failNext.set(n);
	}

	public void failSku(String sku, int n) {
		this.failSku = sku;
		failNext.set(n);
	}

	@Transactional
	public void consume(EventEnvelope event) {
		EventValidator.requireWellFormed(event);
		if (!EventTypes.ORDER_CREATED.equals(event.eventType())) {
			return;
		}
		if (failNext.get() > 0 && (failSku == null || failSku.equals(event.payload().get("sku")))) {
			failNext.decrementAndGet();
			throw new RetryableProcessingException("temporary inventory dependency unavailable");
		}

		Instant now = Instant.now();
		int inserted = inboxRepository.insertIgnore(NAME, event.eventId(), Timestamp.from(now));
		if (inserted == 0) {
			metrics.consumerDuplicateTotal.incrementAndGet();
			return;
		}

		UUID orderId = EventValidator.uuid(event.payload(), "order_id");
		String sku = EventValidator.text(event.payload(), "sku");
		int quantity = EventValidator.integer(event.payload(), "quantity");
		boolean available = !UNAVAILABLE_SKU.equals(sku);
		ReservationStatus status = available ? ReservationStatus.RESERVED : ReservationStatus.REJECTED;
		UUID reservationId = UUID.randomUUID();
		reservationRepository.save(new InventoryReservation(reservationId, orderId, sku, quantity, status, now));

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("order_id", orderId.toString());
		payload.put("reservation_id", reservationId.toString());
		payload.put("sku", sku);
		payload.put("quantity", quantity);
		payload.put("reason", available ? "reserved" : "sku_unavailable");

		String type = available ? EventTypes.INVENTORY_RESERVED : EventTypes.INVENTORY_REJECTED;
		EventEnvelope outgoing = new EventEnvelope(
				UUID.randomUUID(),
				type,
				EventTypes.CURRENT_VERSION,
				"Order",
				orderId,
				2,
				event.correlationId(),
				event.eventId(),
				now,
				payload);
		outboxWriter.append(Topics.INVENTORY, outgoing, now);
		metrics.recordProcessingLatency(event.occurredAt(), now);
	}
}

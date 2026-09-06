package com.lab.orders.order;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lab.orders.inbox.ConsumerInboxRepository;
import com.lab.orders.messaging.EventEnvelope;
import com.lab.orders.messaging.EventTypes;
import com.lab.orders.messaging.EventValidator;
import com.lab.orders.metrics.LabMetrics;
import com.lab.orders.runtime.RetryableProcessingException;

/**
 * Applies downstream events to the Order aggregate using per-aggregate
 * versions. Gaps are deferred (retryable). Stale versions are ignored.
 */
@Service
public class OrderWorkflowConsumer {

	public static final String NAME = "order-workflow-consumer";

	private final ConsumerInboxRepository inboxRepository;
	private final OrderRepository orderRepository;
	private final LabMetrics metrics;

	public OrderWorkflowConsumer(
			ConsumerInboxRepository inboxRepository,
			OrderRepository orderRepository,
			LabMetrics metrics) {
		this.inboxRepository = inboxRepository;
		this.orderRepository = orderRepository;
		this.metrics = metrics;
	}

	@Transactional
	public void consume(EventEnvelope event) {
		EventValidator.requireWellFormed(event);
		if (EventTypes.ORDER_CREATED.equals(event.eventType())) {
			return;
		}

		UUID orderId = EventValidator.uuid(event.payload(), "order_id");
		Order order = orderRepository.findById(orderId).orElseThrow();
		int incoming = event.aggregateVersion();
		int current = order.getLastAppliedVersion();

		if (incoming <= current) {
			int inserted = inboxRepository.insertIgnore(NAME, event.eventId(), Timestamp.from(Instant.now()));
			if (inserted == 0) {
				metrics.consumerDuplicateTotal.incrementAndGet();
			}
			return;
		}
		if (incoming > current + 1) {
			metrics.orderingGapTotal.incrementAndGet();
			throw new RetryableProcessingException(
					"ordering_gap current=" + current + " incoming=" + incoming);
		}

		Instant now = Instant.now();
		int inserted = inboxRepository.insertIgnore(NAME, event.eventId(), Timestamp.from(now));
		if (inserted == 0) {
			metrics.consumerDuplicateTotal.incrementAndGet();
			return;
		}

		OrderStatus next = statusFor(event.eventType());
		order.apply(next, incoming, now);
		orderRepository.save(order);
		if (next == OrderStatus.SHIPPED) {
			metrics.recordEndToEndLag(order.getCreatedAt(), now);
		}
		metrics.recordProcessingLatency(event.occurredAt(), now);
	}

	private static OrderStatus statusFor(String eventType) {
		if (EventTypes.INVENTORY_RESERVED.equals(eventType)) {
			return OrderStatus.AWAITING_PAYMENT;
		}
		if (EventTypes.INVENTORY_REJECTED.equals(eventType)) {
			return OrderStatus.INVENTORY_REJECTED;
		}
		if (EventTypes.PAYMENT_COMPLETED.equals(eventType)) {
			return OrderStatus.READY_TO_SHIP;
		}
		if (EventTypes.PAYMENT_FAILED.equals(eventType)) {
			return OrderStatus.PAYMENT_FAILED;
		}
		if (EventTypes.SHIPMENT_CREATED.equals(eventType)) {
			return OrderStatus.SHIPPED;
		}
		throw new IllegalStateException("unexpected " + eventType);
	}
}

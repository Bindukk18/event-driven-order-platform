package com.lab.orders.payment;

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
import com.lab.orders.order.Order;
import com.lab.orders.order.OrderRepository;
import com.lab.orders.outbox.OutboxWriter;
import com.lab.orders.runtime.RetryableProcessingException;

@Service
public class PaymentConsumer {

	public static final String NAME = "payment-consumer";

	private final ConsumerInboxRepository inboxRepository;
	private final PaymentAttemptRepository attemptRepository;
	private final OrderRepository orderRepository;
	private final OutboxWriter outboxWriter;
	private final FakePaymentProvider provider;
	private final LabMetrics metrics;

	public PaymentConsumer(
			ConsumerInboxRepository inboxRepository,
			PaymentAttemptRepository attemptRepository,
			OrderRepository orderRepository,
			OutboxWriter outboxWriter,
			FakePaymentProvider provider,
			LabMetrics metrics) {
		this.inboxRepository = inboxRepository;
		this.attemptRepository = attemptRepository;
		this.orderRepository = orderRepository;
		this.outboxWriter = outboxWriter;
		this.provider = provider;
		this.metrics = metrics;
	}

	@Transactional
	public void consume(EventEnvelope event) {
		EventValidator.requireWellFormed(event);
		if (!EventTypes.INVENTORY_RESERVED.equals(event.eventType())) {
			return;
		}

		UUID orderId = EventValidator.uuid(event.payload(), "order_id");
		Order order = orderRepository.findById(orderId).orElseThrow();
		// Same event_id is the provider idempotency key. UNKNOWN is not a
		// signal to invent a new key or charge again.
		ProviderResult result = provider.authorize(event.eventId(), orderId, order.getAmount(), order.getSku());
		if (result == ProviderResult.TRANSIENT) {
			throw new RetryableProcessingException("provider_5xx");
		}

		Instant now = Instant.now();
		int inserted = inboxRepository.insertIgnore(NAME, event.eventId(), Timestamp.from(now));
		if (inserted == 0) {
			metrics.consumerDuplicateTotal.incrementAndGet();
			return;
		}

		PaymentAttemptStatus status = switch (result) {
			case SUCCESS -> PaymentAttemptStatus.COMPLETED;
			case FAILED -> PaymentAttemptStatus.FAILED;
			case UNKNOWN -> PaymentAttemptStatus.UNKNOWN;
			case TRANSIENT -> throw new IllegalStateException("transient already thrown");
		};
		String error = result == ProviderResult.UNKNOWN ? "provider_timeout" : null;
		attemptRepository.save(new PaymentAttempt(UUID.randomUUID(), orderId, event.eventId(), status, error, now));

		if (status == PaymentAttemptStatus.UNKNOWN) {
			// Provider-side effect may already exist. Do not emit
			// PaymentCompleted or PaymentFailed. Do not start a second charge.
			metrics.unknownPaymentCount.incrementAndGet();
			metrics.recordProcessingLatency(event.occurredAt(), now);
			return;
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("order_id", orderId.toString());
		payload.put("amount", order.getAmount().toPlainString());
		payload.put("sku", order.getSku());

		String type = status == PaymentAttemptStatus.COMPLETED
				? EventTypes.PAYMENT_COMPLETED
				: EventTypes.PAYMENT_FAILED;
		EventEnvelope outgoing = new EventEnvelope(
				UUID.randomUUID(),
				type,
				EventTypes.CURRENT_VERSION,
				"Order",
				orderId,
				3,
				event.correlationId(),
				event.eventId(),
				now,
				payload);
		outboxWriter.append(Topics.PAYMENTS, outgoing, now);
		metrics.recordProcessingLatency(event.occurredAt(), now);
	}
}

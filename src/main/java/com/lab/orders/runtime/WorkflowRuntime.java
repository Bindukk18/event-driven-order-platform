package com.lab.orders.runtime;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.orders.config.LabProperties;
import com.lab.orders.dlq.DeadLetter;
import com.lab.orders.dlq.DeadLetterRepository;
import com.lab.orders.inventory.InventoryConsumer;
import com.lab.orders.messaging.BrokerDelivery;
import com.lab.orders.messaging.LabBroker;
import com.lab.orders.messaging.Topics;
import com.lab.orders.metrics.LabMetrics;
import com.lab.orders.order.OrderWorkflowConsumer;
import com.lab.orders.outbox.OutboxBackoff;
import com.lab.orders.outbox.OutboxRelay;
import com.lab.orders.payment.PaymentConsumer;
import com.lab.orders.shipping.ShippingConsumer;

/**
 * Pumps outbox → LAB / TEST BROKER → consumers. One broken delivery is
 * retried or dead-lettered without blocking other orders.
 */
@Component
public class WorkflowRuntime {

	private final OutboxRelay relay;
	private final LabBroker broker;
	private final LabProperties properties;
	private final Clock clock;
	private final LabMetrics metrics;
	private final DeadLetterRepository deadLetterRepository;
	private final ObjectMapper objectMapper;
	private final Map<String, Consumer<com.lab.orders.messaging.EventEnvelope>> handlers = new LinkedHashMap<>();
	private volatile String crashBeforeAckConsumer;

	public WorkflowRuntime(
			OutboxRelay relay,
			LabBroker broker,
			LabProperties properties,
			Clock clock,
			LabMetrics metrics,
			DeadLetterRepository deadLetterRepository,
			ObjectMapper objectMapper,
			InventoryConsumer inventoryConsumer,
			PaymentConsumer paymentConsumer,
			ShippingConsumer shippingConsumer,
			OrderWorkflowConsumer orderWorkflowConsumer) {
		this.relay = relay;
		this.broker = broker;
		this.properties = properties;
		this.clock = clock;
		this.metrics = metrics;
		this.deadLetterRepository = deadLetterRepository;
		this.objectMapper = objectMapper;
		broker.subscribe(Topics.ORDERS, InventoryConsumer.NAME);
		broker.subscribe(Topics.INVENTORY, PaymentConsumer.NAME);
		broker.subscribe(Topics.INVENTORY, OrderWorkflowConsumer.NAME);
		broker.subscribe(Topics.PAYMENTS, ShippingConsumer.NAME);
		broker.subscribe(Topics.PAYMENTS, OrderWorkflowConsumer.NAME);
		broker.subscribe(Topics.SHIPPING, OrderWorkflowConsumer.NAME);
		handlers.put(InventoryConsumer.NAME, inventoryConsumer::consume);
		handlers.put(PaymentConsumer.NAME, paymentConsumer::consume);
		handlers.put(ShippingConsumer.NAME, shippingConsumer::consume);
		handlers.put(OrderWorkflowConsumer.NAME, orderWorkflowConsumer::consume);
	}

	public void crashBeforeAck(String consumerName) {
		this.crashBeforeAckConsumer = consumerName;
	}

	public int pump() {
		int total = 0;
		boolean progress = true;
		while (progress) {
			progress = false;
			int published = relay.tick();
			if (published > 0) {
				total += published;
				progress = true;
			}
			for (String consumerName : handlers.keySet()) {
				if (dispatchOne(consumerName)) {
					total++;
					progress = true;
				}
			}
		}
		metrics.setOldestRetryAt(broker.oldestPendingRetry().orElse(null));
		return total;
	}

	public boolean dispatchOne(String consumerName) {
		Instant now = clock.instant();
		return broker.poll(consumerName, now).map(delivery -> {
			delivery.incrementAttempt();
			try {
				handlers.get(consumerName).accept(delivery.getEvent());
				metrics.eventsConsumedTotal.incrementAndGet();
				if (consumerName.equals(crashBeforeAckConsumer)) {
					crashBeforeAckConsumer = null;
					broker.leaveUnacked(delivery.getDeliveryId());
					throw new CrashBeforeAckException("local TX committed; crashed before ACK");
				}
				broker.ack(delivery.getDeliveryId());
				return true;
			} catch (CrashBeforeAckException e) {
				return true;
			} catch (ProcessingException e) {
				return handleFailure(delivery, e.getFailureClass(), e.getMessage());
			} catch (RuntimeException e) {
				return handleFailure(delivery, FailureClass.RETRYABLE, e.getClass().getSimpleName());
			}
		}).orElse(false);
	}

	private boolean handleFailure(BrokerDelivery delivery, FailureClass failureClass, String message) {
		metrics.consumerProcessingFailureTotal.incrementAndGet();
		String safe = sanitize(message);
		if (failureClass == FailureClass.PERMANENT
				|| delivery.getAttemptCount() >= properties.getConsumer().getMaxAttempts()) {
			persistDeadLetter(delivery, safe);
			broker.deadLetter(delivery.getDeliveryId(), safe);
			metrics.deadLetterTotal.incrementAndGet();
			return true;
		}
		metrics.consumerRetryTotal.incrementAndGet();
		long delay = OutboxBackoff.consumerDelayMs(properties.getConsumer(), delivery.getAttemptCount());
		broker.scheduleRetry(delivery.getDeliveryId(), clock.instant().plusMillis(delay), safe);
		return true;
	}

	private void persistDeadLetter(BrokerDelivery delivery, String reason) {
		try {
			deadLetterRepository.save(new DeadLetter(
					UUID.randomUUID(),
					delivery.getEvent().eventId(),
					delivery.getConsumerName(),
					objectMapper.writeValueAsString(delivery.getEvent()),
					delivery.getAttemptCount(),
					reason,
					clock.instant()));
		} catch (JsonProcessingException e) {
			deadLetterRepository.save(new DeadLetter(
					UUID.randomUUID(),
					delivery.getEvent().eventId(),
					delivery.getConsumerName(),
					"{\"event_id\":\"" + delivery.getEvent().eventId() + "\"}",
					delivery.getAttemptCount(),
					reason,
					clock.instant()));
		}
	}

	public static String sanitize(String message) {
		if (message == null) {
			return "processing_failed";
		}
		String trimmed = message.replaceAll("(?i)(password|secret|token)=\\S+", "$1=redacted");
		return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200);
	}
}

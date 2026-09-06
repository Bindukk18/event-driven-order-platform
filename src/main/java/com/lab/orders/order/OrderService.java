package com.lab.orders.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lab.orders.messaging.EventEnvelope;
import com.lab.orders.messaging.EventTypes;
import com.lab.orders.messaging.Topics;
import com.lab.orders.outbox.OutboxWriter;

/**
 * Create never calls the broker. Order + OutboxEvent commit together.
 */
@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final OutboxWriter outboxWriter;

	public OrderService(OrderRepository orderRepository, OutboxWriter outboxWriter) {
		this.orderRepository = orderRepository;
		this.outboxWriter = outboxWriter;
	}

	@Transactional
	public Order createOrder(String customerId, String sku, int quantity) {
		UUID orderId = UUID.randomUUID();
		UUID eventId = UUID.randomUUID();
		Instant now = Instant.now();
		BigDecimal amount = BigDecimal.valueOf(quantity).multiply(new BigDecimal("10.00"));

		Order order = new Order(
				orderId,
				customerId,
				sku,
				quantity,
				amount,
				OrderStatus.AWAITING_INVENTORY,
				1,
				now);
		orderRepository.save(order);

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("order_id", orderId.toString());
		payload.put("customer_id", customerId);
		payload.put("sku", sku);
		payload.put("quantity", quantity);
		payload.put("amount", amount.toPlainString());

		EventEnvelope event = new EventEnvelope(
				eventId,
				EventTypes.ORDER_CREATED,
				EventTypes.CURRENT_VERSION,
				"Order",
				orderId,
				1,
				orderId.toString(),
				null,
				now,
				payload);
		outboxWriter.append(Topics.ORDERS, event, now);
		return order;
	}
}

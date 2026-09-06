package com.lab.orders.messaging;

public final class EventTypes {

	public static final String ORDER_CREATED = "OrderCreated";
	public static final String INVENTORY_RESERVED = "InventoryReserved";
	public static final String INVENTORY_REJECTED = "InventoryRejected";
	public static final String PAYMENT_COMPLETED = "PaymentCompleted";
	public static final String PAYMENT_FAILED = "PaymentFailed";
	public static final String SHIPMENT_CREATED = "ShipmentCreated";

	public static final int CURRENT_VERSION = 1;

	private EventTypes() {
	}
}

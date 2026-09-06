# Event contracts

Envelope (version 1):

```
event_id
event_type
event_version
aggregate_type
aggregate_id
aggregate_version
correlation_id
causation_id
occurred_at
payload
```

`event_version` is the **schema** version (currently 1). Unsupported
schema versions are permanent failures.

`aggregate_version` is the **Order** sequence number.

`correlation_id` is the order id for the whole workflow.
`causation_id` is the `event_id` that triggered this event (`null` on
`OrderCreated`).

Payloads are compact maps. JPA entities are never serialized.

## OrderCreated

- producer: OrderService
- consumer: InventoryConsumer
- required payload: `order_id`, `customer_id`, `sku`, `quantity`, `amount`
- meaning: an order exists and is waiting for inventory

## InventoryReserved

- producer: InventoryConsumer
- consumers: PaymentConsumer, OrderWorkflowConsumer
- required payload: `order_id`, `reservation_id`, `sku`, `quantity`
- meaning: stock was reserved for this order

## InventoryRejected

- producer: InventoryConsumer
- consumer: OrderWorkflowConsumer
- required payload: `order_id`, `sku`, `reason`
- meaning: reservation failed; workflow stops. No compensation here.

## PaymentCompleted

- producer: PaymentConsumer
- consumers: ShippingConsumer, OrderWorkflowConsumer
- required payload: `order_id`, `amount`, `sku`
- meaning: provider returned a definite success

## PaymentFailed

- producer: PaymentConsumer
- consumer: OrderWorkflowConsumer
- required payload: `order_id`, `amount`, `sku`
- meaning: provider returned a definite decline. Workflow stops.

## ShipmentCreated

- producer: ShippingConsumer
- consumer: OrderWorkflowConsumer
- required payload: `order_id`, `shipment_id`
- meaning: a shipment record exists

There is no schema registry in this lab.

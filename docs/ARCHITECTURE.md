# Architecture

Four logical components share one process and one Postgres database.
Each owns its own tables.

```
OrderService          orders
InventoryConsumer     inventory_reservation
PaymentConsumer       payment_attempt
ShippingConsumer      shipment
```

Shared:

```
consumer_inbox
outbox_event
dead_letter
```

## Pipeline

```
Create Order
   local TX: orders + OutboxEvent(OrderCreated)
        ↓
   OutboxRelay  (publish outside the producer TX)
        ↓
   LAB / TEST BROKER   at-least-once
        ↓
   InventoryConsumer
   local TX: inbox + reservation + OutboxEvent(InventoryReserved|Rejected)
        ↓
   PaymentConsumer
   local TX: inbox + payment_attempt + optional OutboxEvent
        ↓
   ShippingConsumer
   local TX: inbox + shipment + OutboxEvent(ShipmentCreated)
        ↓
   OrderWorkflowConsumer
   local TX: inbox + orders.status / last_applied_version
```

## Order states

Stored states and allowed transitions:

```
CREATED                 (momentary; create TX writes AWAITING_INVENTORY)
  ↓
AWAITING_INVENTORY
  ↓ InventoryReserved     → AWAITING_PAYMENT
  ↓ InventoryRejected     → INVENTORY_REJECTED   (terminal; no compensation)
AWAITING_PAYMENT
  ↓ PaymentCompleted      → READY_TO_SHIP
  ↓ PaymentFailed         → PAYMENT_FAILED       (terminal; no compensation)
READY_TO_SHIP
  ↓ ShipmentCreated       → SHIPPED
```

`INVENTORY_RESERVED` and `PAID` are logical steps on those transitions,
not extra durable wait states.

## Event versions on the Order aggregate

| Event | aggregate_version |
|---|---|
| OrderCreated | 1 |
| InventoryReserved / InventoryRejected | 2 |
| PaymentCompleted / PaymentFailed | 3 |
| ShipmentCreated | 4 |

## Consumer names

- `inventory-consumer`
- `payment-consumer`
- `shipping-consumer`
- `order-workflow-consumer`

Inbox uniqueness is `(consumer_name, event_id)`.

## Payment timeout (UNKNOWN)

`PAY-TIMEOUT` exercises **CHARGE_SUCCEEDS_RESPONSE_LOST** on the
FAKE / TEST provider:

- provider accepts `event_id` as the idempotency key
- provider records **one** successful charge in a hidden ledger
- caller receives timeout / `UNKNOWN` — it cannot see that ledger
- PaymentConsumer persists `PaymentAttempt` as UNKNOWN
- no `PaymentCompleted` or `PaymentFailed`
- Order stays `AWAITING_PAYMENT`
- redelivery of the same event does not create charge #2

UNKNOWN is not blindly retryable. This lab does **not** implement
status-query reconciliation. A real system would look up the same
idempotency key and then emit `PaymentCompleted` or `PaymentFailed`
once. The demo prints the hidden charge count only because this is a
LAB TEST DOUBLE.

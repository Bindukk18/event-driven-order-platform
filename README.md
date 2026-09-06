# Event-Driven Order Platform

How does an asynchronous Order → Inventory → Payment → Shipping workflow
remain correct when events are duplicated, retried or delivered out of order?

```
ORDER
  ↓ OrderCreated
INVENTORY
  ↓ InventoryReserved
PAYMENT
  ↓ PaymentCompleted
SHIPPING
  ↓ ShipmentCreated
```

**BROKER DELIVERY IS AT-LEAST-ONCE**

therefore:

**EVERY CONSUMER MUST BE IDEMPOTENT**

```
duplicate delivery
    → inbox unique (consumer_name, event_id)
    → local business effect once
    → outgoing event intent once
```

This is a **reliability lab**, not an ecommerce platform. There is no UI,
catalog, checkout, Kafka, saga engine, or exactly-once claim.

The broker is an in-process **LAB / TEST BROKER** (`LabBroker`). Semantics
are broker-neutral.

## Thesis

```
AT-LEAST-ONCE DELIVERY
        +
IDEMPOTENT CONSUMERS
        +
EXPLICIT EVENT STATE
        +
BOUNDED RETRIES
        +
OBSERVABILITY
```

not:

"messages are exactly once"

Each consumer applies **one local business effect per (event_id, consumer_name)**,
even if the event is delivered repeatedly.

## Quick start

```bash
make test
make demo
```

Requires Java 21. Tests use embedded PostgreSQL (Zonky), same strategy as
the other portfolio labs.

## Sample demo output

```
HAPPY PATH
OrderCreated  bdda48fd-d2d9-4d8e-ae9e-00229212bc36  caused-by -
InventoryReserved  823587d7-6af1-43b4-aa5e-551e2c912cf4  caused-by bdda48fd-d2d9-4d8e-ae9e-00229212bc36
PaymentCompleted  8e931207-5261-4bc8-9834-d6d30ea34c34  caused-by 823587d7-6af1-43b4-aa5e-551e2c912cf4
ShipmentCreated  b7ddd4c0-0d16-44fa-9e05-54783eb80e88  caused-by 8e931207-5261-4bc8-9834-d6d30ea34c34
Correlation: 0e554193-bba7-4bda-ab1d-249417f42be7
Final status: SHIPPED
DUPLICATE OrderCreated
inventory reservations=1
InventoryReserved outbox=1
consumer_duplicate_total=1
TIMEOUT AFTER PROVIDER EFFECT
provider successful charges=1
local payment status=UNKNOWN
order status=AWAITING_PAYMENT
blind retry issued=false
LAB TEST DOUBLE: charge count is hidden provider truth, not app-visible state
```

LOCAL LAB RESULT from `make demo` — not a benchmark.
Event IDs change each run; the causation chain does not. See
[docs/results/2026-09-06-local-demo.md](docs/results/2026-09-06-local-demo.md).

## Local consumer transaction

For every consumer, one local DB transaction writes:

```
inbox claim
+ domain mutation
+ outgoing OutboxEvent
```

Then a relay publishes **outside** that transaction. There is no
`DB commit` then `broker.publish()` on the producer path.

## Failure stops the workflow

`InventoryRejected` and `PaymentFailed` are terminals. This lab does
**not** compensate. A later saga-orchestration lab covers that.

A payment provider timeout is **PAYMENT_UNKNOWN**, not failure.

The provider-side effect **may already have happened** even though the
caller timed out. The consumer does not emit `PaymentCompleted` or
`PaymentFailed`, and **UNKNOWN is not blindly retryable** — a second
charge must not be issued. Recovery is a later status lookup against
the same idempotency key (not automated in this lab).

## Security notes

- validate event schema and `event_version` before mutation
- real systems authenticate broker producers/consumers
- do not put secrets in payloads
- minimize PII
- add tenant context if multi-tenant
- protect DLQ replay (same `event_id`, authorized operators only)
- sanitize failure messages
- no compliance claim

## Limitations

- lab, not a production order platform
- broker is a LAB / TEST DOUBLE
- no Kafka durability or performance claim
- no distributed transaction
- no exactly-once delivery, processing, or workflow
- compensation intentionally omitted
- global ordering is not guaranteed
- payment provider behavior is simplified
- DLQ replay is a documented operator step, not a full ops product
- outbox cleanup/retention is simplified
- no multi-region design
- no performance benchmark

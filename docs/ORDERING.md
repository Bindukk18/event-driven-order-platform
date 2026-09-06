# Ordering

This lab does **not** claim global ordering.

Ordering is **per aggregate** (`aggregate_id` + `aggregate_version`).

`OrderWorkflowConsumer` tracks `orders.last_applied_version`.

| Incoming version | Action |
|---|---|
| `<= current` | stale or duplicate — claim inbox, ignore |
| `== current + 1` | apply |
| `> current + 1` | gap — do **not** apply; retryable `ordering_gap` |

There is no global resequencer.

Happy-path versions are consecutive: OrderCreated=1, inventory=2,
payment=3, shipment=4. The same rule covers a PaymentCompleted
(version 3) that arrives before InventoryReserved (version 2).

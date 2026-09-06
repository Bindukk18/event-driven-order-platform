# Observability

Lab counters (`LabMetrics`). No `event_id` or `order_id` labels.

| Metric | Meaning |
|---|---|
| events_published_total | broker publishes |
| events_consumed_total | handler returned without throw |
| consumer_duplicate_total | inbox unique hits |
| consumer_processing_failure_total | handler classified failures |
| consumer_retry_total | retryable reschedules |
| dead_letter_total | durable DLQ writes |
| oldest_retry_age | age of oldest pending retried delivery |
| outbox_pending_count | PENDING outbox rows |
| outbox_oldest_age | age of oldest PENDING outbox row |
| event_processing_latency | consume time minus `occurred_at` |
| end_to_end_order_event_lag | create time to SHIPPED apply |
| ordering_gap_total | version gaps observed |
| unknown_payment_count | caller timed out; provider-side effect may already exist |
| broker_redelivery_total | duplicate / unacked redeliveries |

Failure reasons written to DLQ are sanitized (no secrets).

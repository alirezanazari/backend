UPDATE outbox_events
SET status = 'DEAD_LETTER'
WHERE status = 'FAILED';

package com.redbank.account.infrastructure;

import com.redbank.account.domain.OutboxEventRecord;
import com.redbank.account.domain.OutboxEventStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public OutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(OutboxEventRecord event) {
        jdbcTemplate.update("""
                        INSERT INTO outbox_events (
                            id, aggregate_type, aggregate_id, event_type, payload, status,
                            created_at, updated_at, published_at, retry_count
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                event.id(),
                event.aggregateType(),
                event.aggregateId(),
                event.eventType(),
                event.payload(),
                event.status().name(),
                Timestamp.from(event.createdAt()),
                Timestamp.from(event.updatedAt()),
                timestamp(event.publishedAt()),
                event.retryCount()
        );
    }

    public List<OutboxEventRecord> findPending(int limit, int maxRetryCount) {
        return jdbcTemplate.query("""
                        SELECT id, aggregate_type, aggregate_id, event_type, payload, status,
                               created_at, updated_at, published_at, retry_count
                        FROM outbox_events
                        WHERE status = 'PENDING'
                          AND retry_count < ?
                        ORDER BY created_at ASC
                        LIMIT ?
                        """,
                this::map,
                maxRetryCount,
                limit
        );
    }

    public void markPublished(UUID id, Instant publishedAt) {
        jdbcTemplate.update("""
                        UPDATE outbox_events
                        SET status = ?, published_at = ?, updated_at = ?
                        WHERE id = ?
                        """,
                OutboxEventStatus.PUBLISHED.name(),
                Timestamp.from(publishedAt),
                Timestamp.from(publishedAt),
                id
        );
    }

    public void markAttemptFailed(UUID id, Instant updatedAt, int maxRetryCount) {
        jdbcTemplate.update("""
                        UPDATE outbox_events
                        SET retry_count = retry_count + 1,
                            status = CASE WHEN retry_count + 1 >= ? THEN ? ELSE ? END,
                            updated_at = ?
                        WHERE id = ?
                        """,
                maxRetryCount,
                OutboxEventStatus.FAILED.name(),
                OutboxEventStatus.PENDING.name(),
                Timestamp.from(updatedAt),
                id
        );
    }

    private OutboxEventRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxEventRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("aggregate_type"),
                resultSet.getString("aggregate_id"),
                resultSet.getString("event_type"),
                resultSet.getString("payload"),
                OutboxEventStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                toNullableInstant(resultSet, "published_at"),
                resultSet.getInt("retry_count")
        );
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant toNullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

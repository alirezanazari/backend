package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.DeviceRecord;
import com.redbank.iam.domain.DeviceStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeviceRepository {
    private final JdbcTemplate jdbcTemplate;

    public DeviceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(DeviceRecord device) {
        jdbcTemplate.update("""
                        INSERT INTO devices (
                            id, user_id, device_fingerprint, device_name, os, app_version,
                            status, verified_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                device.id(),
                device.userId(),
                device.deviceFingerprint(),
                device.deviceName(),
                device.os(),
                device.appVersion(),
                device.status().name(),
                timestamp(device.verifiedAt()),
                Timestamp.from(device.createdAt()),
                Timestamp.from(device.updatedAt())
        );
    }

    public Optional<DeviceRecord> findByUserIdAndFingerprint(UUID userId, String fingerprint) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, device_fingerprint, device_name, os, app_version,
                               status, verified_at, created_at, updated_at
                        FROM devices
                        WHERE user_id = ? AND device_fingerprint = ?
                        """,
                this::map,
                userId,
                fingerprint
        ).stream().findFirst();
    }

    public Optional<DeviceRecord> findById(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, device_fingerprint, device_name, os, app_version,
                               status, verified_at, created_at, updated_at
                        FROM devices
                        WHERE id = ?
                        """,
                this::map,
                id
        ).stream().findFirst();
    }

    public void markVerified(UUID id, Instant verifiedAt) {
        jdbcTemplate.update("""
                        UPDATE devices
                        SET status = ?, verified_at = ?, updated_at = ?
                        WHERE id = ?
                        """,
                DeviceStatus.VERIFIED.name(),
                Timestamp.from(verifiedAt),
                Timestamp.from(verifiedAt),
                id
        );
    }

    private DeviceRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DeviceRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("device_fingerprint"),
                resultSet.getString("device_name"),
                resultSet.getString("os"),
                resultSet.getString("app_version"),
                DeviceStatus.valueOf(resultSet.getString("status")),
                toNullableInstant(resultSet, "verified_at"),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
        );
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private Instant toNullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}

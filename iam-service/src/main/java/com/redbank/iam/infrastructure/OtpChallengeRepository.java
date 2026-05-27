package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.OtpChallengeRecord;
import com.redbank.iam.domain.OtpChallengeStatus;
import com.redbank.iam.domain.OtpPurpose;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OtpChallengeRepository {
    private final JdbcTemplate jdbcTemplate;

    public OtpChallengeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(OtpChallengeRecord challenge) {
        jdbcTemplate.update("""
                        INSERT INTO otp_challenges (
                            id, user_id, device_id, otp_hash, purpose, expires_at, attempt_count,
                            max_attempts, status, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                challenge.id(),
                challenge.userId(),
                challenge.deviceId(),
                challenge.otpHash(),
                challenge.purpose().name(),
                Timestamp.from(challenge.expiresAt()),
                challenge.attemptCount(),
                challenge.maxAttempts(),
                challenge.status().name(),
                Timestamp.from(challenge.createdAt()),
                Timestamp.from(challenge.updatedAt())
        );
    }

    public Optional<OtpChallengeRecord> findById(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, device_id, otp_hash, purpose, expires_at, attempt_count,
                               max_attempts, status, created_at, updated_at
                        FROM otp_challenges
                        WHERE id = ?
                        """,
                this::map,
                id
        ).stream().findFirst();
    }

    public void incrementAttemptCount(UUID id, Instant updatedAt) {
        jdbcTemplate.update("""
                        UPDATE otp_challenges
                        SET attempt_count = attempt_count + 1, updated_at = ?
                        WHERE id = ?
                        """,
                Timestamp.from(updatedAt),
                id
        );
    }

    public void markStatus(UUID id, OtpChallengeStatus status, Instant updatedAt) {
        jdbcTemplate.update("""
                        UPDATE otp_challenges
                        SET status = ?, updated_at = ?
                        WHERE id = ?
                        """,
                status.name(),
                Timestamp.from(updatedAt),
                id
        );
    }

    private OtpChallengeRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OtpChallengeRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getObject("device_id", UUID.class),
                resultSet.getString("otp_hash"),
                OtpPurpose.valueOf(resultSet.getString("purpose")),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getInt("attempt_count"),
                resultSet.getInt("max_attempts"),
                OtpChallengeStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}

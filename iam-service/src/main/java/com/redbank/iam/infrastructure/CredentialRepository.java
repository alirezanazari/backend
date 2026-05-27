package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.CredentialRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CredentialRepository {
    private final JdbcTemplate jdbcTemplate;

    public CredentialRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(CredentialRecord credential) {
        jdbcTemplate.update("""
                        INSERT INTO credentials (
                            id, user_id, password_hash, password_algorithm, password_changed_at, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                credential.id(),
                credential.userId(),
                credential.passwordHash(),
                credential.passwordAlgorithm(),
                Timestamp.from(credential.passwordChangedAt()),
                Timestamp.from(credential.createdAt()),
                Timestamp.from(credential.updatedAt())
        );
    }

    public Optional<CredentialRecord> findByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, password_hash, password_algorithm, password_changed_at, created_at, updated_at
                        FROM credentials
                        WHERE user_id = ?
                        """,
                this::map,
                userId
        ).stream().findFirst();
    }

    private CredentialRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CredentialRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("password_hash"),
                resultSet.getString("password_algorithm"),
                toInstant(resultSet, "password_changed_at"),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}

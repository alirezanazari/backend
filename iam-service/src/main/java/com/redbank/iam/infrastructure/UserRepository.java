package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.UserRecord;
import com.redbank.iam.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(UserRecord user) {
        jdbcTemplate.update("""
                        INSERT INTO users (
                            id, username, phone_number, national_id, first_name, last_name, country_code,
                            province, city, address, postal_code, selected_job_id, status, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                user.id(),
                user.username(),
                user.phoneNumber(),
                user.nationalId(),
                user.firstName(),
                user.lastName(),
                user.countryCode(),
                user.province(),
                user.city(),
                user.address(),
                user.postalCode(),
                user.selectedJobId(),
                user.status().name(),
                Timestamp.from(user.createdAt()),
                Timestamp.from(user.updatedAt())
        );
    }

    public Optional<UserRecord> findByUsername(String username) {
        return jdbcTemplate.query("""
                        SELECT id, username, phone_number, national_id, first_name, last_name, country_code,
                               province, city, address, postal_code, selected_job_id, status, created_at, updated_at
                        FROM users
                        WHERE username = ?
                        """,
                this::map,
                username
        ).stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        return exists("username", username);
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return exists("phone_number", phoneNumber);
    }

    public boolean existsByNationalId(String nationalId) {
        return exists("national_id", nationalId);
    }

    public Optional<UserRecord> findById(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, username, phone_number, national_id, first_name, last_name, country_code,
                               province, city, address, postal_code, selected_job_id, status, created_at, updated_at
                        FROM users
                        WHERE id = ?
                        """,
                this::map,
                id
        ).stream().findFirst();
    }

    private UserRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UserRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("username"),
                resultSet.getString("phone_number"),
                resultSet.getString("national_id"),
                resultSet.getString("first_name"),
                resultSet.getString("last_name"),
                resultSet.getString("country_code"),
                resultSet.getString("province"),
                resultSet.getString("city"),
                resultSet.getString("address"),
                resultSet.getString("postal_code"),
                resultSet.getString("selected_job_id"),
                UserStatus.valueOf(resultSet.getString("status")),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
        );
    }

    private boolean exists(String column, String value) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE " + column + " = ?",
                Integer.class,
                value
        );
        return count != null && count > 0;
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}

package com.redbank.account.infrastructure;

import com.redbank.account.domain.ProfileRecord;
import com.redbank.account.domain.ProfileStatus;
import com.redbank.account.domain.ProfileStatusHistoryRecord;
import com.redbank.contracts.account.ProfileType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProfileRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(ProfileRecord profile) {
        jdbcTemplate.update("""
                        INSERT INTO profiles (
                            id, user_id, selected_job_id, type, display_name, display_name_normalized, image_light_url,
                            image_dark_url, image_alt, status, is_active, created_at, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                profile.id(),
                profile.userId(),
                profile.selectedJobId(),
                profile.type().name(),
                profile.displayName(),
                profile.displayNameNormalized(),
                profile.imageLightUrl(),
                profile.imageDarkUrl(),
                profile.imageAlt(),
                profile.status().name(),
                profile.active(),
                Timestamp.from(profile.createdAt()),
                Timestamp.from(profile.updatedAt())
        );
    }

    public int countByUserId(UUID userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM profiles WHERE user_id = ?",
                Integer.class,
                userId
        );
        return count == null ? 0 : count;
    }

    public int countActiveByUserIdAndType(UUID userId, ProfileType type) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM profiles
                        WHERE user_id = ?
                          AND type = ?
                          AND status = 'ACTIVE'
                        """,
                Integer.class,
                userId,
                type.name()
        );
        return count == null ? 0 : count;
    }

    public boolean existsActiveByUserIdAndTypeAndDisplayNameNormalized(
            UUID userId,
            ProfileType type,
            String displayNameNormalized
    ) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM profiles
                        WHERE user_id = ?
                          AND type = ?
                          AND display_name_normalized = ?
                          AND status = 'ACTIVE'
                        """,
                Integer.class,
                userId,
                type.name(),
                displayNameNormalized
        );
        return count != null && count > 0;
    }

    public Optional<ProfileRecord> findById(UUID id) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, selected_job_id, type, display_name, display_name_normalized, image_light_url,
                               image_dark_url, image_alt, status, is_active, created_at, updated_at
                        FROM profiles
                        WHERE id = ?
                        """,
                this::map,
                id
        ).stream().findFirst();
    }

    public Optional<ProfileRecord> findActiveByUserId(UUID userId) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, selected_job_id, type, display_name, display_name_normalized, image_light_url,
                               image_dark_url, image_alt, status, is_active, created_at, updated_at
                        FROM profiles
                        WHERE user_id = ? AND is_active = TRUE
                        ORDER BY created_at ASC
                        LIMIT 1
                        """,
                this::map,
                userId
        ).stream().findFirst();
    }

    public Optional<ProfileRecord> findFirstByUserIdAndType(UUID userId, ProfileType type) {
        return jdbcTemplate.query("""
                        SELECT id, user_id, selected_job_id, type, display_name, display_name_normalized, image_light_url,
                               image_dark_url, image_alt, status, is_active, created_at, updated_at
                        FROM profiles
                        WHERE user_id = ? AND type = ? AND status = 'ACTIVE'
                        ORDER BY created_at ASC
                        LIMIT 1
                        """,
                this::map,
                userId,
                type.name()
        ).stream().findFirst();
    }

    public List<ProfileRecord> findByUserId(UUID userId, boolean includeInactive) {
        String statusPredicate = includeInactive ? "" : "AND status = 'ACTIVE'";
        return jdbcTemplate.query("""
                        SELECT id, user_id, selected_job_id, type, display_name, display_name_normalized, image_light_url,
                               image_dark_url, image_alt, status, is_active, created_at, updated_at
                        FROM profiles
                        WHERE user_id = ? %s
                        ORDER BY created_at ASC
                        """.formatted(statusPredicate),
                this::map,
                userId
        );
    }

    public void setActive(UUID userId, UUID profileId, Instant updatedAt) {
        jdbcTemplate.update("""
                        UPDATE profiles
                        SET is_active = FALSE, updated_at = ?
                        WHERE user_id = ?
                        """,
                Timestamp.from(updatedAt),
                userId
        );
        jdbcTemplate.update("""
                        UPDATE profiles
                        SET is_active = TRUE, updated_at = ?
                        WHERE user_id = ? AND id = ?
                        """,
                Timestamp.from(updatedAt),
                userId,
                profileId
        );
    }

    public void edit(ProfileRecord profile, Instant updateAt) {
        jdbcTemplate.update("""
                        UPDATE profiles
                        SET display_name = ?, display_name_normalized = ?, image_light_url = ?,
                            image_dark_url = ?, image_alt = ?, updated_at = ?
                        WHERE id = ?
                        """,
                profile.displayName(),
                profile.displayNameNormalized(),
                profile.imageLightUrl(),
                profile.imageDarkUrl(),
                profile.imageAlt(),
                Timestamp.from(updateAt),
                profile.id()
        );
    }

    public void close(ProfileRecord profile, Instant closedAt) {
        jdbcTemplate.update("""
                        UPDATE profiles
                        SET status = ?, is_active = FALSE, updated_at = ?
                        WHERE id = ?
                        """,
                ProfileStatus.INACTIVE.name(),
                Timestamp.from(closedAt),
                profile.id()
        );
    }

    public void openAndSetActive(ProfileRecord profile, Instant openedAt) {
        jdbcTemplate.update("""
                        UPDATE profiles
                        SET is_active = FALSE, updated_at = ?
                        WHERE user_id = ?
                        """,
                Timestamp.from(openedAt),
                profile.userId()
        );
        jdbcTemplate.update("""
                        UPDATE profiles
                        SET status = ?, is_active = TRUE, updated_at = ?
                        WHERE user_id = ? AND id = ?
                        """,
                ProfileStatus.ACTIVE.name(),
                Timestamp.from(openedAt),
                profile.userId(),
                profile.id()
        );
    }

    public void saveStatusHistory(ProfileStatusHistoryRecord history) {
        jdbcTemplate.update("""
                        INSERT INTO profile_status_history (
                            id, profile_id, user_id, previous_status, new_status, reason_text, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                history.id(),
                history.profileId(),
                history.userId(),
                history.previousStatus().name(),
                history.newStatus().name(),
                history.reasonText(),
                Timestamp.from(history.createdAt())
        );
    }

    private ProfileRecord map(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ProfileRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("user_id", UUID.class),
                resultSet.getString("selected_job_id"),
                ProfileType.valueOf(resultSet.getString("type")),
                resultSet.getString("display_name"),
                resultSet.getString("display_name_normalized"),
                resultSet.getString("image_light_url"),
                resultSet.getString("image_dark_url"),
                resultSet.getString("image_alt"),
                ProfileStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("is_active"),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "updated_at")
        );
    }

    private Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }
}

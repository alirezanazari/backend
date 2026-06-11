package com.redbank.account.infrastructure;

import com.redbank.account.domain.ProfileRecord;
import com.redbank.account.domain.ProfileStatus;
import com.redbank.account.domain.ProfileStatusHistoryRecord;
import com.redbank.contracts.account.ProfileType;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.account.jooq.tables.ProfileStatusHistory.PROFILE_STATUS_HISTORY;
import static com.redbank.account.jooq.tables.Profiles.PROFILES;

@Repository
public class ProfileRepository {
    private final DSLContext dsl;

    public ProfileRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(ProfileRecord profile) {
        dsl.insertInto(PROFILES)
                .set(PROFILES.ID, profile.id())
                .set(PROFILES.USER_ID, profile.userId())
                .set(PROFILES.SELECTED_JOB_ID, profile.selectedJobId())
                .set(PROFILES.TYPE, profile.type().name())
                .set(PROFILES.DISPLAY_NAME, profile.displayName())
                .set(PROFILES.DISPLAY_NAME_NORMALIZED, profile.displayNameNormalized())
                .set(PROFILES.IMAGE_LIGHT_URL, profile.imageLightUrl())
                .set(PROFILES.IMAGE_DARK_URL, profile.imageDarkUrl())
                .set(PROFILES.IMAGE_ALT, profile.imageAlt())
                .set(PROFILES.STATUS, profile.status().name())
                .set(PROFILES.IS_ACTIVE, profile.active())
                .set(PROFILES.CREATED_AT, toOffsetDateTime(profile.createdAt()))
                .set(PROFILES.UPDATED_AT, toOffsetDateTime(profile.updatedAt()))
                .execute();
    }

    public int countByUserId(UUID userId) {
        return dsl.fetchCount(PROFILES, PROFILES.USER_ID.eq(userId));
    }

    public int countActiveByUserIdAndType(UUID userId, ProfileType type) {
        return dsl.fetchCount(
                PROFILES,
                PROFILES.USER_ID.eq(userId)
                        .and(PROFILES.TYPE.eq(type.name()))
                        .and(PROFILES.STATUS.eq(ProfileStatus.ACTIVE.name()))
        );
    }

    public boolean existsActiveByUserIdAndTypeAndDisplayNameNormalized(
            UUID userId,
            ProfileType type,
            String displayNameNormalized
    ) {
        return dsl.fetchExists(
                PROFILES,
                PROFILES.USER_ID.eq(userId)
                        .and(PROFILES.TYPE.eq(type.name()))
                        .and(PROFILES.DISPLAY_NAME_NORMALIZED.eq(displayNameNormalized))
                        .and(PROFILES.STATUS.eq(ProfileStatus.ACTIVE.name()))
        );
    }

    public boolean existsByUserIdAndTypeAndDisplayNameNormalizedAndIdNot(
            UUID userId,
            ProfileType type,
            String displayNameNormalized,
            UUID excludedProfileId
    ) {
        return dsl.fetchExists(
                PROFILES,
                PROFILES.USER_ID.eq(userId)
                        .and(PROFILES.TYPE.eq(type.name()))
                        .and(PROFILES.DISPLAY_NAME_NORMALIZED.eq(displayNameNormalized))
                        .and(PROFILES.ID.ne(excludedProfileId))
        );
    }

    public Optional<ProfileRecord> findById(UUID id) {
        return dsl.selectFrom(PROFILES)
                .where(PROFILES.ID.eq(id))
                .fetchOptional(this::map);
    }

    public Optional<ProfileRecord> findActiveByUserId(UUID userId) {
        return dsl.selectFrom(PROFILES)
                .where(PROFILES.USER_ID.eq(userId))
                .and(PROFILES.IS_ACTIVE.isTrue())
                .orderBy(PROFILES.CREATED_AT.asc())
                .limit(1)
                .fetchOptional(this::map);
    }

    public Optional<ProfileRecord> findFirstByUserIdAndType(UUID userId, ProfileType type) {
        return dsl.selectFrom(PROFILES)
                .where(PROFILES.USER_ID.eq(userId))
                .and(PROFILES.TYPE.eq(type.name()))
                .and(PROFILES.STATUS.eq(ProfileStatus.ACTIVE.name()))
                .orderBy(PROFILES.CREATED_AT.asc())
                .limit(1)
                .fetchOptional(this::map);
    }

    public List<ProfileRecord> findByUserId(UUID userId, boolean includeInactive) {
        var condition = PROFILES.USER_ID.eq(userId);
        if (!includeInactive) {
            condition = condition.and(PROFILES.STATUS.eq(ProfileStatus.ACTIVE.name()));
        }
        return dsl.selectFrom(PROFILES)
                .where(condition)
                .orderBy(PROFILES.CREATED_AT.asc())
                .fetch(this::map);
    }

    public void setActive(UUID userId, UUID profileId, Instant updatedAt) {
        OffsetDateTime timestamp = toOffsetDateTime(updatedAt);
        dsl.update(PROFILES)
                .set(PROFILES.IS_ACTIVE, false)
                .set(PROFILES.UPDATED_AT, timestamp)
                .where(PROFILES.USER_ID.eq(userId))
                .execute();
        dsl.update(PROFILES)
                .set(PROFILES.IS_ACTIVE, true)
                .set(PROFILES.UPDATED_AT, timestamp)
                .where(PROFILES.USER_ID.eq(userId))
                .and(PROFILES.ID.eq(profileId))
                .execute();
    }

    public boolean edit(UUID userId, ProfileRecord profile, Instant updatedAt) {
        return dsl.update(PROFILES)
                .set(PROFILES.DISPLAY_NAME, profile.displayName())
                .set(PROFILES.DISPLAY_NAME_NORMALIZED, profile.displayNameNormalized())
                .set(PROFILES.IMAGE_LIGHT_URL, profile.imageLightUrl())
                .set(PROFILES.IMAGE_DARK_URL, profile.imageDarkUrl())
                .set(PROFILES.IMAGE_ALT, profile.imageAlt())
                .set(PROFILES.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(PROFILES.USER_ID.eq(userId))
                .and(PROFILES.ID.eq(profile.id()))
                .execute() == 1;
    }

    public void close(ProfileRecord profile, Instant closedAt) {
        dsl.update(PROFILES)
                .set(PROFILES.STATUS, ProfileStatus.INACTIVE.name())
                .set(PROFILES.IS_ACTIVE, false)
                .set(PROFILES.UPDATED_AT, toOffsetDateTime(closedAt))
                .where(PROFILES.ID.eq(profile.id()))
                .execute();
    }

    public void openAndSetActive(ProfileRecord profile, Instant openedAt) {
        OffsetDateTime timestamp = toOffsetDateTime(openedAt);
        dsl.update(PROFILES)
                .set(PROFILES.IS_ACTIVE, false)
                .set(PROFILES.UPDATED_AT, timestamp)
                .where(PROFILES.USER_ID.eq(profile.userId()))
                .execute();
        dsl.update(PROFILES)
                .set(PROFILES.STATUS, ProfileStatus.ACTIVE.name())
                .set(PROFILES.IS_ACTIVE, true)
                .set(PROFILES.UPDATED_AT, timestamp)
                .where(PROFILES.USER_ID.eq(profile.userId()))
                .and(PROFILES.ID.eq(profile.id()))
                .execute();
    }

    public void saveStatusHistory(ProfileStatusHistoryRecord history) {
        dsl.insertInto(PROFILE_STATUS_HISTORY)
                .set(PROFILE_STATUS_HISTORY.ID, history.id())
                .set(PROFILE_STATUS_HISTORY.PROFILE_ID, history.profileId())
                .set(PROFILE_STATUS_HISTORY.USER_ID, history.userId())
                .set(PROFILE_STATUS_HISTORY.PREVIOUS_STATUS, history.previousStatus().name())
                .set(PROFILE_STATUS_HISTORY.NEW_STATUS, history.newStatus().name())
                .set(PROFILE_STATUS_HISTORY.REASON_TEXT, history.reasonText())
                .set(PROFILE_STATUS_HISTORY.CREATED_AT, toOffsetDateTime(history.createdAt()))
                .execute();
    }

    private ProfileRecord map(Record record) {
        return new ProfileRecord(
                record.get(PROFILES.ID),
                record.get(PROFILES.USER_ID),
                record.get(PROFILES.SELECTED_JOB_ID),
                ProfileType.valueOf(record.get(PROFILES.TYPE)),
                record.get(PROFILES.DISPLAY_NAME),
                record.get(PROFILES.DISPLAY_NAME_NORMALIZED),
                record.get(PROFILES.IMAGE_LIGHT_URL),
                record.get(PROFILES.IMAGE_DARK_URL),
                record.get(PROFILES.IMAGE_ALT),
                ProfileStatus.valueOf(record.get(PROFILES.STATUS)),
                record.get(PROFILES.IS_ACTIVE),
                record.get(PROFILES.CREATED_AT).toInstant(),
                record.get(PROFILES.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

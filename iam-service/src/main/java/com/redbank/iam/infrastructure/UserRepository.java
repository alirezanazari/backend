package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.UserRecord;
import com.redbank.iam.domain.UserStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.iam.jooq.tables.Users.USERS;

@Repository
public class UserRepository {
    private final DSLContext dsl;

    public UserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(UserRecord user) {
        dsl.insertInto(USERS)
                .set(USERS.ID, user.id())
                .set(USERS.USERNAME, user.username())
                .set(USERS.PHONE_NUMBER, user.phoneNumber())
                .set(USERS.NATIONAL_ID, user.nationalId())
                .set(USERS.FIRST_NAME, user.firstName())
                .set(USERS.LAST_NAME, user.lastName())
                .set(USERS.COUNTRY_CODE, user.countryCode())
                .set(USERS.PROVINCE, user.province())
                .set(USERS.CITY, user.city())
                .set(USERS.ADDRESS, user.address())
                .set(USERS.POSTAL_CODE, user.postalCode())
                .set(USERS.SELECTED_JOB_ID, user.selectedJobId())
                .set(USERS.STATUS, user.status().name())
                .set(USERS.CREATED_AT, toOffsetDateTime(user.createdAt()))
                .set(USERS.UPDATED_AT, toOffsetDateTime(user.updatedAt()))
                .execute();
    }

    public Optional<UserRecord> findByUsername(String username) {
        return dsl.selectFrom(USERS)
                .where(USERS.USERNAME.eq(username))
                .fetchOptional(this::map);
    }

    public boolean existsByUsername(String username) {
        return dsl.fetchExists(USERS, USERS.USERNAME.eq(username));
    }

    public boolean existsByPhoneNumber(String phoneNumber) {
        return dsl.fetchExists(USERS, USERS.PHONE_NUMBER.eq(phoneNumber));
    }

    public boolean existsByNationalId(String nationalId) {
        return dsl.fetchExists(USERS, USERS.NATIONAL_ID.eq(nationalId));
    }

    public Optional<UserRecord> findById(UUID id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional(this::map);
    }

    private UserRecord map(Record record) {
        return new UserRecord(
                record.get(USERS.ID),
                record.get(USERS.USERNAME),
                record.get(USERS.PHONE_NUMBER),
                record.get(USERS.NATIONAL_ID),
                record.get(USERS.FIRST_NAME),
                record.get(USERS.LAST_NAME),
                record.get(USERS.COUNTRY_CODE),
                record.get(USERS.PROVINCE),
                record.get(USERS.CITY),
                record.get(USERS.ADDRESS),
                record.get(USERS.POSTAL_CODE),
                record.get(USERS.SELECTED_JOB_ID),
                UserStatus.valueOf(record.get(USERS.STATUS)),
                record.get(USERS.CREATED_AT).toInstant(),
                record.get(USERS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

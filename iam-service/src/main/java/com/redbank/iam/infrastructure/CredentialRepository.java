package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.CredentialRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.iam.jooq.tables.Credentials.CREDENTIALS;

@Repository
public class CredentialRepository {
    private final DSLContext dsl;

    public CredentialRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(CredentialRecord credential) {
        dsl.insertInto(CREDENTIALS)
                .set(CREDENTIALS.ID, credential.id())
                .set(CREDENTIALS.USER_ID, credential.userId())
                .set(CREDENTIALS.PASSWORD_HASH, credential.passwordHash())
                .set(CREDENTIALS.PASSWORD_ALGORITHM, credential.passwordAlgorithm())
                .set(CREDENTIALS.PASSWORD_CHANGED_AT, toOffsetDateTime(credential.passwordChangedAt()))
                .set(CREDENTIALS.CREATED_AT, toOffsetDateTime(credential.createdAt()))
                .set(CREDENTIALS.UPDATED_AT, toOffsetDateTime(credential.updatedAt()))
                .execute();
    }

    public Optional<CredentialRecord> findByUserId(UUID userId) {
        return dsl.selectFrom(CREDENTIALS)
                .where(CREDENTIALS.USER_ID.eq(userId))
                .fetchOptional(this::map);
    }

    private CredentialRecord map(Record record) {
        return new CredentialRecord(
                record.get(CREDENTIALS.ID),
                record.get(CREDENTIALS.USER_ID),
                record.get(CREDENTIALS.PASSWORD_HASH),
                record.get(CREDENTIALS.PASSWORD_ALGORITHM),
                record.get(CREDENTIALS.PASSWORD_CHANGED_AT).toInstant(),
                record.get(CREDENTIALS.CREATED_AT).toInstant(),
                record.get(CREDENTIALS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

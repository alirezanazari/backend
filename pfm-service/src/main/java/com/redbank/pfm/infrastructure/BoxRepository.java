package com.redbank.pfm.infrastructure;

import com.redbank.contracts.common.Image;
import com.redbank.contracts.pfm.BoxStatus;
import com.redbank.pfm.domain.BoxRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.pfm.jooq.tables.Boxes.BOXES;

@Repository
public class BoxRepository {
    private final DSLContext dsl;

    public BoxRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(BoxRecord box) {
        dsl.insertInto(BOXES)
                .set(BOXES.ID, box.id())
                .set(BOXES.USER_ID, box.userId())
                .set(BOXES.PROFILE_ID, box.profileId())
                .set(BOXES.ACCOUNT_NUMBER, box.accountNumber())
                .set(BOXES.TITLE, box.title())
                .set(BOXES.PRESET_ID, box.presetId())
                .set(BOXES.IMAGE_LIGHT_URL, lightUrl(box.image()))
                .set(BOXES.IMAGE_DARK_URL, darkUrl(box.image()))
                .set(BOXES.IMAGE_ALT, alt(box.image()))
                .set(BOXES.STATUS, box.status().name())
                .set(BOXES.CREATED_AT, toOffsetDateTime(box.createdAt()))
                .set(BOXES.UPDATED_AT, toOffsetDateTime(box.updatedAt()))
                .execute();
    }

    public List<BoxRecord> findByOwner(
            UUID userId,
            UUID profileId,
            List<BoxStatus> statuses
    ) {
        Condition condition = BOXES.USER_ID.eq(userId).and(BOXES.PROFILE_ID.eq(profileId));
        if (statuses != null && !statuses.isEmpty()) {
            condition = condition.and(BOXES.STATUS.in(statuses.stream().map(Enum::name).toList()));
        }
        return dsl.selectFrom(BOXES)
                .where(condition)
                .orderBy(BOXES.CREATED_AT.asc(), BOXES.ID.asc())
                .fetch(this::map);
    }

    public Optional<BoxRecord> findByIdAndOwner(UUID boxId, UUID userId, UUID profileId) {
        return dsl.selectFrom(BOXES)
                .where(BOXES.ID.eq(boxId))
                .and(BOXES.USER_ID.eq(userId))
                .and(BOXES.PROFILE_ID.eq(profileId))
                .fetchOptional(this::map);
    }

    public Optional<BoxRecord> findByAccountNumberAndOwner(
            String accountNumber,
            UUID userId,
            UUID profileId
    ) {
        return dsl.selectFrom(BOXES)
                .where(BOXES.ACCOUNT_NUMBER.eq(accountNumber))
                .and(BOXES.USER_ID.eq(userId))
                .and(BOXES.PROFILE_ID.eq(profileId))
                .fetchOptional(this::map);
    }

    public boolean updateDisplayMetadata(BoxRecord box) {
        int updated = dsl.update(BOXES)
                .set(BOXES.TITLE, box.title())
                .set(BOXES.PRESET_ID, box.presetId())
                .set(BOXES.IMAGE_LIGHT_URL, lightUrl(box.image()))
                .set(BOXES.IMAGE_DARK_URL, darkUrl(box.image()))
                .set(BOXES.IMAGE_ALT, alt(box.image()))
                .set(BOXES.UPDATED_AT, toOffsetDateTime(box.updatedAt()))
                .where(BOXES.ID.eq(box.id()))
                .and(BOXES.USER_ID.eq(box.userId()))
                .and(BOXES.PROFILE_ID.eq(box.profileId()))
                .execute();
        return updated == 1;
    }

    private BoxRecord map(Record record) {
        return new BoxRecord(
                record.get(BOXES.ID),
                record.get(BOXES.USER_ID),
                record.get(BOXES.PROFILE_ID),
                record.get(BOXES.ACCOUNT_NUMBER),
                record.get(BOXES.TITLE),
                record.get(BOXES.PRESET_ID),
                new Image(
                        record.get(BOXES.IMAGE_LIGHT_URL),
                        record.get(BOXES.IMAGE_DARK_URL),
                        record.get(BOXES.IMAGE_ALT)
                ),
                BoxStatus.valueOf(record.get(BOXES.STATUS)),
                record.get(BOXES.CREATED_AT).toInstant(),
                record.get(BOXES.UPDATED_AT).toInstant()
        );
    }

    private String lightUrl(Image image) {
        return image == null ? null : image.lightUrl();
    }

    private String darkUrl(Image image) {
        return image == null ? null : image.darkUrl();
    }

    private String alt(Image image) {
        return image == null ? null : image.alt();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

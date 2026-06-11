package com.redbank.pfm.infrastructure;

import com.redbank.contracts.common.Image;
import com.redbank.pfm.domain.BoxPresetRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.pfm.jooq.tables.BoxPresets.BOX_PRESETS;

@Repository
public class BoxPresetRepository {
    private final DSLContext dsl;

    public BoxPresetRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<BoxPresetRecord> findActive(String language) {
        return dsl.selectFrom(BOX_PRESETS)
                .where(BOX_PRESETS.LANGUAGE.eq(language))
                .and(BOX_PRESETS.IS_ACTIVE.isTrue())
                .orderBy(BOX_PRESETS.SORT_ORDER.asc(), BOX_PRESETS.ID.asc())
                .fetch(this::map);
    }

    public Optional<BoxPresetRecord> findActiveById(UUID presetId) {
        return dsl.selectFrom(BOX_PRESETS)
                .where(BOX_PRESETS.ID.eq(presetId))
                .and(BOX_PRESETS.IS_ACTIVE.isTrue())
                .fetchOptional(this::map);
    }

    private BoxPresetRecord map(Record record) {
        return new BoxPresetRecord(
                record.get(BOX_PRESETS.ID),
                record.get(BOX_PRESETS.LANGUAGE),
                record.get(BOX_PRESETS.TITLE),
                new Image(
                        record.get(BOX_PRESETS.IMAGE_LIGHT_URL),
                        record.get(BOX_PRESETS.IMAGE_DARK_URL),
                        record.get(BOX_PRESETS.IMAGE_ALT)
                ),
                record.get(BOX_PRESETS.SORT_ORDER),
                Boolean.TRUE.equals(record.get(BOX_PRESETS.IS_ACTIVE)),
                record.get(BOX_PRESETS.CREATED_AT).toInstant(),
                record.get(BOX_PRESETS.UPDATED_AT).toInstant()
        );
    }
}

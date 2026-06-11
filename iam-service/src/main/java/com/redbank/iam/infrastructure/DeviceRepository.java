package com.redbank.iam.infrastructure;

import com.redbank.iam.domain.DeviceRecord;
import com.redbank.iam.domain.DeviceStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.iam.jooq.tables.Devices.DEVICES;

@Repository
public class DeviceRepository {
    private final DSLContext dsl;

    public DeviceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(DeviceRecord device) {
        dsl.insertInto(DEVICES)
                .set(DEVICES.ID, device.id())
                .set(DEVICES.USER_ID, device.userId())
                .set(DEVICES.DEVICE_FINGERPRINT, device.deviceFingerprint())
                .set(DEVICES.DEVICE_NAME, device.deviceName())
                .set(DEVICES.OS, device.os())
                .set(DEVICES.APP_VERSION, device.appVersion())
                .set(DEVICES.STATUS, device.status().name())
                .set(DEVICES.VERIFIED_AT, toOffsetDateTime(device.verifiedAt()))
                .set(DEVICES.CREATED_AT, toOffsetDateTime(device.createdAt()))
                .set(DEVICES.UPDATED_AT, toOffsetDateTime(device.updatedAt()))
                .execute();
    }

    public Optional<DeviceRecord> findByUserIdAndFingerprint(UUID userId, String fingerprint) {
        return dsl.selectFrom(DEVICES)
                .where(DEVICES.USER_ID.eq(userId))
                .and(DEVICES.DEVICE_FINGERPRINT.eq(fingerprint))
                .fetchOptional(this::map);
    }

    public Optional<DeviceRecord> findById(UUID id) {
        return dsl.selectFrom(DEVICES)
                .where(DEVICES.ID.eq(id))
                .fetchOptional(this::map);
    }

    public void markVerified(UUID id, Instant verifiedAt) {
        OffsetDateTime timestamp = toOffsetDateTime(verifiedAt);
        dsl.update(DEVICES)
                .set(DEVICES.STATUS, DeviceStatus.VERIFIED.name())
                .set(DEVICES.VERIFIED_AT, timestamp)
                .set(DEVICES.UPDATED_AT, timestamp)
                .where(DEVICES.ID.eq(id))
                .execute();
    }

    private DeviceRecord map(Record record) {
        return new DeviceRecord(
                record.get(DEVICES.ID),
                record.get(DEVICES.USER_ID),
                record.get(DEVICES.DEVICE_FINGERPRINT),
                record.get(DEVICES.DEVICE_NAME),
                record.get(DEVICES.OS),
                record.get(DEVICES.APP_VERSION),
                DeviceStatus.valueOf(record.get(DEVICES.STATUS)),
                toInstant(record.get(DEVICES.VERIFIED_AT)),
                record.get(DEVICES.CREATED_AT).toInstant(),
                record.get(DEVICES.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}

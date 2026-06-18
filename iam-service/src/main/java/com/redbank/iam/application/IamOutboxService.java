package com.redbank.iam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.iam.domain.DeviceRecord;
import com.redbank.iam.domain.OutboxEventRecord;
import com.redbank.iam.domain.OutboxEventStatus;
import com.redbank.iam.domain.UserRecord;
import com.redbank.iam.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

@Service
public class IamOutboxService {
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String DEVICE_VERIFIED = "DEVICE_VERIFIED";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public IamOutboxService(
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public void enqueueUserRegistered(UserRecord user) {
        enqueue(
                "USER",
                user.id().toString(),
                USER_REGISTERED,
                new UserRegisteredPayload(user.id(), user.selectedJobId(), user.createdAt())
        );
    }

    public void enqueueDeviceVerified(DeviceRecord device) {
        enqueue(
                "DEVICE",
                device.id().toString(),
                DEVICE_VERIFIED,
                new DeviceVerifiedPayload(device.userId(), device.id(), device.verifiedAt())
        );
    }

    private void enqueue(String aggregateType, String aggregateId, String eventType, Object payload) {
        Instant now = clock.instant();
        outboxEventRepository.save(new OutboxEventRecord(
                UUID.randomUUID(),
                aggregateType,
                aggregateId,
                eventType,
                serialize(payload),
                OutboxEventStatus.PENDING,
                now,
                now,
                null,
                0
        ));
        recordEnqueued(eventType);
    }

    private void recordEnqueued(String eventType) {
        meterRegistry.counter(
                "redbank.outbox.enqueued",
                "producer",
                "iam",
                "event.type",
                eventType
        ).increment();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("IAM outbox payload could not be created", exception);
        }
    }

    private record UserRegisteredPayload(UUID userId, String selectedJobId, Instant registeredAt) {
    }

    private record DeviceVerifiedPayload(UUID userId, UUID deviceId, Instant verifiedAt) {
    }
}

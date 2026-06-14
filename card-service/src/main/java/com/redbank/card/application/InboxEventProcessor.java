package com.redbank.card.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.card.infrastructure.InboxEventRepository;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxEventProcessor {
    private final InboxEventRepository inboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public InboxEventProcessor(InboxEventRepository inboxEventRepository, ObjectMapper objectMapper, Clock clock) {
        this.inboxEventRepository = inboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public <T> T process(
            UUID eventId,
            String eventType,
            String consumerName,
            Class<T> responseType,
            Supplier<T> handler
    ) {
        if (inboxEventRepository.claim(eventId, eventType, consumerName, clock.instant())) {
            T result = handler.get();
            inboxEventRepository.markProcessed(
                    eventId,
                    consumerName,
                    serialize(result),
                    clock.instant()
            );
            return result;
        }
        String result = inboxEventRepository.findProcessedResult(eventId, eventType, consumerName)
                .orElseThrow(() -> new CardConflictException("Event is already being processed"));
        return deserialize(result, responseType);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Inbox result could not be serialized", exception);
        }
    }

    private <T> T deserialize(String value, Class<T> responseType) {
        try {
            return objectMapper.readValue(value, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Inbox result could not be deserialized", exception);
        }
    }
}

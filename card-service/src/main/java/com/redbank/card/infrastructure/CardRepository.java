package com.redbank.card.infrastructure;

import com.redbank.card.domain.CardRecord;
import com.redbank.card.domain.CardStatusHistoryRecord;
import com.redbank.contracts.card.CardForm;
import com.redbank.contracts.card.CardStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.card.jooq.tables.CardStatusHistory.CARD_STATUS_HISTORY;
import static com.redbank.card.jooq.tables.Cards.CARDS;

@Repository
public class CardRepository {
    private final DSLContext dsl;

    public CardRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(CardRecord card) {
        dsl.insertInto(CARDS)
                .set(CARDS.ID, card.id())
                .set(CARDS.USER_ID, card.userId())
                .set(CARDS.PROFILE_ID, card.profileId())
                .set(CARDS.ACCOUNT_NUMBER, card.accountNumber())
                .set(CARDS.MASKED_PAN, card.maskedPan())
                .set(CARDS.CARD_TOKEN, card.cardToken())
                .set(CARDS.FORM, card.form().name())
                .set(CARDS.STATUS, card.status().name())
                .set(CARDS.EXPIRES_AT, card.expiresAt().atDay(1))
                .set(CARDS.ISSUANCE_REFERENCE, card.issuanceReference())
                .set(CARDS.CREATED_AT, toOffsetDateTime(card.createdAt()))
                .set(CARDS.UPDATED_AT, toOffsetDateTime(card.updatedAt()))
                .execute();
    }

    public Optional<CardRecord> findByIssuanceReference(String issuanceReference) {
        return dsl.selectFrom(CARDS)
                .where(CARDS.ISSUANCE_REFERENCE.eq(issuanceReference))
                .fetchOptional(this::map);
    }

    public Optional<CardRecord> findById(UUID cardId) {
        return dsl.selectFrom(CARDS)
                .where(CARDS.ID.eq(cardId))
                .fetchOptional(this::map);
    }

    public Optional<CardRecord> findByIdAndUserId(UUID cardId, UUID userId) {
        return dsl.selectFrom(CARDS)
                .where(CARDS.ID.eq(cardId))
                .and(CARDS.USER_ID.eq(userId))
                .fetchOptional(this::map);
    }

    public Optional<CardRecord> findDigitalByUserIdAndProfileIdAndAccountNumber(
            UUID userId,
            UUID profileId,
            String accountNumber
    ) {
        return dsl.selectFrom(CARDS)
                .where(CARDS.USER_ID.eq(userId))
                .and(CARDS.PROFILE_ID.eq(profileId))
                .and(CARDS.ACCOUNT_NUMBER.eq(accountNumber))
                .and(CARDS.FORM.eq(CardForm.DIGITAL.name()))
                .and(CARDS.STATUS.eq(CardStatus.ACTIVE.name()))
                .orderBy(CARDS.CREATED_AT.asc())
                .limit(1)
                .fetchOptional(this::map);
    }

    public List<CardRecord> findByAccountNumber(String accountNumber) {
        return dsl.selectFrom(CARDS)
                .where(CARDS.ACCOUNT_NUMBER.eq(accountNumber))
                .orderBy(CARDS.CREATED_AT.asc())
                .fetch(this::map);
    }

    public List<CardRecord> findByUserIdAndProfileId(
            UUID userId,
            UUID profileId,
            List<CardStatus> statuses,
            String accountNumber
    ) {
        // TODO(phase-5-learning): build the filtered SQL query and map the matching card rows.
        throw new UnsupportedOperationException("TODO: implement filtered card database query");
    }

    public boolean updateStatus(UUID cardId, CardStatus expectedStatus, CardStatus newStatus, Instant updatedAt) {
        int updated = dsl.update(CARDS)
                .set(CARDS.STATUS, newStatus.name())
                .set(CARDS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(CARDS.ID.eq(cardId))
                .and(CARDS.STATUS.eq(expectedStatus.name()))
                .execute();
        return updated == 1;
    }

    public boolean updateForm(UUID cardId, CardForm expectedForm, CardForm newForm, Instant updatedAt) {
        int updated = dsl.update(CARDS)
                .set(CARDS.FORM, newForm.name())
                .set(CARDS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(CARDS.ID.eq(cardId))
                .and(CARDS.FORM.eq(expectedForm.name()))
                .execute();
        return updated == 1;
    }

    public void saveStatusHistory(CardStatusHistoryRecord history) {
        dsl.insertInto(CARD_STATUS_HISTORY)
                .set(CARD_STATUS_HISTORY.ID, history.id())
                .set(CARD_STATUS_HISTORY.CARD_ID, history.cardId())
                .set(CARD_STATUS_HISTORY.USER_ID, history.userId())
                .set(CARD_STATUS_HISTORY.PROFILE_ID, history.profileId())
                .set(
                        CARD_STATUS_HISTORY.PREVIOUS_STATUS,
                        history.previousStatus() == null ? null : history.previousStatus().name()
                )
                .set(CARD_STATUS_HISTORY.NEW_STATUS, history.newStatus().name())
                .set(CARD_STATUS_HISTORY.REASON, history.reason())
                .set(CARD_STATUS_HISTORY.ACTOR_TYPE, history.actorType().name())
                .set(CARD_STATUS_HISTORY.ACTOR_ID, history.actorId())
                .set(CARD_STATUS_HISTORY.CREATED_AT, toOffsetDateTime(history.createdAt()))
                .execute();
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private CardRecord map(Record record) {
        return new CardRecord(
                record.get(CARDS.ID),
                record.get(CARDS.USER_ID),
                record.get(CARDS.PROFILE_ID),
                record.get(CARDS.ACCOUNT_NUMBER),
                record.get(CARDS.MASKED_PAN),
                record.get(CARDS.CARD_TOKEN),
                CardForm.valueOf(record.get(CARDS.FORM)),
                CardStatus.valueOf(record.get(CARDS.STATUS)),
                YearMonth.from(record.get(CARDS.EXPIRES_AT)),
                record.get(CARDS.ISSUANCE_REFERENCE),
                record.get(CARDS.CREATED_AT).toInstant(),
                record.get(CARDS.UPDATED_AT).toInstant()
        );
    }
}

package com.redbank.transfer.infrastructure;

import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;
import com.redbank.contracts.transfer.TransferStatus;
import com.redbank.transfer.domain.TransferRecord;
import com.redbank.transfer.domain.TransferStatusHistoryRecord;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.transfer.jooq.tables.TransferStatusHistory.TRANSFER_STATUS_HISTORY;
import static com.redbank.transfer.jooq.tables.Transfers.TRANSFERS;

@Repository
public class TransferRepository {
    private final DSLContext dsl;

    public TransferRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void save(TransferRecord transfer) {
        dsl.insertInto(TRANSFERS)
                .set(TRANSFERS.ID, transfer.id())
                .set(TRANSFERS.REQUEST_ID, transfer.requestId())
                .set(TRANSFERS.USER_ID, transfer.userId())
                .set(TRANSFERS.PROFILE_ID, transfer.profileId())
                .set(TRANSFERS.SOURCE_TYPE, transfer.sourceType().name())
                .set(TRANSFERS.SOURCE_REFERENCE, transfer.sourceReference())
                .set(TRANSFERS.SOURCE_ACCOUNT_NUMBER, transfer.sourceAccountNumber())
                .set(TRANSFERS.DESTINATION_TYPE, transfer.destinationType().name())
                .set(TRANSFERS.DESTINATION_REFERENCE, transfer.destinationReference())
                .set(TRANSFERS.DESTINATION_ACCOUNT_NUMBER, transfer.destinationAccountNumber())
                .set(TRANSFERS.DESTINATION_DISPLAY_NAME, transfer.destinationDisplayName())
                .set(TRANSFERS.DESTINATION_ACCOUNT_OR_CARD_NUMBER, transfer.destinationAccountOrCardNumber())
                .set(TRANSFERS.AMOUNT, transfer.amount())
                .set(TRANSFERS.CURRENCY, transfer.currency())
                .set(TRANSFERS.METHOD, transfer.method().name())
                .set(TRANSFERS.STATUS, transfer.status().name())
                .set(TRANSFERS.LEDGER_TRANSACTION_ID, transfer.ledgerTransactionId())
                .set(TRANSFERS.DESCRIPTION, transfer.description())
                .set(TRANSFERS.FAILURE_REASON, transfer.failureReason())
                .set(TRANSFERS.CREATED_AT, toOffsetDateTime(transfer.createdAt()))
                .set(TRANSFERS.UPDATED_AT, toOffsetDateTime(transfer.updatedAt()))
                .execute();
    }

    public Optional<TransferRecord> findByIdAndUserId(UUID transferId, UUID userId) {
        return dsl.selectFrom(TRANSFERS)
                .where(TRANSFERS.ID.eq(transferId))
                .and(TRANSFERS.USER_ID.eq(userId))
                .fetchOptional(this::map);
    }

    public boolean updateStatus(
            UUID transferId,
            TransferStatus expectedStatus,
            TransferStatus newStatus,
            String ledgerTransactionId,
            String failureReason,
            Instant updatedAt
    ) {
        int updated = dsl.update(TRANSFERS)
                .set(TRANSFERS.STATUS, newStatus.name())
                .set(TRANSFERS.LEDGER_TRANSACTION_ID, ledgerTransactionId)
                .set(TRANSFERS.FAILURE_REASON, failureReason)
                .set(TRANSFERS.UPDATED_AT, toOffsetDateTime(updatedAt))
                .where(TRANSFERS.ID.eq(transferId))
                .and(TRANSFERS.STATUS.eq(expectedStatus.name()))
                .execute();
        return updated == 1;
    }

    public List<TransferRecord> findSuccessfulRecent(UUID userId, UUID profileId, int candidateLimit) {
        return dsl.selectFrom(TRANSFERS)
                .where(TRANSFERS.USER_ID.eq(userId))
                .and(TRANSFERS.PROFILE_ID.eq(profileId))
                .and(TRANSFERS.STATUS.eq(TransferStatus.SUCCESS.name()))
                .orderBy(TRANSFERS.CREATED_AT.desc())
                .limit(candidateLimit)
                .fetch(this::map);
    }

    public void saveStatusHistory(TransferStatusHistoryRecord history) {
        dsl.insertInto(TRANSFER_STATUS_HISTORY)
                .set(TRANSFER_STATUS_HISTORY.ID, history.id())
                .set(TRANSFER_STATUS_HISTORY.TRANSFER_ID, history.transferId())
                .set(
                        TRANSFER_STATUS_HISTORY.PREVIOUS_STATUS,
                        history.previousStatus() == null ? null : history.previousStatus().name()
                )
                .set(TRANSFER_STATUS_HISTORY.NEW_STATUS, history.newStatus().name())
                .set(TRANSFER_STATUS_HISTORY.REASON, history.reason())
                .set(TRANSFER_STATUS_HISTORY.CREATED_AT, toOffsetDateTime(history.createdAt()))
                .execute();
    }

    private TransferRecord map(Record record) {
        return new TransferRecord(
                record.get(TRANSFERS.ID),
                record.get(TRANSFERS.REQUEST_ID),
                record.get(TRANSFERS.USER_ID),
                record.get(TRANSFERS.PROFILE_ID),
                DestinationType.valueOf(record.get(TRANSFERS.SOURCE_TYPE)),
                record.get(TRANSFERS.SOURCE_REFERENCE),
                record.get(TRANSFERS.SOURCE_ACCOUNT_NUMBER),
                DestinationType.valueOf(record.get(TRANSFERS.DESTINATION_TYPE)),
                record.get(TRANSFERS.DESTINATION_REFERENCE),
                record.get(TRANSFERS.DESTINATION_ACCOUNT_NUMBER),
                record.get(TRANSFERS.DESTINATION_DISPLAY_NAME),
                record.get(TRANSFERS.DESTINATION_ACCOUNT_OR_CARD_NUMBER),
                record.get(TRANSFERS.AMOUNT),
                record.get(TRANSFERS.CURRENCY),
                TransferMethod.valueOf(record.get(TRANSFERS.METHOD)),
                TransferStatus.valueOf(record.get(TRANSFERS.STATUS)),
                record.get(TRANSFERS.LEDGER_TRANSACTION_ID),
                record.get(TRANSFERS.DESCRIPTION),
                record.get(TRANSFERS.FAILURE_REASON),
                record.get(TRANSFERS.CREATED_AT).toInstant(),
                record.get(TRANSFERS.UPDATED_AT).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}

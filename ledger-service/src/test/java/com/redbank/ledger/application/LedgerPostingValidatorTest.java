package com.redbank.ledger.application;

import com.redbank.contracts.ledger.LedgerDirection;
import com.redbank.ledger.domain.LedgerEntryRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerPostingValidatorTest {
    private final LedgerPostingValidator validator = new LedgerPostingValidator();

    @Test
    void acceptsBalancedMultiEntryPosting() {
        UUID transactionId = UUID.randomUUID();

        assertThatNoException().isThrownBy(() -> validator.validate(List.of(
                entry(transactionId, LedgerDirection.DEBIT, "60.00", "IRR"),
                entry(transactionId, LedgerDirection.DEBIT, "40.00", "IRR"),
                entry(transactionId, LedgerDirection.CREDIT, "25.00", "IRR"),
                entry(transactionId, LedgerDirection.CREDIT, "75.00", "IRR")
        )));
    }

    @Test
    void rejectsUnbalancedPosting() {
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.validate(List.of(
                entry(transactionId, LedgerDirection.DEBIT, "100.00", "IRR"),
                entry(transactionId, LedgerDirection.CREDIT, "99.99", "IRR")
        )))
                .isInstanceOf(LedgerBusinessRuleException.class)
                .hasMessage("Ledger debits and credits must balance");
    }

    @Test
    void rejectsMixedCurrencies() {
        UUID transactionId = UUID.randomUUID();

        assertThatThrownBy(() -> validator.validate(List.of(
                entry(transactionId, LedgerDirection.DEBIT, "100.00", "IRR"),
                entry(transactionId, LedgerDirection.CREDIT, "100.00", "USD")
        )))
                .isInstanceOf(LedgerBusinessRuleException.class)
                .hasMessage("Ledger transaction cannot mix currencies");
    }

    @Test
    void rejectsEntriesFromDifferentTransactions() {
        assertThatThrownBy(() -> validator.validate(List.of(
                entry(UUID.randomUUID(), LedgerDirection.DEBIT, "100.00", "IRR"),
                entry(UUID.randomUUID(), LedgerDirection.CREDIT, "100.00", "IRR")
        )))
                .isInstanceOf(LedgerBusinessRuleException.class)
                .hasMessage("Ledger entries must belong to the same transaction");
    }

    private LedgerEntryRecord entry(
            UUID transactionId,
            LedgerDirection direction,
            String amount,
            String currency
    ) {
        return new LedgerEntryRecord(
                UUID.randomUUID(),
                transactionId,
                UUID.randomUUID(),
                direction,
                new BigDecimal(amount),
                currency,
                Instant.now()
        );
    }
}

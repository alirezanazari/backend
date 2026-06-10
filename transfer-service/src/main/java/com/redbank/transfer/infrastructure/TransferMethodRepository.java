package com.redbank.transfer.infrastructure;

import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;
import com.redbank.transfer.domain.TransferMethodRecord;
import java.util.List;
import java.util.Optional;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

import static com.redbank.transfer.jooq.tables.TransferMethods.TRANSFER_METHODS;

@Repository
public class TransferMethodRepository {
    private final DSLContext dsl;

    public TransferMethodRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<TransferMethodRecord> find(DestinationType sourceType, DestinationType destinationType) {
        Condition condition = DSL.noCondition();
        if (sourceType != null) {
            condition = condition.and(TRANSFER_METHODS.SOURCE_TYPE.eq(sourceType.name()));
        }
        if (destinationType != null) {
            condition = condition.and(TRANSFER_METHODS.DESTINATION_TYPE.eq(destinationType.name()));
        }
        return dsl.selectFrom(TRANSFER_METHODS)
                .where(condition)
                .orderBy(TRANSFER_METHODS.SORT_ORDER.asc())
                .fetch(this::map);
    }

    public Optional<TransferMethodRecord> findByMethod(TransferMethod method) {
        return dsl.selectFrom(TRANSFER_METHODS)
                .where(TRANSFER_METHODS.METHOD.eq(method.name()))
                .fetchOptional(this::map);
    }

    private TransferMethodRecord map(Record record) {
        return new TransferMethodRecord(
                TransferMethod.valueOf(record.get(TRANSFER_METHODS.METHOD)),
                DestinationType.valueOf(record.get(TRANSFER_METHODS.SOURCE_TYPE)),
                DestinationType.valueOf(record.get(TRANSFER_METHODS.DESTINATION_TYPE)),
                record.get(TRANSFER_METHODS.TITLE),
                Boolean.TRUE.equals(record.get(TRANSFER_METHODS.ENABLED)),
                record.get(TRANSFER_METHODS.SORT_ORDER)
        );
    }
}

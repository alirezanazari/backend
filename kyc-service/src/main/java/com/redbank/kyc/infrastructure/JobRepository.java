package com.redbank.kyc.infrastructure;

import com.redbank.kyc.domain.JobRecord;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import static com.redbank.kyc.jooq.tables.Jobs.JOBS;

@Repository
public class JobRepository {
    private final DSLContext dsl;

    public JobRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public List<JobRecord> findActive() {
        return dsl.selectFrom(JOBS)
                .where(JOBS.IS_ACTIVE.isTrue())
                .orderBy(JOBS.TITLE.asc(), JOBS.ID.asc())
                .fetch(this::map);
    }

    private JobRecord map(Record record) {
        return new JobRecord(
                record.get(JOBS.ID),
                record.get(JOBS.CODE),
                record.get(JOBS.TITLE),
                Boolean.TRUE.equals(record.get(JOBS.IS_ACTIVE)),
                record.get(JOBS.CREATED_AT).toInstant(),
                record.get(JOBS.UPDATED_AT).toInstant()
        );
    }
}

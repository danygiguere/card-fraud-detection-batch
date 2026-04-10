package com.example.batch.listener;

import com.example.batch.writer.ClassifiedTransactionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.jdbc.core.JdbcTemplate;

public class JobCompletionListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(JobCompletionListener.class);

    private final JdbcTemplate jdbcTemplate;
    private final ClassifiedTransactionWriter writer;

    public JobCompletionListener(JdbcTemplate jdbcTemplate, ClassifiedTransactionWriter writer) {
        this.jdbcTemplate = jdbcTemplate;
        this.writer = writer;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        log.info("========================================");
        log.info("  FRAUD DETECTION BATCH JOB STARTING");
        log.info("========================================");
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        long durationMs = jobExecution.getEndTime() != null && jobExecution.getStartTime() != null
                ? java.time.Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime()).toMillis()
                : 0;

        Integer dbCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verified_transactions", Integer.class);

        log.info("========================================");
        log.info("  FRAUD DETECTION BATCH JOB COMPLETE");
        log.info("========================================");
        log.info("  Status:               {}", jobExecution.getStatus());
        log.info("  Duration:             {} ms", durationMs);
        log.info("  Verified (DB):        {}", dbCount);
        log.info("  Flagged (File):       {}", writer.getFlaggedCount());
        log.info("  Total Processed:      {}", writer.getMatchedCount() + writer.getFlaggedCount());
        log.info("========================================");

        if (jobExecution.getStatus() == BatchStatus.FAILED) {
            log.error("Job failed with exceptions:");
            jobExecution.getAllFailureExceptions()
                    .forEach(ex -> log.error("  - {}", ex.getMessage()));
        }
    }
}

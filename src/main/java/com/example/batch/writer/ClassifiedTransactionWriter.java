package com.example.batch.writer;

import com.example.batch.model.ProcessedTransaction;
import com.example.batch.model.VerifiedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class ClassifiedTransactionWriter implements ItemWriter<ProcessedTransaction> {

    private static final Logger log = LoggerFactory.getLogger(ClassifiedTransactionWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final Path flaggedOutputPath;
    private final AtomicBoolean headerWritten = new AtomicBoolean(false);
    private final AtomicInteger matchedCount = new AtomicInteger(0);
    private final AtomicInteger flaggedCount = new AtomicInteger(0);

    public ClassifiedTransactionWriter(JdbcTemplate jdbcTemplate, String flaggedOutputFile) {
        this.jdbcTemplate = jdbcTemplate;
        this.flaggedOutputPath = Path.of(flaggedOutputFile);
    }

    @Override
    public void write(Chunk<? extends ProcessedTransaction> chunk) throws Exception {
        ensureOutputDirectory();

        var matched = chunk.getItems().stream()
                .filter(ProcessedTransaction::isMatched)
                .map(ProcessedTransaction::verified)
                .toList();

        var flagged = chunk.getItems().stream()
                .filter(p -> !p.isMatched())
                .map(ProcessedTransaction::flagged)
                .toList();

        if (!matched.isEmpty()) {
            batchInsertToDatabase(matched);
            matchedCount.addAndGet(matched.size());
        }

        if (!flagged.isEmpty()) {
            batchWriteToFlaggedFile(flagged);
            flaggedCount.addAndGet(flagged.size());
        }
    }

    private void batchInsertToDatabase(java.util.List<VerifiedTransaction> items) {
        jdbcTemplate.batchUpdate("""
                INSERT INTO verified_transactions
                    (transaction_id, card_fingerprint, encrypted_card_number, cardholder_name,
                     amount, merchant, transaction_timestamp, verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                items,
                items.size(),
                (ps, vt) -> {
                    ps.setString(1, vt.transactionId());
                    ps.setString(2, vt.cardFingerprint());
                    ps.setString(3, vt.encryptedCardNumber());
                    ps.setString(4, vt.cardholderName());
                    ps.setBigDecimal(5, vt.amount());
                    ps.setString(6, vt.merchant());
                    ps.setObject(7, vt.transactionTimestamp());
                    ps.setObject(8, vt.verifiedAt());
                }
        );
    }

    private synchronized void batchWriteToFlaggedFile(java.util.List<com.example.batch.model.Transaction> items) throws IOException {
        boolean writeHeader = headerWritten.compareAndSet(false, true);

        try (var writer = new PrintWriter(new FileWriter(flaggedOutputPath.toFile(), true))) {
            if (writeHeader) {
                writer.println("transaction_id,card_fingerprint,amount,merchant,timestamp");
            }
            for (var tx : items) {
                writer.printf("%s,%s,%s,%s,%s%n",
                        tx.transactionId(),
                        tx.cardFingerprint(),
                        tx.amount(),
                        tx.merchant(),
                        tx.timestamp()
                );
            }
        }
    }

    private void ensureOutputDirectory() throws IOException {
        Path parent = flaggedOutputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
    }

    public int getMatchedCount() {
        return matchedCount.get();
    }

    public int getFlaggedCount() {
        return flaggedCount.get();
    }

    public void resetCounters() {
        matchedCount.set(0);
        flaggedCount.set(0);
        headerWritten.set(false);
    }
}

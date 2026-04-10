package com.example.batch.model;

/**
 * Wrapper that holds either a verified (matched) or flagged (unmatched) transaction.
 */
public record ProcessedTransaction(
        VerifiedTransaction verified,
        Transaction flagged
) {

    public static ProcessedTransaction matched(VerifiedTransaction verified) {
        return new ProcessedTransaction(verified, null);
    }

    public static ProcessedTransaction unmatched(Transaction flagged) {
        return new ProcessedTransaction(null, flagged);
    }

    public boolean isMatched() {
        return verified != null;
    }
}

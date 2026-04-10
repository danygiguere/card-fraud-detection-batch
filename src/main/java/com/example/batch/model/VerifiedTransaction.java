package com.example.batch.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VerifiedTransaction(
        Long id,
        String transactionId,
        String cardFingerprint,
        String encryptedCardNumber,
        String cardholderName,
        BigDecimal amount,
        String merchant,
        LocalDateTime transactionTimestamp,
        LocalDateTime verifiedAt
) {

    public static VerifiedTransaction of(Transaction transaction, CardHolder cardHolder) {
        return new VerifiedTransaction(
                null,
                transaction.transactionId(),
                transaction.cardFingerprint(),
                cardHolder.encryptedCardNumber(),
                cardHolder.cardholderName(),
                transaction.amount(),
                transaction.merchant(),
                LocalDateTime.parse(transaction.timestamp()),
                LocalDateTime.now()
        );
    }
}

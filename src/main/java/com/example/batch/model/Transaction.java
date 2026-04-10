package com.example.batch.model;

public record Transaction(
        String transactionId,
        String cardFingerprint,
        java.math.BigDecimal amount,
        String merchant,
        String timestamp
) {}

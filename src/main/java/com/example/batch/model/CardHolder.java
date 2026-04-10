package com.example.batch.model;

public record CardHolder(
        String cardFingerprint,
        String encryptedCardNumber,
        String cardholderName
) {}

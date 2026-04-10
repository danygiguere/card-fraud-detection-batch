package com.example.batch.processor;

import com.example.batch.model.ProcessedTransaction;
import com.example.batch.model.Transaction;
import com.example.batch.model.VerifiedTransaction;
import com.example.batch.reader.CardHolderLookupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

public class FraudDetectionProcessor implements ItemProcessor<Transaction, ProcessedTransaction> {

    private static final Logger log = LoggerFactory.getLogger(FraudDetectionProcessor.class);

    private final CardHolderLookupService lookupService;

    public FraudDetectionProcessor(CardHolderLookupService lookupService) {
        this.lookupService = lookupService;
    }

    @Override
    public ProcessedTransaction process(Transaction transaction) {
        var cardHolder = lookupService.findByFingerprint(transaction.cardFingerprint());

        if (cardHolder != null) {
            log.debug("MATCH: Transaction {} matched cardholder {}", 
                    transaction.transactionId(), cardHolder.cardholderName());
            return ProcessedTransaction.matched(VerifiedTransaction.of(transaction, cardHolder));
        } else {
            log.debug("FLAGGED: Transaction {} - no matching cardholder for fingerprint {}", 
                    transaction.transactionId(), transaction.cardFingerprint());
            return ProcessedTransaction.unmatched(transaction);
        }
    }
}

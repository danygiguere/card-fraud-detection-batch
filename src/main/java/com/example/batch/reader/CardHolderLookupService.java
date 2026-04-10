package com.example.batch.reader;

import com.example.batch.model.CardHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Service
public class CardHolderLookupService {

    private static final Logger log = LoggerFactory.getLogger(CardHolderLookupService.class);

    @Value("${batch.cardholders-file}")
    private Resource cardholdersFile;

    private final Map<String, CardHolder> cardHolderMap = new HashMap<>();

    @PostConstruct
    public void loadCardHolders() throws Exception {
        try (var reader = new BufferedReader(
                new InputStreamReader(cardholdersFile.getInputStream(), StandardCharsets.UTF_8))) {

            reader.readLine(); // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",", 3);
                if (fields.length == 3) {
                    var cardHolder = new CardHolder(
                            fields[0].trim(),
                            fields[1].trim(),
                            fields[2].trim()
                    );
                    cardHolderMap.put(cardHolder.cardFingerprint(), cardHolder);
                }
            }
        }

        log.info("Loaded {} cardholders into lookup map", cardHolderMap.size());
    }

    public CardHolder findByFingerprint(String fingerprint) {
        return cardHolderMap.get(fingerprint);
    }

    public int size() {
        return cardHolderMap.size();
    }
}

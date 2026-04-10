# Card Fraud Detection Batch Pipeline

## Overview
A Spring Batch demo project for a bank presentation that processes card transaction fingerprints, matches them against a known cardholder database, and routes results accordingly. Uses Java 25, virtual threads, partitioned steps for parallel processing, and PostgreSQL.

## Architecture

```
transactions.csv ──► Partitioner ──► Partition 1 ──► Processor ──┬──► DB (verified_transactions)
                                 ├──► Partition 2 ──► Processor ──┤
                                 ├──► Partition 3 ──► Processor ──┤
                                 └──► Partition N ──► Processor ──┘──► flagged_transactions.csv
                                        ▲
                                        │
                                  cardholders.csv (lookup)
```

### Flow
1. **Read**: `transactions.csv` — each row has transaction ID, card fingerprint, amount, merchant, timestamp
2. **Lookup**: `cardholders.csv` loaded into memory as a `Map<fingerprint, CardHolder>` for fast matching
3. **Process**: Match each transaction fingerprint against cardholder map
4. **Write (classified)**:
   - ✅ Match → `verified_transactions` table in PostgreSQL
   - ❌ No match → `flagged_transactions.csv`

### Partitioning Strategy
- A custom `Partitioner` splits `transactions.csv` by line ranges
- Each partition is a separate `ExecutionContext` with start/end line numbers
- Virtual threads handle each partition via `VirtualThreadTaskExecutor`

## Tech Stack
- **Java 25** (Gradle toolchain)
- **Spring Boot** (latest stable) + **Spring Batch**
- **Gradle** (Kotlin DSL)
- **PostgreSQL** (local, JDBC) — `card-fraud-detection-batch`
- **Flyway** for DB migrations
- **Virtual threads** (`spring.threads.virtual.enabled=true`)

## Project Structure

```
spring-batch/
├── build.gradle.kts
├── settings.gradle.kts
├── src/
│   ├── main/
│   │   ├── java/com/example/batch/
│   │   │   ├── SpringBatchApplication.java
│   │   │   ├── config/
│   │   │   │   └── BatchConfig.java              # Job, step, partitioner, taskExecutor beans
│   │   │   ├── model/
│   │   │   │   ├── Transaction.java               # Input model
│   │   │   │   ├── CardHolder.java                 # Lookup model
│   │   │   │   └── VerifiedTransaction.java        # Output model (DB entity)
│   │   │   ├── processor/
│   │   │   │   └── FraudDetectionProcessor.java    # Fingerprint matching logic
│   │   │   ├── partitioner/
│   │   │   │   └── TransactionFilePartitioner.java # Splits CSV by line ranges
│   │   │   ├── reader/
│   │   │   │   └── CardHolderLookupService.java    # Loads cardholders.csv into memory
│   │   │   ├── writer/
│   │   │   │   └── ClassifiedTransactionWriter.java # Routes to DB or file
│   │   │   └── listener/
│   │   │       └── JobCompletionListener.java      # Logs summary stats
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── db/migration/
│   │       │   └── V1__create_verified_transactions.sql
│   │       └── data/
│   │           ├── transactions.csv
│   │           └── cardholders.csv
│   └── test/
│       ├── java/com/example/batch/
│       │   ├── SpringBatchApplicationTests.java
│       │   └── cucumber/
│       │       ├── CucumberIntegrationTest.java    # Cucumber runner
│       │       └── FraudDetectionSteps.java        # Step definitions
│       └── resources/
│           ├── features/
│           │   └── fraud-detection.feature          # BDD scenarios
│           └── application-test.yml                 # Testcontainers config
```

## Database

### Flyway Migration: `V1__create_verified_transactions.sql`
```sql
CREATE TABLE verified_transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(50) NOT NULL,
    card_fingerprint VARCHAR(128) NOT NULL,
    encrypted_card_number VARCHAR(256) NOT NULL,
    cardholder_name VARCHAR(100) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    merchant VARCHAR(100) NOT NULL,
    transaction_timestamp TIMESTAMP NOT NULL,
    verified_at TIMESTAMP DEFAULT NOW()
);
```

Spring Batch metadata tables will be auto-created by Spring Batch itself.

## Sample Data

### transactions.csv (~50 rows)
```
transaction_id,card_fingerprint,amount,merchant,timestamp
TXN-001,fp_a1b2c3d4e5,250.00,Amazon,2026-04-10T10:15:00
TXN-002,fp_x9y8z7w6v5,1200.50,BestBuy,2026-04-10T10:16:00
TXN-003,fp_unknown_01,99.99,ShadyShop,2026-04-10T10:17:00
...
```

### cardholders.csv
```
card_fingerprint,encrypted_card_number,cardholder_name
fp_a1b2c3d4e5,ENC_4111xxxx1234,Alice Johnson
fp_x9y8z7w6v5,ENC_5500xxxx5678,Bob Smith
...
```

## Configuration (application.yml)
```yaml
spring:
  application:
    name: card-fraud-detection-batch
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce_db
    username: postgres
    password: postgres
  batch:
    jdbc:
      initialize-schema: always
  flyway:
    baseline-on-migrate: false
    locations: classpath:db/migration
    url: jdbc:postgresql://localhost:5432/ecommerce_db
    user: postgres
    password: postgres
    clean-disabled: true
    out-of-order: false
  threads:
    virtual:
      enabled: true

batch:
  partition-count: 4
  chunk-size: 10
```

## Todos

1. **gradle-setup** — Create `build.gradle.kts` and `settings.gradle.kts` with Java 25 toolchain, Spring Boot, Spring Batch, PostgreSQL, Flyway dependencies
2. **app-config** — Create `application.yml` and `SpringBatchApplication.java`
3. **models** — Create `Transaction`, `CardHolder`, `VerifiedTransaction` model classes
4. **flyway-migration** — Create `V1__create_verified_transactions.sql`
5. **sample-data** — Generate `transactions.csv` and `cardholders.csv` with ~50 transactions, ~30 cardholders (some won't match)
6. **cardholder-lookup** — Create `CardHolderLookupService` to load cardholders into a `Map`
7. **partitioner** — Create `TransactionFilePartitioner` to split CSV by line ranges
8. **processor** — Create `FraudDetectionProcessor` with fingerprint matching logic
9. **writer** — Create `ClassifiedTransactionWriter` that routes matched to DB, unmatched to file
10. **batch-config** — Create `BatchConfig` with job, partitioned step, virtual thread executor
11. **listener** — Create `JobCompletionListener` for summary stats logging
12. **testcontainers-setup** — Add Testcontainers PostgreSQL dependency, create `application-test.yml` with dynamic container config
13. **cucumber-setup** — Add Cucumber dependencies, create feature file with BDD scenarios, runner class, and step definitions
14. **integration-test** — Wire Cucumber + Testcontainers together, verify job runs end-to-end against real PostgreSQL
15. **verify** — Build and verify the project compiles and all tests pass

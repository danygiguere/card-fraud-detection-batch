# Card Fraud Detection Batch Pipeline

A Spring Batch demo project showcasing modern Java batch processing for a banking fraud detection use case. Built with Java 25 virtual threads, partitioned parallel processing, and PostgreSQL.

---

## What It Does

The application processes a daily file of card transaction fingerprints and matches them against a known cardholder database to verify legitimate transactions.

```
transactions.csv ──► Partitioner ──► Partition 1 ──► Processor ──┬──► DB: verified_transactions
                                 ├──► Partition 2 ──► Processor ──┤
                                 ├──► Partition 3 ──► Processor ──┤
                                 └──► Partition 4 ──► Processor ──┘──► flagged_transactions.csv
                                            ▲
                                            │
                                      cardholders.csv (HashMap lookup)
```

- ✅ **Matched** transactions → saved to PostgreSQL (`verified_transactions` table)
- 🚩 **Unmatched** transactions → written to `output/flagged_transactions.csv` for review

---

## Tech Stack

| Technology | Version | Purpose |
|---|---|---|
| Java | 25 | Language + virtual threads (Project Loom) |
| Spring Boot | 3.4.4 | Application framework |
| Spring Batch | (via Boot) | Batch job orchestration |
| Gradle | 8.12 | Build tool (Kotlin DSL) |
| PostgreSQL | 17+ | Store verified transactions |
| Flyway | (via Boot) | Database migrations |
| Testcontainers | 1.20.4 | Real PostgreSQL in integration tests |
| Cucumber | 7.18.1 | BDD-style integration tests |

---

## Architecture Highlights

### Partitioned Parallel Processing
The input file is split into N partitions by line range. Each partition runs as an independent worker step on a **virtual thread**, processing chunks in parallel.

### Virtual Threads
Enabled globally via:
```yaml
spring:
  threads:
    virtual:
      enabled: true
```
Virtual threads (Project Loom) allow high concurrency with low overhead — ideal for I/O-bound batch jobs doing DB writes and file operations.

### HashMap Cardholder Lookup — O(1)
All 5,000 cardholders are loaded into a `HashMap<fingerprint, CardHolder>` at startup. Each transaction lookup is O(1) — no DB reads during processing.

### Batch Insert per Chunk
Matched transactions are inserted using `jdbcTemplate.batchUpdate()` — one DB round-trip per chunk of 1,000 rows instead of one per row.

---

## Prerequisites

- Java 25
- Docker (for running tests with Testcontainers)
- PostgreSQL running locally

### Database Setup

> **Note:** Flyway automatically runs migrations on startup (creates tables, indexes, etc.), but it cannot create the database itself — you need to do that once manually.

**Option 1 — Quick Docker setup (recommended)**

Use [@danygiguere/docker_db](https://github.com/danygiguere/docker_db) to spin up a PostgreSQL instance in seconds:
```bash
git clone https://github.com/danygiguere/docker_db.git
cd docker_db/postgres
docker-compose up -d
```
Then create the database:
```sql
CREATE DATABASE "card-fraud-detection-batch";
```

Flyway will handle the rest on first run.

---

## Running the Application

```bash
./gradlew bootRun
```

On completion, check:
- **Verified transactions** → PostgreSQL `verified_transactions` table
- **Flagged transactions** → `output/flagged_transactions.csv`

The job logs a summary on completion:
```
========================================
  FRAUD DETECTION BATCH JOB COMPLETE
========================================
  Status:               COMPLETED
  Duration:             1243 ms
  Verified (DB):        6987
  Flagged (File):       3013
  Total Processed:      10000
========================================
```

---

## Running Tests

```bash
./gradlew test
```

Tests use **Testcontainers** to spin up a real PostgreSQL instance automatically — no local DB required for tests.

### Test Scenarios (Cucumber BDD)

```gherkin
Scenario: Successfully process transactions with matching and non-matching fingerprints
Scenario: Job reports accurate processing statistics
Scenario: No records are lost across chunk boundaries
```

The chunk boundary test independently verifies:
```
DB count + flagged file lines = total input lines
```

---

## Configuration

Key settings in `src/main/resources/application.yml`:

```yaml
batch:
  partition-count: 4    # Number of parallel partitions
  chunk-size: 1000      # Rows per chunk (batch insert size)
  input-file: classpath:data/transactions.csv
  cardholders-file: classpath:data/cardholders.csv
  flagged-output-file: output/flagged_transactions.csv
```

---

## Sample Data

| File | Rows | Description |
|---|---|---|
| `transactions.csv` | 10,000 | Daily card transactions with fingerprints |
| `cardholders.csv` | 5,000 | Known cardholders with encrypted card numbers |

~70% of transactions match a known cardholder. The remaining ~30% are flagged as suspicious.

---

## Project Structure

```
src/
├── main/java/com/example/batch/
│   ├── config/BatchConfig.java              # Job, steps, partitioner, virtual thread executor
│   ├── model/                               # Transaction, CardHolder, VerifiedTransaction
│   ├── partitioner/TransactionFilePartitioner.java
│   ├── processor/FraudDetectionProcessor.java
│   ├── reader/CardHolderLookupService.java  # HashMap-based O(1) lookup
│   ├── writer/ClassifiedTransactionWriter.java
│   └── listener/JobCompletionListener.java
├── main/resources/
│   ├── application.yml
│   ├── db/migration/V1__create_verified_transactions.sql
│   └── data/                                # transactions.csv, cardholders.csv
└── test/
    ├── java/.../cucumber/                   # Cucumber step definitions + runner
    └── resources/features/fraud-detection.feature
```

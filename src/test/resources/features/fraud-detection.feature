Feature: Card Fraud Detection Batch Processing

  As a bank fraud detection system
  I want to process card transaction fingerprints against known cardholders
  So that verified transactions are saved and suspicious ones are flagged

  Scenario: Successfully process transactions with matching and non-matching fingerprints
    Given the fraud detection batch job is configured
    And transactions and cardholder data files are available
    When the fraud detection job runs
    Then the job completes successfully
    And verified transactions are saved to the database
    And unmatched transactions are written to the flagged file

  Scenario: Job reports accurate processing statistics
    Given the fraud detection batch job is configured
    And transactions and cardholder data files are available
    When the fraud detection job runs
    Then the job completes successfully
    And the total processed count equals the number of input transactions

  Scenario: No records are lost across chunk boundaries
    Given the fraud detection batch job is configured
    And transactions and cardholder data files are available
    When the fraud detection job runs
    Then the job completes successfully
    And the sum of verified in database and flagged in file equals total input transactions

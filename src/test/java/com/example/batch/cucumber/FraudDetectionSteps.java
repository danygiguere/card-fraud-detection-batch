package com.example.batch.cucumber;

import com.example.batch.writer.ClassifiedTransactionWriter;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class FraudDetectionSteps {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private Job fraudDetectionJob;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClassifiedTransactionWriter classifiedTransactionWriter;

    @Value("${batch.input-file}")
    private Resource inputFile;

    @Value("${batch.cardholders-file}")
    private Resource cardholdersFile;

    @Value("${batch.flagged-output-file}")
    private String flaggedOutputFile;

    private JobExecution jobExecution;

    @Before
    public void setUp() {
        jobLauncherTestUtils.setJob(fraudDetectionJob);
        jdbcTemplate.execute("DELETE FROM verified_transactions");
        classifiedTransactionWriter.resetCounters();

        Path flaggedPath = Path.of(flaggedOutputFile);
        try {
            Files.deleteIfExists(flaggedPath);
        } catch (Exception ignored) {}
    }

    @Given("the fraud detection batch job is configured")
    public void theFraudDetectionBatchJobIsConfigured() {
        assertThat(fraudDetectionJob).isNotNull();
        assertThat(fraudDetectionJob.getName()).isEqualTo("fraudDetectionJob");
    }

    @And("transactions and cardholder data files are available")
    public void transactionsAndCardholderDataFilesAreAvailable() throws Exception {
        assertThat(inputFile.exists()).isTrue();
        assertThat(cardholdersFile.exists()).isTrue();
    }

    @When("the fraud detection job runs")
    public void theFraudDetectionJobRuns() throws Exception {
        jobExecution = jobLauncherTestUtils.launchJob();
    }

    @Then("the job completes successfully")
    public void theJobCompletesSuccessfully() {
        assertThat(jobExecution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
    }

    @And("verified transactions are saved to the database")
    public void verifiedTransactionsAreSavedToTheDatabase() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verified_transactions", Integer.class);
        assertThat(count).isGreaterThan(0);
    }

    @And("unmatched transactions are written to the flagged file")
    public void unmatchedTransactionsAreWrittenToTheFlaggedFile() {
        Path flaggedPath = Path.of(flaggedOutputFile);
        assertThat(flaggedPath).exists();
        assertThat(classifiedTransactionWriter.getFlaggedCount()).isGreaterThan(0);
    }

    @And("the total processed count equals the number of input transactions")
    public void theTotalProcessedCountEqualsTheNumberOfInputTransactions() throws Exception {
        long inputLines;
        try (var lines = Files.lines(Path.of(inputFile.getURI()))) {
            inputLines = lines.count() - 1; // minus header
        }

        int totalProcessed = classifiedTransactionWriter.getMatchedCount()
                + classifiedTransactionWriter.getFlaggedCount();

        assertThat(totalProcessed).isEqualTo((int) inputLines);
    }

    @And("the sum of verified in database and flagged in file equals total input transactions")
    public void theSumOfVerifiedInDatabaseAndFlaggedInFileEqualsTotalInputTransactions() throws Exception {
        // Count total input lines (ground truth)
        long totalInput;
        try (var lines = Files.lines(Path.of(inputFile.getURI()))) {
            totalInput = lines.count() - 1; // minus header
        }

        // Count from DB (independent of in-memory counters)
        long verifiedInDb = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM verified_transactions", Long.class);

        // Count from flagged file (independent of in-memory counters)
        long flaggedInFile = 0;
        Path flaggedPath = Path.of(flaggedOutputFile);
        if (Files.exists(flaggedPath)) {
            try (var lines = Files.lines(flaggedPath)) {
                flaggedInFile = lines.count() - 1; // minus header
            }
        }

        assertThat(verifiedInDb + flaggedInFile)
                .as("DB verified (%d) + flagged file (%d) should equal total input (%d)",
                        verifiedInDb, flaggedInFile, totalInput)
                .isEqualTo(totalInput);
    }
}

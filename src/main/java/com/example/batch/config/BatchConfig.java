package com.example.batch.config;

import com.example.batch.listener.JobCompletionListener;
import com.example.batch.model.ProcessedTransaction;
import com.example.batch.model.Transaction;
import com.example.batch.partitioner.TransactionFilePartitioner;
import com.example.batch.processor.FraudDetectionProcessor;
import com.example.batch.reader.CardHolderLookupService;
import com.example.batch.writer.ClassifiedTransactionWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.file.mapping.FieldSetMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    @Value("${batch.partition-count}")
    private int partitionCount;

    @Value("${batch.chunk-size}")
    private int chunkSize;

    @Value("${batch.input-file}")
    private Resource inputFile;

    @Value("${batch.flagged-output-file}")
    private String flaggedOutputFile;

    @Bean
    public TaskExecutor batchTaskExecutor() {
        return new VirtualThreadTaskExecutor("fraud-detection-");
    }

    @Bean
    public Partitioner transactionPartitioner() {
        return new TransactionFilePartitioner(inputFile);
    }

    @Bean
    public FieldSetMapper<Transaction> transactionFieldSetMapper() {
        return fieldSet -> new Transaction(
                fieldSet.readString("transaction_id"),
                fieldSet.readString("card_fingerprint"),
                fieldSet.readBigDecimal("amount"),
                fieldSet.readString("merchant"),
                fieldSet.readString("timestamp")
        );
    }

    @Bean
    public FraudDetectionProcessor fraudDetectionProcessor(CardHolderLookupService lookupService) {
        return new FraudDetectionProcessor(lookupService);
    }

    @Bean
    public ClassifiedTransactionWriter classifiedTransactionWriter(JdbcTemplate jdbcTemplate) {
        return new ClassifiedTransactionWriter(jdbcTemplate, flaggedOutputFile);
    }

    @Bean
    public Step partitionedStep(JobRepository jobRepository,
                                 Step workerStep,
                                 Partitioner transactionPartitioner,
                                 TaskExecutor batchTaskExecutor) {
        return new StepBuilder("partitionedStep", jobRepository)
                .partitioner("workerStep", transactionPartitioner)
                .step(workerStep)
                .gridSize(partitionCount)
                .taskExecutor(batchTaskExecutor)
                .build();
    }

    @Bean
    public Step workerStep(JobRepository jobRepository,
                           PlatformTransactionManager transactionManager,
                           FraudDetectionProcessor processor,
                           ClassifiedTransactionWriter writer) {
        return new StepBuilder("workerStep", jobRepository)
                .<Transaction, ProcessedTransaction>chunk(chunkSize, transactionManager)
                .reader(partitionedTransactionReader(0, 0))
                .processor(processor)
                .writer(writer)
                .build();
    }

    @Bean
    @StepScope
    public FlatFileItemReader<Transaction> partitionedTransactionReader(
            @Value("#{stepExecutionContext['startLine']}") int startLine,
            @Value("#{stepExecutionContext['endLine']}") int endLine) {

        return new FlatFileItemReaderBuilder<Transaction>()
                .name("transactionReader")
                .resource(inputFile)
                .linesToSkip(1 + startLine) // skip header + offset lines
                .maxItemCount(endLine - startLine)
                .delimited()
                .names("transaction_id", "card_fingerprint", "amount", "merchant", "timestamp")
                .fieldSetMapper(transactionFieldSetMapper())
                .build();
    }

    @Bean
    public JobCompletionListener jobCompletionListener(JdbcTemplate jdbcTemplate,
                                                        ClassifiedTransactionWriter writer) {
        return new JobCompletionListener(jdbcTemplate, writer);
    }

    @Bean
    public Job fraudDetectionJob(JobRepository jobRepository,
                                  Step partitionedStep,
                                  JobCompletionListener listener) {
        return new JobBuilder("fraudDetectionJob", jobRepository)
                .listener(listener)
                .start(partitionedStep)
                .build();
    }
}

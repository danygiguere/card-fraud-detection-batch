package com.example.batch.partitioner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TransactionFilePartitioner implements Partitioner {

    private static final Logger log = LoggerFactory.getLogger(TransactionFilePartitioner.class);

    private final Resource resource;

    public TransactionFilePartitioner(Resource resource) {
        this.resource = resource;
    }

    @Override
    public Map<String, ExecutionContext> partition(int gridSize) {
        int totalLines = countLines();
        int linesPerPartition = (totalLines + gridSize - 1) / gridSize;

        Map<String, ExecutionContext> partitions = new HashMap<>();

        for (int i = 0; i < gridSize; i++) {
            var context = new ExecutionContext();
            int startLine = i * linesPerPartition;
            int endLine = Math.min(startLine + linesPerPartition, totalLines);

            context.putInt("startLine", startLine);
            context.putInt("endLine", endLine);
            context.putString("partitionName", "partition-" + i);

            partitions.put("partition-" + i, context);

            log.info("Created partition-{}: lines {} to {}", i, startLine, endLine);
        }

        return partitions;
    }

    private int countLines() {
        try (var reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            reader.readLine(); // skip header
            int count = 0;
            while (reader.readLine() != null) {
                count++;
            }
            log.info("Total transaction lines: {}", count);
            return count;
        } catch (Exception e) {
            throw new RuntimeException("Failed to count lines in transactions file", e);
        }
    }
}

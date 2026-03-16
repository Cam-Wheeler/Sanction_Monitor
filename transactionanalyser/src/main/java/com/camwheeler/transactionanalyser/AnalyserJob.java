package com.camwheeler.transactionanalyser;

import com.camwheeler.transactionanalyser.function.AnthropicAnalysisFunction;
import com.camwheeler.transactionanalyser.model.AnalysisResult;
import com.camwheeler.transactionanalyser.model.FilterResult;
import com.camwheeler.transactionanalyser.serde.AnalysisResultSerializationSchema;
import com.camwheeler.transactionanalyser.serde.FilterResultDeserializationSchema;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class AnalyserJob {
    // Entry point for the Flink streaming job that analyses flagged transactions.

    private static final Logger LOG = LoggerFactory.getLogger(AnalyserJob.class);

    private static final String INPUT_TOPIC = "flagged-transactions-topic";
    private static final String OUTPUT_TOPIC = "analysis-results-topic";
    private static final String GROUP_ID = "transaction-analyser";

    /*
    Sets up and executes the Flink pipeline.
    Pipeline: KafkaSource -> AsyncDataStream (Anthropic API) -> KafkaSink.
    Reads KAFKA_BOOTSTRAP_SERVERS from environment, falls back to Docker Compose defaults.
    */
    public static void main(String[] args) throws Exception {
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            bootstrapServers = "kafka-1:9092,kafka-2:9092,kafka-3:9092";
        }

        LOG.info("Starting Transaction Analyser Job");
        LOG.info("Kafka bootstrap servers: {}", bootstrapServers);
        LOG.info("Input topic: {}", INPUT_TOPIC);
        LOG.info("Output topic: {}", OUTPUT_TOPIC);

        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        env.enableCheckpointing(60_000);

        // Source: consume FilterResult JSON from flagged-transactions-topic
        KafkaSource<FilterResult> source = KafkaSource.<FilterResult>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(INPUT_TOPIC)
                .setGroupId(GROUP_ID)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new FilterResultDeserializationSchema())
                .build();

        DataStream<FilterResult> flaggedTransactions = env
                .fromSource(source, WatermarkStrategy.noWatermarks(), "Kafka Source")
                .filter(FilterResult::flagged);

        // Async transform: call Anthropic API for each flagged transaction
        DataStream<AnalysisResult> analysisResults = AsyncDataStream.unorderedWait(
                flaggedTransactions,
                new AnthropicAnalysisFunction(),
                120,
                TimeUnit.SECONDS,
                5
        );

        // Sink: publish AnalysisResult JSON to analysis-results-topic
        KafkaSink<AnalysisResult> sink = KafkaSink.<AnalysisResult>builder()
                .setBootstrapServers(bootstrapServers)
                .setRecordSerializer(new AnalysisResultSerializationSchema(OUTPUT_TOPIC))
                .build();

        analysisResults.sinkTo(sink);

        env.execute("Transaction Analyser");
    }
}

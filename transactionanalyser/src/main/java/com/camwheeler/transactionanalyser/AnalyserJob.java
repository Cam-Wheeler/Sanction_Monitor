package com.camwheeler.transactionanalyser;

import com.camwheeler.transactionanalyser.function.AnthropicAnalysisFunction;
import com.camwheeler.transactionanalyser.function.FlaggedPartyKeySelector;
import com.camwheeler.transactionanalyser.function.TransactionEnrichmentFunction;
import com.camwheeler.transactionanalyser.model.AnalysisResult;
import com.camwheeler.transactionanalyser.model.EnrichedFilterResult;
import com.camwheeler.transactionanalyser.model.FilterResult;
import com.camwheeler.transactionanalyser.serde.AnalysisResultSerializationSchema;
import com.camwheeler.transactionanalyser.serde.FilterResultDeserializationSchema;
import com.camwheeler.transactionanalyser.util.TimestampUtils;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.AsyncDataStream;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;
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

        // Register Kryo serialiser for UUID to avoid KryoException on checkpoint
        env.getConfig().addDefaultKryoSerializer(UUID.class, UUIDSerializer.class);

        // Source: consume FilterResult JSON from flagged-transactions-topic
        KafkaSource<FilterResult> source = KafkaSource.<FilterResult>builder()
                .setBootstrapServers(bootstrapServers)
                .setTopics(INPUT_TOPIC)
                .setGroupId(GROUP_ID)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new FilterResultDeserializationSchema())
                .build();

        // Watermark strategy with bounded out-of-orderness for event-time processing
        WatermarkStrategy<FilterResult> watermarkStrategy = WatermarkStrategy
                .<FilterResult>forBoundedOutOfOrderness(Duration.ofMinutes(1))
                .withTimestampAssigner((event, recordTimestamp) ->
                        TimestampUtils.toEpochMillis(event.getTransaction().getDate(), event.getTransaction().getTime()));

        DataStream<FilterResult> flaggedTransactions = env
                .fromSource(source, watermarkStrategy, "Kafka Source")
                .filter(FilterResult::isFlagged);

        // Enrich each transaction with recent history for the same flagged party
        DataStream<EnrichedFilterResult> enrichedTransactions = flaggedTransactions
                .keyBy(new FlaggedPartyKeySelector())
                .process(new TransactionEnrichmentFunction());

        // Async transform: call Anthropic API for each enriched transaction
        DataStream<AnalysisResult> analysisResults = AsyncDataStream.unorderedWait(
                enrichedTransactions,
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

    public static class UUIDSerializer extends Serializer<UUID> {
        @Override
        public void write(Kryo kryo, Output output, UUID uuid) {
            output.writeLong(uuid.getMostSignificantBits());
            output.writeLong(uuid.getLeastSignificantBits());
        }

        @Override
        public UUID read(Kryo kryo, Input input, Class<UUID> type) {
            return new UUID(input.readLong(), input.readLong());
        }
    }
}

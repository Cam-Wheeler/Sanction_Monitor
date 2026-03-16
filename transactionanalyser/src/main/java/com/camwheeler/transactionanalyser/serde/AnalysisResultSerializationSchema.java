package com.camwheeler.transactionanalyser.serde;

import com.camwheeler.transactionanalyser.model.AnalysisResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;

public class AnalysisResultSerializationSchema implements KafkaRecordSerializationSchema<AnalysisResult> {
    // Serialises AnalysisResult to JSON and publishes to the analysis-results-topic.

    private final String topic;
    private transient ObjectMapper objectMapper;

    public AnalysisResultSerializationSchema(String topic) {
        this.topic = topic;
    }

    @Override
    public void open(SerializationSchema.InitializationContext context, KafkaSinkContext sinkContext) {
        objectMapper = new ObjectMapper();
    }

    @Override
    public ProducerRecord<byte[], byte[]> serialize(AnalysisResult element, KafkaSinkContext context, Long timestamp) {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }

        byte[] key = element.transactionId().toString().getBytes(StandardCharsets.UTF_8);
        byte[] value;

        try {
            value = objectMapper.writeValueAsBytes(element);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AnalysisResult", e);
        }

        return new ProducerRecord<>(topic, key, value);
    }
}

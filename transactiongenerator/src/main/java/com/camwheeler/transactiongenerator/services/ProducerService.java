package com.camwheeler.transactiongenerator.services;

import com.camwheeler.transactiongenerator.model.Transaction;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ProducerService {

    @SuppressWarnings("unused") // To stop the checker from moaning for now. `
    private final Properties kafkaProducerProperties;
    private final GeneratorService generatorService;
    private final KafkaProducer<String, String> producer;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topic-name}")
    private String topicName;

    public ProducerService(Properties kafkaProducerProperties, GeneratorService generatorService) {
        this.kafkaProducerProperties = kafkaProducerProperties;
        this.generatorService = generatorService;
        this.producer = new KafkaProducer<>(kafkaProducerProperties);
        this.objectMapper = new ObjectMapper();
    }

    /*
     Produces n number of transactions to the topic.
     Utilises GeneratorService to actually create the transactions.
     @param nTransactions the number of transactions to generate.
    */
    public void produceTransactions(int nTransactions) {
        for (int i = 0; i < nTransactions; i++) {
            try {
                Transaction transaction = generatorService.generateTransaction();
                String messageId = UUID.randomUUID().toString();
                String payload = objectMapper.writeValueAsString(transaction);

                producer.send(new ProducerRecord<>(topicName, messageId, payload), (recordMetadata, ex) -> {
                    if (ex != null) {
                        ex.printStackTrace();
                    } else {
                        System.out.printf("Sent transaction %s to partition %d at offset %d%n",
                                messageId, recordMetadata.partition(), recordMetadata.offset());
                    }
                }).get( 10000, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
                throw new RuntimeException("Failed to produce transaction", e);
            }
        }
    }

    public void produceTestSequence() {
        List<Transaction> transactions = generatorService.generateTestSequence();
        for (Transaction transaction : transactions) {
            try {
                String messageId = UUID.randomUUID().toString();
                String payload = objectMapper.writeValueAsString(transaction);

                producer.send(new ProducerRecord<>(topicName, messageId, payload), (recordMetadata, ex) -> {
                    if (ex != null) {
                        ex.printStackTrace();
                    } else {
                        System.out.printf("Sent test transaction %s to partition %d at offset %d%n",
                                messageId, recordMetadata.partition(), recordMetadata.offset());
                    }
                }).get(10000, TimeUnit.MILLISECONDS);

            } catch (Exception e) {
                throw new RuntimeException("Failed to produce test transaction", e);
            }
        }
    }
}
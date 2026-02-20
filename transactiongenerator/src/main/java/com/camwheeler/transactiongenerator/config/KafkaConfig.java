package com.camwheeler.transactiongenerator.config;

import java.util.Properties;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConfig {
    
    @Value("${kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${kafka.acks}")
    private String acks;

    @Value("${kafka.key-serializer}")
    private String keySerializer;

    @Value("${kafka.value-serializer}")
    private String valueSerializer;

    @Bean
    public Properties kafkaProducerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer);
        return props;
    }
}

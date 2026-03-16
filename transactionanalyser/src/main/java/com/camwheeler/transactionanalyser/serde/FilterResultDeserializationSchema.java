package com.camwheeler.transactionanalyser.serde;

import com.camwheeler.transactionanalyser.model.FilterResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;

import java.io.IOException;

public class FilterResultDeserializationSchema implements DeserializationSchema<FilterResult> {
    // Deserialises FilterResult JSON from the flagged-transactions-topic.

    private transient ObjectMapper objectMapper;

    @Override
    public void open(InitializationContext context) {
        objectMapper = new ObjectMapper();
    }

    @Override
    public FilterResult deserialize(byte[] message) throws IOException {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        return objectMapper.readValue(message, FilterResult.class);
    }

    @Override
    public boolean isEndOfStream(FilterResult nextElement) {
        return false;
    }

    @Override
    public TypeInformation<FilterResult> getProducedType() {
        return TypeInformation.of(FilterResult.class);
    }
}

#!/bin/bash

/opt/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092 \
--create --topic transactions-topic \
--partitions 3 \
--replication-factor 3 \
--if-not-exists

/opt/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092 \
--create --topic flagged-transactions-topic \
--partitions 3 \
--replication-factor 3 \
--if-not-exists

/opt/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092 \
--create --topic analysis-results-topic \
--partitions 3 \
--replication-factor 3 \
--if-not-exists

/opt/kafka/bin/kafka-topics.sh \
--bootstrap-server kafka-1:9092,kafka-2:9092,kafka-3:9092 \
--create --topic approved-transactions-topic \
--partitions 3 \
--replication-factor 3 \
--if-not-exists

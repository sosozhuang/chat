package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.KafkaConfig;
import com.github.sosozhuang.protobuf.Chat;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.consumer.internals.NoOpConsumerRebalanceListener;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class KafkaMessageService implements CloseableMessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaMessageService.class);
    private static final ConsumerRebalanceListener NO_OP_LISTENER = new NoOpConsumerRebalanceListener();
    private static final Map<Chat.Access, ConsumerTask> TEMP_TASKS = new ConcurrentHashMap<>();
    private KafkaConfig config;
    private Pattern pattern;
    private Properties producerProps;
    private Producer producer;
    private Properties consumerProps;
    private ThreadLocal<ConsumerTask> tasks;
    private List<Consumer> consumers;

    public KafkaMessageService(KafkaConfig config) {
        this.config = config;
        pattern = Pattern.compile("^" + config.getTopicPattern("chat") + "-\\d$");
        initProps();
        if (config.getTopicCreate()) {
            createTopicsIfNotExists();
        }
        producer = new KafkaProducer<>(producerProps);
        consumers = new ArrayList<>(16);
        tasks = new ThreadLocal<>();
    }

    private void initProps() {
        producerProps = new Properties();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getServers("localhost:9092"));
        producerProps.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProps.put(ProducerConfig.RETRIES_CONFIG, 0);
        producerProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 1024);
        producerProps.put(ProducerConfig.LINGER_MS_CONFIG, 1);
        producerProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 262144);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArraySerializer");
        producerProps.put("topic.count", config.getTopicCount(8));
        producerProps.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, GroupPartitioner.class);

        consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getServers("localhost:9092"));
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, config.getConsumerGroupId(String.valueOf(System.currentTimeMillis())));
        consumerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
    }

    private String getTopic(String topic) {
        return String.format("%s-%s", config.getTopicPattern("chat"), topic);
    }

    private String getTopic(int topic) {
        return String.format("%s-%d", config.getTopicPattern("chat"), topic);
    }

    public void createTopicsIfNotExists() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, config.getServers("localhost:9092"));
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 50000);
        props.put(AdminClientConfig.RETRIES_CONFIG, 3);
        AdminClient client = AdminClient.create(props);
        List<String> topicNames = IntStream.range(0, config.getTopicCount()).mapToObj(this::getTopic).collect(Collectors.toList());
        try {
            List<NewTopic> newTopics;
            try {
                Map<String, TopicDescription> descriptions = client.describeTopics(topicNames).all().get();
                newTopics = topicNames.stream().filter(topicName -> {
                    return !descriptions.containsKey(topicName);
                }).map(topicName -> {
                    return new NewTopic(topicName, config.getTopicPartition(8), config.getTopicReplication((short) 3));
                }).collect(Collectors.toList());
            } catch (ExecutionException e) {
                if (!e.getMessage().contains("UnknownTopicOrPartitionException")) {
                    throw e;
                }
                Map<String, String> configs = new HashMap<>();
                configs.put(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE);
                configs.put(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "102400");
                configs.put(TopicConfig.MESSAGE_TIMESTAMP_TYPE_CONFIG, "CreateTime");
                newTopics = topicNames.stream().map(topicName -> {
                    return new NewTopic(topicName, config.getTopicPartition(8), config.getTopicReplication((short) 3)).configs(Collections.unmodifiableMap(configs));
                }).collect(Collectors.toList());
            }
            if (newTopics.size() == 0) {
                return;
            }
            client.createTopics(newTopics).all().get();
        } catch (InterruptedException | ExecutionException e) {
            LOGGER.warn("Kafka creates topics error.", e);
        } finally {
            client.close();
        }
    }

    private int mapGroupIDToIndex(String groupID) {
        return (int) (Long.parseLong(groupID) % config.getTopicCount(8));
    }

    @Override
    public Future<?> send(String user, Chat.Group group, MessageRecord record) {
        return producer.send(new ProducerRecord<String, byte[]>(getTopic(mapGroupIDToIndex(group.getId())), null, System.currentTimeMillis(), (String) record.getKey(), (byte[]) record.getValue()));
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive() {
        ConsumerTask task = tasks.get();
        if (task == null) {
            KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps);
            consumer.subscribe(pattern, NO_OP_LISTENER);
            consumer.seekToEnd(Collections.emptyList());
            consumers.add(consumer);
            task = new ConsumerTask(consumer);
            tasks.set(task);
        }
        ConsumerRecords<String, byte[]> records = task.pollMessage();
        if (records == null || records.count() == 0) {
            return Collections.emptyList();
        }
        List<MessageRecord<K, V>> messages = new ArrayList<>(records.count());
        records.forEach(record -> {
            messages.add(new MessageRecord(record.key(), record.value()));
        });
        return messages;
    }

    private ConsumerTask newTempTask(Chat.Access access) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getServers("localhost:9092"));
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer");
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        String topicName = getTopic(mapGroupIDToIndex(access.getGroupId()));
        List<TopicPartition> partitions = consumer.partitionsFor(topicName).stream().map(partitionInfo -> {
            return new TopicPartition(topicName, partitionInfo.partition());
        }).collect(Collectors.toList());
        Map<TopicPartition, Long> timestamps = partitions.stream().collect(Collectors.toMap(Function.identity(), partition -> {
            return access.getTimestamp();
        }));
        Map<TopicPartition, OffsetAndTimestamp> offsets = consumer.offsetsForTimes(timestamps);
        if (offsets.size() == 0) {
            return null;
        }
        consumer.assign(offsets.keySet());
        offsets.forEach((topicPartition, offsetAndTimestamp) -> {
            consumer.seek(topicPartition, offsetAndTimestamp.offset());
        });
        return new ConsumerTask(consumer);
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive(String user, Chat.Group group, long timestamp) {
        Chat.Access.Builder builder = Chat.Access.newBuilder();
        builder.setUser(user);
        builder.setGroupId(group.getId());
        builder.setTimestamp(timestamp);
        Chat.Access access = builder.build();
        ConsumerTask task = TEMP_TASKS.computeIfAbsent(access, this::newTempTask);
        try {
            ConsumerRecords<String, byte[]> records = task.pollMessage();
            if (records == null || records.count() == 0) {
                LOGGER.info("No more message to poll.");
                task.consumer.close(100, TimeUnit.MILLISECONDS);
                TEMP_TASKS.remove(access, task);
                return Collections.emptyList();
            }
            List<MessageRecord<K, V>> messages = new ArrayList<>(records.count());
            records.forEach(record -> {
                messages.add(new MessageRecord(record.key(), record.value()));
            });
            return messages;
        } catch (RuntimeException e) {
            task.consumer.close(100, TimeUnit.MILLISECONDS);
            TEMP_TASKS.remove(access, task);
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        closeConsumers();
        closeProducer();
    }

    private void closeProducer() {
        if (producer != null) {
            producer.flush();
            producer.close();
            producer = null;
        }
    }

    private void closeConsumers() {
        if (consumers != null) {
            for (Consumer consumer : consumers) {
                consumer.wakeup();
                consumer.close();
            }
            consumers = null;
        }
        TEMP_TASKS.values().forEach(task -> {
            task.consumer.wakeup();
            task.consumer.close();
        });
    }

    private class ConsumerTask {
        private Consumer consumer;

        ConsumerTask(Consumer consumer) {
            this.consumer = consumer;
        }

        ConsumerRecords pollMessage() {
            ConsumerRecords records = consumer.poll(config.getConsumerPollTimeout(80));
            consumer.commitAsync();
            return records;
        }
    }
}

package com.github.sosozhuang.conf;

public class KafkaConfig {
    private Configuration config;
    public KafkaConfig(Configuration config) {
        this.config = config;
    }

    public String getServers() {
        return getServers(null);
    }
    public String getServers(String defaultValue) {
        return config.getString("kafka.servers", defaultValue);
    }

    public boolean getTopicCreate() {
        return getTopicCreate(false);
    }
    public boolean getTopicCreate(boolean defaultValue) {
        return config.getBoolean("kafka.topic.create", defaultValue);
    }

    public String getTopicPattern() {
        return getTopicPattern(null);
    }
    public String getTopicPattern(String defaultValue) {
        return config.getString("kafka.topic.pattern", defaultValue);
    }

    public int getTopicCount() {
        return getTopicCount(0);
    }
    public int getTopicCount(int defaultValue) {
        return config.getInteger("kafka.topic.count", defaultValue);
    }

    public int getTopicPartition() {
        return getTopicPartition(0);
    }
    public int getTopicPartition(int defaultValue) {
        return config.getInteger("kafka.topic.partition", defaultValue);
    }

    public short getTopicReplication() {
        return getTopicReplication((short) 0);
    }
    public short getTopicReplication(short defaultValue) {
        return config.getShort("kafka.topic.replica", defaultValue);
    }

    public String getConsumerGroupId() {
        return getConsumerGroupId(null);
    }
    public String getConsumerGroupId(String defaultValue) {
        return config.getString("kafka.consumer.group_id", defaultValue);
    }

    public long getConsumerPollTimeout() {
        return getConsumerPollTimeout(0);
    }
    public long getConsumerPollTimeout(long defaultValue) {
        return config.getLong("kafka.consumer.poll_timeout", defaultValue);
    }

    public long getConsumerScheduleRate() {
        return getConsumerScheduleRate(0);
    }
    public long getConsumerScheduleRate(long defaultValue) {
        return config.getLong("kafka.consumer.schedule_rate", defaultValue);
    }

    public long getConsumerCloseTimeout() {
        return getConsumerCloseTimeout(0);
    }
    public long getConsumerCloseTimeout(long defaultValue) {
        return config.getLong("kafka.consumer.close_timeout", defaultValue);
    }
}

package com.github.sosozhuang.conf;

public class KafkaConfiguration {
    private Configuration config;
    public KafkaConfiguration(Configuration config) {
        this.config = config;
    }

    public String getServers() {
        return getServers(null);
    }
    public String getServers(String defaultValue) {
        return config.getString("kafka.servers", defaultValue);
    }

    public String getTopicPattern() {
        return getTopicPattern(null);
    }
    public String getTopicPattern(String defaultValue) {
        return config.getString("kafka.topic_pattern", defaultValue);
    }

    public String getConsumerGroupId() {
        return getConsumerGroupId(null);
    }
    public String getConsumerGroupId(String defaultValue) {
        return config.getString("kafka.consumer.default_id", defaultValue);
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

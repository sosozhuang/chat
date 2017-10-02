package com.github.sosozhuang.conf;

import org.apache.activemq.ActiveMQConnection;

import javax.jms.ConnectionFactory;

public class ActiveMQConfig {
    private Configuration config;
    public ActiveMQConfig(Configuration config) {
        this.config = config;
    }

    public String getUserName() {
        return getUserName(ActiveMQConnection.DEFAULT_USER);
    }
    public String getUserName(String defaultValue) {
        return config.getString("activemq.conn.username", defaultValue);
    }

    public String getPassword() {
        return getPassword(ActiveMQConnection.DEFAULT_PASSWORD);
    }
    public String getPassword(String defaultValue) {
        return config.getString("activemq.conn.password", defaultValue);
    }

    public String getBrokerURL() {
        return getBrokerURL(ActiveMQConnection.DEFAULT_BROKER_URL);
    }
    public String getBrokerURL(String defaultValue) {
        return config.getString("activemq.conn.broker_url", defaultValue);
    }

    public String getClientID() {
        return getClientID(null);
    }
    public String getClientID(String defaultValue) {
        return config.getString("activemq.conn.client_id", defaultValue);
    }

    public String getTopicPattern() {
        return getTopicPattern(null);
    }
    public String getTopicPattern(String defaultValue) {
        return config.getString("activemq.topic.pattern", defaultValue);
    }

    public int getTopicCount() {
        return getTopicCount(0);
    }
    public int getTopicCount(int defaultValue) {
        return config.getInteger("activemq.topic.count", defaultValue);
    }


    public int getProducerCommitCount() {
        return getProducerCommitCount(0);
    }
    public int getProducerCommitCount(int defaultValue) {
        return config.getInteger("activemq.producer.commit_count", defaultValue);
    }

    public int getProducerCommitInterval() {
        return getProducerCommitInterval(0);
    }
    public int getProducerCommitInterval(int defaultValue) {
        return config.getInteger("activemq.producer.commit_interval", defaultValue);
    }

    public int getConsumerCommitCount() {
        return getConsumerCommitCount(0);
    }
    public int getConsumerCommitCount(int defaultValue) {
        return config.getInteger("activemq.consumer.commit_count", defaultValue);
    }

    public int getConsumerCommitInterval() {
        return getConsumerCommitInterval(0);
    }
    public int getConsumerCommitInterval(int defaultValue) {
        return config.getInteger("activemq.consumer.commit_interval", defaultValue);
    }
}

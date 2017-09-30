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

}

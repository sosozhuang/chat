package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.ActiveMQConfig;
import com.github.sosozhuang.protobuf.Chat;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.concurrent.Future;

public class ActiveMQMessageService implements CloseableMessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQMessageService.class);
    private ActiveMQConfig config;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private Session session;

    public ActiveMQMessageService(ActiveMQConfig config) throws JMSException {
        this.config = config;
        this.connectionFactory = new ActiveMQConnectionFactory(config.getBrokerURL());
        connection = connectionFactory.createConnection(config.getUserName(), config.getPassword());
        connection.start();
        session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        destination = session.createTopic(config.getTopicPattern() + "*");
        messageProducer = session.createProducer(destination);
        TopicSubscriber subscriber = session.createDurableSubscriber(session.createTopic(config.getTopicPattern() + "*"), "xx");
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive() {
        return null;
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive(String user, Chat.Group group, long timestamp) {
        return null;
    }

    @Override
    public Future<?> send(String user, Chat.Group group, MessageRecord record) {
        return null;
    }

    @Override
    public void close() throws IOException {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException e) {
                LOGGER.error("Close activemq connection error.", e);
            } finally {
                connection = null;
            }
        }
        if (session != null) {
            try {
                session.commit();
                session.close();
            } catch (JMSException e) {
                LOGGER.error("Close activemq session error.", e);
            } finally {
                session = null;
            }
        }
    }
}

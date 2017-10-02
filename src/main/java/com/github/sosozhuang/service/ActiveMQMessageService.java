package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.ActiveMQConfig;
import com.github.sosozhuang.protobuf.Chat;
import com.sun.istack.internal.NotNull;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ActiveMQMessageService implements CloseableMessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQMessageService.class);
    private ActiveMQConfig config;
    private ActiveMQConnectionFactory connectionFactory;
    private List<ActiveMQTopic> topics;
    private List<ConnectionHolder> holderList;
    private ThreadLocal<ConnectionHolder> xxs;

    public ActiveMQMessageService(ActiveMQConfig config) throws JMSException {
        this.config = config;
        connectionFactory = new ActiveMQConnectionFactory(config.getUserName(), config.getPassword(), config.getBrokerURL());
        connectionFactory.setClientID(config.getClientID());
        connectionFactory.setUseAsyncSend(true);
        topics = IntStream.range(0, config.getTopicCount()).mapToObj(this::getTopic).map(name -> {
            return new ActiveMQTopic(name);
        }).collect(Collectors.toList());

        holderList = new ArrayList<>(8);
        xxs = new ThreadLocal<>();
    }

    private String getTopic(int topic) {
        return String.format("%s-%d", config.getTopicPattern("chat"), topic);
    }

    private void rebalance() throws JMSException {
        int size = topics.size();
        int n = holderList.size();
        long parts = Math.round(size / (float) n);
        for (int i = 0; i < n - 1; i++) {
            holderList.get(i).setTopics(topics.subList((int) (i * parts), (int) ((i + 1) * parts - 1)));
        }
        holderList.get(n - 1).setTopics(topics.subList((int) ((n - 1) * parts), size - 1));
    }

    private ConnectionHolder createHolder() {
        try {
            ConnectionHolder holder = new ConnectionHolder();
            xxs.set(holder);
            synchronized (this) {
                holderList.add(holder);
                rebalance();
            }
            return holder;
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive() {
        ConnectionHolder holder = xxs.get();
        if (holder == null) {
            holder = createHolder();
        }
        List<BytesMessage> messages = null;
        try {
            messages = holder.receive();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        if (messages == null || messages.size() == 0) {
            return Collections.emptyList();
        }
        return messages.stream().map(message -> {
            try {
                String key = message.getStringProperty("message.key");
                byte[] value = new byte[message.getBodyLength()];
                message.readBytes(value);
                return new MessageRecord(message.getStringProperty("message.key"), value);
            } catch (JMSException e) {
                LOGGER.error("Create message record error.", e);
            }
            return null;
        }).collect(Collectors.toList());

//        List<MessageRecord<K, V>> list = new ArrayList<>(messages.size());
//        messages.forEach(record -> {
//            list.add(new MessageRecord(record.key(), record.value()));
//        });
//        return messages;
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive(String user, Chat.Group group, long timestamp) {
        return null;
    }

    private int mapGroupIDToIndex(String groupID) {
        return (int) (Long.parseLong(groupID) % config.getTopicCount(64));
    }

    @Override
    public Future<?> send(String user, Chat.Group group, MessageRecord record) {
        ConnectionHolder connectionHolder = xxs.get();
        if (connectionHolder == null) {
            connectionHolder = createHolder();
        }
        ActiveMQBytesMessage message = new ActiveMQBytesMessage();
        try {
            message.setStringProperty("message.key", (String) record.getKey());
            message.writeBytes((byte[]) record.getValue());
            connectionHolder.send(topics.get(mapGroupIDToIndex(group.getId())), message);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (holderList != null) {
            holderList.forEach(holder -> holder.close());
            holderList = null;
        }
//        if (connection != null) {
//            try {
//                connection.close();
//            } catch (JMSException e) {
//                LOGGER.error("Close activemq connection error.", e);
//            } finally {
//                connection = null;
//            }
//        }
//        if (session != null) {
//            try {
//                session.commit();
//                session.close();
//            } catch (JMSException e) {
//                LOGGER.error("Close activemq session error.", e);
//            } finally {
//                session = null;
//            }
//        }
    }

    private class ConnectionHolder {
        Connection connection;
        Session producerSession;
        MessageProducer producer;
        Session[] subscriberSessions;
        TopicSubscriber[] subscribers;
        List<ActiveMQTopic> topics;

        int producerCommitCount;
        int producerCommitInterval;
        int consumerCommitCount;
        int consumerCommitInterval;

        int sentCount;
        long producerLastCommit;
        int receivedCount;
        long consumerLastCommit;

        ConnectionHolder() throws JMSException {
            connection = connectionFactory.createConnection();
            connection.start();
            producerSession = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
            producer = producerSession.createProducer(null);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.setDisableMessageID(true);

            producerCommitCount = config.getProducerCommitCount();
            producerCommitInterval = config.getProducerCommitInterval();
            consumerCommitCount = config.getConsumerCommitCount();
            consumerCommitInterval = config.getConsumerCommitInterval();

            sentCount = receivedCount = 0;
            producerLastCommit = consumerLastCommit = Instant.now().toEpochMilli();
        }

        void send(Destination destination, Message message) throws JMSException {
            producer.send(destination, message);
            sentCount++;
            long now = Instant.now().toEpochMilli();
            if (sentCount > producerCommitCount || now - producerLastCommit >= producerCommitInterval) {
                producerSession.commit();
                sentCount = 0;
                producerLastCommit = now;
            }
        }

        List<BytesMessage> receive() throws JMSException {
            List<BytesMessage> messages = new ArrayList<>((int) (100 * .6) * subscribers.length);
            BytesMessage message = null;
            for (TopicSubscriber subscriber : subscribers) {
                for (int i = 0; i < 100; i++) {
                    message = (BytesMessage) subscriber.receiveNoWait();
                    if (message == null) {
                        break;
                    }
                    messages.add(message);
                    receivedCount++;
                }
            }
            long now = Instant.now().toEpochMilli();
            if (receivedCount > consumerCommitCount || now - consumerLastCommit >= consumerCommitInterval) {
                for (Session session : subscriberSessions) {
                    session.commit();
                }
                receivedCount = 0;
                consumerLastCommit = now;
            }
            return messages;
        }

        void setTopics(@NotNull List<ActiveMQTopic> topics) throws JMSException {
            this.topics = topics;
            if (subscribers != null) {
                for (TopicSubscriber subscriber : subscribers) {
                    subscriber.close();
                }
                for (Session session : subscriberSessions) {
                    session.commit();
                    session.close();
                }
            }

            int size = topics.size();
            subscribers = new TopicSubscriber[size];
            subscriberSessions = new Session[size];
            receivedCount = 0;
            consumerLastCommit = Instant.now().toEpochMilli();
            int i = 0;
            for (ActiveMQTopic topic : topics) {
                Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
                subscriberSessions[i] = session;
                subscribers[i] = session.createDurableSubscriber(topic, topic.getTopicName());
                i++;
            }
        }
//        void setSubscribers(@NotNull List<TopicSubscriber> subscribers) {
//            this.subscribers = subscribers;
//        }

        void close() {
            if (producer != null) {
                try {
                    producer.close();
                } catch (JMSException e) {
                    LOGGER.error("Close activemq producer error.", e);
                }
            }
            if (producerSession != null) {
                try {
                    producerSession.commit();
                    producerSession.close();
                } catch (JMSException e) {
                    LOGGER.error("Close activemq producer session error.", e);
                }
            }
            if (subscribers != null) {
                for (TopicSubscriber subscriber : subscribers) {
                    try {
                        subscriber.close();
                    } catch (JMSException e) {
                        LOGGER.error("Close activemq subscriber error.", e);
                    }
                }
            }
            if (subscriberSessions != null) {
                for (Session session : subscriberSessions) {
                    try {
                        session.close();
                    } catch (JMSException e) {
                        LOGGER.error("Close activemq subscriber session error.", e);
                    }
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (JMSException e) {
                    LOGGER.error("Close activemq connection error.", e);
                }
            }
        }

    }
}

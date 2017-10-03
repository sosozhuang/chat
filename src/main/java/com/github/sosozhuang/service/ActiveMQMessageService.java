package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.ActiveMQConfig;
import com.github.sosozhuang.protobuf.Chat;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.util.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ActiveMQMessageService implements CloseableMessageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActiveMQMessageService.class);
    private static final String MESSAGE_KEY_PROPERTY = "messageKey";
    private ActiveMQConfig config;
    private ActiveMQConnectionFactory connectionFactory;
    private Connection connection;
    private List<ActiveMQTopic> topics;
    private List<InternalService> serviceList;
    private ThreadLocal<InternalService> services;
    private Map<Chat.Access, InternalTempService> tempServices;

    public ActiveMQMessageService(ActiveMQConfig config) throws JMSException {
        this.config = config;
        connectionFactory = new ActiveMQConnectionFactory(config.getUserName(), config.getPassword(), config.getBrokerURL());
        connectionFactory.setClientID(config.getClientID("chat"));
        connectionFactory.setUseAsyncSend(true);

        connection = connectionFactory.createConnection();
        connection.setClientID("offline");
        connection.start();

        topics = IntStream.range(0, config.getTopicCount()).mapToObj(this::createTopic).collect(Collectors.toList());
        serviceList = new ArrayList<>(8);
        services = new ThreadLocal<>();
        tempServices = new ConcurrentHashMap<>();
    }

    private ActiveMQTopic createTopic(int topic) {
        return new ActiveMQTopic(String.format("%s-%d", config.getTopicPattern("chat"), topic));
    }

    private void rebalance() throws JMSException {
        int size = topics.size();
        int n = serviceList.size();
        long parts = Math.round(size / (float) n);
        if (parts == 0L) {
            serviceList.get(0).setTopics(topics);
            return;
        }
        for (int i = 0; i < n - 1 && i * parts < size; i++) {
            serviceList.get(i).setTopics(topics.subList((int) (i * parts), (int) ((i + 1) * parts)));
        }
        parts *= n - 1;
        if (size > parts) {
            serviceList.get(n - 1).setTopics(topics.subList((int) parts, size));
        }
    }

    private InternalService createService() {
        try {
            InternalService service = new InternalService();
            services.set(service);
            synchronized (this) {
                serviceList.add(service);
                rebalance();
            }
            return service;
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    private static <K, V> MessageRecord<K, V> messageMapper(BytesMessage message) {
        try {
            String key = message.getStringProperty(MESSAGE_KEY_PROPERTY);
            byte[] bytes = new byte[1024];
            int n = -1;
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            while ((n = message.readBytes(bytes)) != -1) {
                os.write(bytes, 0, n);
            }
            return new MessageRecord(key, os.toByteArray());
        } catch (JMSException e) {
            LOGGER.error("Create message record error.", e);
        }
        return null;
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive() {
        InternalService service = services.get();
        if (service == null) {
            service = createService();
        }
        List<BytesMessage> messages = null;
        try {
            messages = service.receive();
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
        if (messages == null || messages.size() == 0) {
            return Collections.emptyList();
        }
        return messages.stream().map(message -> (MessageRecord<K, V>) ActiveMQMessageService.messageMapper(message)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private InternalTempService createTempService(Chat.Access access) {
        try {
            Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
            String id = access.getGroupId();
            String selector = String.format("JMSTimestamp >= %d AND %s = '%s'", access.getTimestamp(), MESSAGE_KEY_PROPERTY, id);
            TopicSubscriber subscriber = session.createDurableSubscriber(topics.get(mapGroupIDToIndex(id)), id + ":" + access.getUser(), selector, false);
            return new InternalTempService(session, subscriber);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <K, V> Iterable<MessageRecord<K, V>> receive(String user, Chat.Group group, long timestamp) {
        Chat.Access.Builder builder = Chat.Access.newBuilder();
        builder.setUser(user);
        builder.setGroupId(group.getId());
        builder.setTimestamp(timestamp);
        Chat.Access access = builder.build();
        InternalTempService service = tempServices.computeIfAbsent(access, this::createTempService);
        List<BytesMessage> messages = null;
        try {
            messages = service.receive();
        } catch (JMSException e) {
            service.close();
            tempServices.remove(access, service);
            throw new RuntimeException(e);
        }
        if (messages == null || messages.size() == 0) {
            LOGGER.info("No more message to poll.");
            service.close();
            tempServices.remove(access, service);
            return Collections.emptyList();
        }

        return messages.stream().map(message -> (MessageRecord<K, V>) ActiveMQMessageService.messageMapper(message)).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private int mapGroupIDToIndex(String groupID) {
        return (int) (Long.parseLong(groupID) % config.getTopicCount(64));
    }

    @Override
    public void send(String user, Chat.Group group, MessageRecord record) {
        InternalService service = services.get();
        if (service == null) {
            service = createService();
        }
        ActiveMQBytesMessage message = new ActiveMQBytesMessage();
        try {
            message.setJMSTimestamp(System.currentTimeMillis());
            message.setStringProperty(MESSAGE_KEY_PROPERTY, (String) record.getKey());
            message.writeBytes((byte[]) record.getValue());
            service.send(topics.get(mapGroupIDToIndex(group.getId())), message);
        } catch (JMSException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (serviceList != null) {
            serviceList.forEach(service -> service.close());
            serviceList = null;
        }
        tempServices.values().forEach(service -> service.close());
        try {
            connection.close();
        } catch (JMSException e) {
            LOGGER.error("Close activemq connection error.", e);
        }
    }

    private class InternalService {
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

        InternalService() throws JMSException {
            connection = connectionFactory.createConnection();
            connection.start();
            producerSession = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
            producer = producerSession.createProducer(null);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            producer.setDisableMessageID(true);
            producer.setDisableMessageTimestamp(true);

            producerCommitCount = config.getProducerCommitCount();
            producerCommitInterval = config.getProducerCommitInterval();
            consumerCommitCount = config.getConsumerCommitCount();
            consumerCommitInterval = config.getConsumerCommitInterval();

            sentCount = receivedCount = 0;
            producerLastCommit = consumerLastCommit = System.currentTimeMillis();
        }

        void send(Destination destination, Message message) throws JMSException {
            producer.send(destination, message);
            sentCount++;
            long now = System.currentTimeMillis();
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
            long now = System.currentTimeMillis();
            if (receivedCount > consumerCommitCount || now - consumerLastCommit >= consumerCommitInterval) {
                for (Session session : subscriberSessions) {
                    session.commit();
                }
                receivedCount = 0;
                consumerLastCommit = now;
            }
            return messages;
        }

        void setTopics(List<ActiveMQTopic> topics) throws JMSException {
            Objects.requireNonNull(topics);
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
            consumerLastCommit = System.currentTimeMillis();
            int i = 0;
            for (ActiveMQTopic topic : topics) {
                Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
                subscriberSessions[i] = session;
                subscribers[i] = session.createDurableSubscriber(topic, topic.getTopicName());
                i++;
            }
        }

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
                        session.commit();
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

    private static class InternalTempService {

        final Session session;
        final TopicSubscriber subscriber;

        InternalTempService(Session session, TopicSubscriber subscriber) {
            this.session = session;
            this.subscriber = subscriber;
        }

        List<BytesMessage> receive() throws JMSException {
            List<BytesMessage> messages = new ArrayList<>((int) (100 * .6));
            BytesMessage message = null;
            for (int i = 0; i < 100; i++) {
                message = (BytesMessage) subscriber.receiveNoWait();
                if (message == null) {
                    break;
                }
                messages.add(message);
            }
            return messages;
        }

        void close() {
            try {
                subscriber.close();
            } catch (JMSException e) {
                LOGGER.error("Close activemq subscriber error.", e);
            }
            try {
                session.commit();
                session.close();
            } catch (JMSException e) {
                LOGGER.error("Close activemq subscriber session error.", e);
            }
        }
    }
}

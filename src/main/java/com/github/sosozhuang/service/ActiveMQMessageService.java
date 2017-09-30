package com.github.sosozhuang.service;

import com.github.sosozhuang.protobuf.Chat;

import java.io.IOException;
import java.util.concurrent.Future;

public class ActiveMQMessageService implements CloseableMessageService {
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

    }
}

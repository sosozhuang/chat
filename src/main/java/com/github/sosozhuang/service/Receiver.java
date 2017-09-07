package com.github.sosozhuang.service;

import com.github.sosozhuang.protobuf.Chat;

public interface Receiver {
    public <K, V> Iterable<MessageRecord<K, V>> receive();
    public <K, V> Iterable<MessageRecord<K, V>> receive(String user, Chat.Group group, long timestamp);
}

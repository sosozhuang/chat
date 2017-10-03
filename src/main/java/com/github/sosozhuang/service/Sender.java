package com.github.sosozhuang.service;

import com.github.sosozhuang.protobuf.Chat;

import java.util.concurrent.Future;

public interface Sender {
    public void send(String user, Chat.Group group, MessageRecord record);
}

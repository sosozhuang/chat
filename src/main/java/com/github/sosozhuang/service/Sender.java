package com.github.sosozhuang.service;

import java.util.concurrent.Future;

public interface Sender {
    public Future<?> send(String topic, String key, String value);
}

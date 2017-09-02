package com.github.sosozhuang.service;

import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.io.Closeable;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public interface MessageService extends Sender, Receiver {
}

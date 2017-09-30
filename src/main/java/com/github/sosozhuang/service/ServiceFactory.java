package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.ActiveMQConfig;
import com.github.sosozhuang.conf.Configuration;
import com.github.sosozhuang.conf.KafkaConfig;
import com.github.sosozhuang.conf.RedisConfig;
import io.netty.util.internal.StringUtil;

public class ServiceFactory {
    private ServiceFactory() {}
    public static CloseableMessageService createMessageService(Configuration config) throws UnsupportedException {
        String type = config.getString("message.service");
        if (StringUtil.isNullOrEmpty(type)) {
            throw new IllegalArgumentException("Message service type is empty.");
        }

        switch (type.trim().toLowerCase()) {
            case "kafka":
                KafkaConfig kafkaConf = new KafkaConfig(config);
                return new KafkaMessageService(kafkaConf);
            case "activemq":
                ActiveMQConfig activeMQConf = new ActiveMQConfig(config);
                return new ActiveMQMessageService(activeMQConf);
            default:
                throw new UnsupportedException("Message service type[" + type + "] is not supported");
        }

    }

    public static CloseableMetaService createMetaService(Configuration config) throws UnsupportedException {
        String type = config.getString("meta.service");
        if (StringUtil.isNullOrEmpty(type)) {
            throw new IllegalArgumentException("Meta service type is empty.");
        }

        switch (type.trim().toLowerCase()) {
            case "redis":
                RedisConfig redisConf = new RedisConfig(config);
                return new RedisMetaService(redisConf);
            default:
                throw new UnsupportedException("Meta service type[" + type + "] is not supported");
        }
    }
}

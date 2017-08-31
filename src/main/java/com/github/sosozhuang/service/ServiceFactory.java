package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.Configuration;
import com.github.sosozhuang.conf.KafkaConfiguration;
import com.github.sosozhuang.conf.RedisConfiguration;
import io.netty.util.internal.StringUtil;

public class ServiceFactory {
    private ServiceFactory() {}
    public static MessageService createMessageService(Configuration config) throws UnsupportedException {
        String type = config.getString("message.service");
        if (StringUtil.isNullOrEmpty(type)) {
            throw new UnsupportedException("Message service type is empty.");
        }

        switch (type.trim().toLowerCase()) {
            case "kafka":
                KafkaConfiguration kafkaConf = new KafkaConfiguration(config);
                return new KafkaMessageService(kafkaConf);
            default:
                throw new UnsupportedException("Message service type [" + type + "] is not supported");
        }

    }

    public static MetaService createMetaService(Configuration config) throws UnsupportedException {
        String type = config.getString("meta.service");
        if (StringUtil.isNullOrEmpty(type)) {
            throw new UnsupportedException("Meta service type is empty.");
        }

        switch (type.trim().toLowerCase()) {
            case "redis":
                RedisConfiguration redisConf = new RedisConfiguration(config);
                return new RedisMetaService(redisConf);
            default:
                throw new UnsupportedException("Meta service type [" + type + "] is not supported");
        }
    }
}

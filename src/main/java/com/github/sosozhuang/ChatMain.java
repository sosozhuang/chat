package com.github.sosozhuang;

import com.github.sosozhuang.conf.Configuration;
import com.github.sosozhuang.conf.ServerConfig;
import com.github.sosozhuang.service.CloseableMessageService;
import com.github.sosozhuang.service.CloseableMetaService;
import com.github.sosozhuang.service.ServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public final class ChatMain {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatMain.class);

    public static void main(String[] args) {
        try {
            Configuration config;
            String configPath = System.getProperty("config");
            if (configPath == null || "".equals(configPath)) {
                config = new Configuration();
            } else {
                config = new Configuration(new File(configPath));
            }

            ServerConfig serverConf = new ServerConfig(config);
            CloseableMetaService metaService = ServiceFactory.createMetaService(config);
            CloseableMessageService messageService = ServiceFactory.createMessageService(config);
            ChatServer server = new ChatServer(serverConf, metaService, messageService);
            server.init();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                server.stop();
                try {
                    metaService.close();
                } catch (IOException e) {
                    LOGGER.error("Close meta service error.", e);
                }
                try {
                    messageService.close();
                } catch (IOException e) {
                    LOGGER.error("Close message service error.", e);
                }
                LOGGER.info("Chat service stopped.");
            }));
            server.start();
        } catch (Exception e) {
            LOGGER.error("Unable to start chat service.", e);
        }
    }
}

package com.github.sosozhuang;


import com.github.sosozhuang.conf.ServerConfig;
import com.github.sosozhuang.handler.ChatHandler;
import com.github.sosozhuang.handler.ChatInitializer;
import com.github.sosozhuang.protobuf.Chat;
import com.github.sosozhuang.service.MessageRecord;
import com.github.sosozhuang.service.MessageService;
import com.github.sosozhuang.service.MetaService;
import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class ChatServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatServer.class);
    private ServerConfig config;
    private long id;
    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private volatile ChannelFuture future;
    private volatile boolean registered;
    private MetaService metaService;
    private MessageService messageService;

    public ChatServer(ServerConfig config,
                      MetaService metaService,
                      MessageService messageService) throws UnknownHostException {
        this.config = config;
        this.metaService = metaService;
        this.messageService = messageService;
        this.id = config.getId();
        if (this.id <= 0) {
            this.id = addressToLong(config.getHost(), config.getPort());
            config.setId(this.id);
        }
        this.registered = false;
    }

    private static long addressToLong(String host, int port) throws UnknownHostException {
        String address = InetAddress.getByName(host).getHostAddress();
        long ip = 0;
        String[] nums = address.split("\\.");
        for (int i = 0; i < 4; i++) {
            ip = ip << 8 | Integer.parseInt(nums[i]);
        }
        ip = ip << 16 | port;
        return ip;
    }

    private void receive() {
        Iterable<MessageRecord<String, byte[]>> records;
        try {
            records = messageService.receive();
        } catch (RuntimeException e) {
            LOGGER.error("Receive messages from service error.", e);
            return;
        }
        Chat.Message message = null;
        for (MessageRecord<String, byte[]> record : records) {
            try {
                message = Chat.Message.parseFrom(record.getValue());
            } catch (InvalidProtocolBufferException e) {
                LOGGER.error("Parse record error.", e);
                continue;
            }
            if (message.getServerId() == id) {
                continue;
            }
            ChatHandler.receiveMessage(message);
        }
    }

    public void init() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 64)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChatInitializer(config,
                        metaService, messageService));
    }

    public void start() throws InterruptedException {
        Chat.Server.Builder builder = Chat.Server.newBuilder();
        builder.setId(String.valueOf(id));
        builder.setHost(config.getHost());
        builder.setPort(config.getPort());
        builder.setStartAt(System.currentTimeMillis());
        builder.setConfig(config.toString());
        if (!metaService.registerServer(builder.build())) {
            throw new RuntimeException("Server[" + id + "] already registered in meta service.");
        }
        registered = true;
        future = bootstrap.bind(config.getHost(), config.getPort()).addListener(future -> {
            if (future.isSuccess()) {
                LOGGER.info("Chat service rocks!");
                for (EventExecutor executor : workerGroup) {
                    executor.scheduleAtFixedRate(this::receive, 0, config.getExecutorScheduleRate(80), TimeUnit.MILLISECONDS);
                }
            }
        }).sync();
    }

    public void stop() {
        if (future != null) {
            try {
                future.channel().close().sync();
            } catch (Throwable cause) {
                LOGGER.error("Close server channel error.", cause);
            }
        }
        if (bossGroup != null) {
            try {
                bossGroup.shutdownGracefully().sync();
            } catch (Throwable cause) {
                LOGGER.error("Shut down boss group error.", cause);
            }
        }
        if (workerGroup != null) {
            try {
                workerGroup.shutdownGracefully().sync();
            } catch (Throwable cause) {
                LOGGER.error("Shut down worker group error.", cause);
            }
        }
        if (registered && !metaService.unRegisterServer(String.valueOf(id))) {
            LOGGER.warn("Server not registered in meta service");
        }
    }
}

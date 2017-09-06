package com.github.sosozhuang;


import com.github.sosozhuang.conf.ServerConfiguration;
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
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class ChatServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatServer.class);
    private ServerConfiguration config;
    private SslContext sslCtx;
    private String host;
    private int port;
    private long id;
    private ServerBootstrap bootstrap;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private ChannelFuture future;
    private MetaService metaService;
    private MessageService messageService;
    private boolean registered;

    public ChatServer(ServerConfiguration config,
                      MetaService metaService,
                      MessageService messageService) throws Exception {
        this.config = config;
        this.metaService = metaService;
        this.messageService = messageService;
        if (config.getSsl()) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            String cert = config.getCert();
            String key = config.getKey();
            if (StringUtil.isNullOrEmpty(cert) || StringUtil.isNullOrEmpty(key)) {
                sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
            } else {
                sslCtx = SslContextBuilder.forServer(new File(cert), new File(key)).build();
            }
        }
        this.host = config.getHost();
        this.port = config.getPort();
        this.id = config.getId();
        if (this.id <= 0) {
            this.id = address2Long(host, port);
        }
        this.registered = false;
    }

    private static long address2Long(String host, int port) throws UnknownHostException {
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
        try {
            Iterable<MessageRecord<String, byte[]>> records = messageService.receive();
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
        } catch (Exception e) {
            LOGGER.error("Receive messages from service error.", e);
        }
    }

    public void init() {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();
        bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 64)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childHandler(new ChatInitializer(id,
                        config.getIdleClose(),
                        config.getIdleTimeout(),
                        config.getWebsocketPath("/websocket"),
                        sslCtx, metaService, messageService));
    }

    public void start() throws InterruptedException {
        Chat.Server.Builder builder = Chat.Server.newBuilder();
        builder.setId(String.valueOf(id));
        builder.setHost(host);
        builder.setPort(port);
        builder.setStartAt(System.currentTimeMillis());
        builder.setConfig(config.toString());
        if (!metaService.registerServer(builder.build())) {
            throw new RuntimeException("Server[" + id + "] already registered in meta service.");
        }
        registered = true;
        future = bootstrap.bind(host, port).addListener(future -> {
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
            } catch (Throwable throwable) {
                LOGGER.error("Close server channel error.", throwable);
            }
        }
        if (bossGroup != null) {
            try {
                bossGroup.shutdownGracefully().sync();
            } catch (Throwable throwable) {
                LOGGER.error("Shut down boss group error.", throwable);
            }
        }
        if (workerGroup != null) {
            try {
                workerGroup.shutdownGracefully().sync();
            } catch (Throwable throwable) {
                LOGGER.error("Shut down worker group error.", throwable);
            }
        }
        if (registered && !metaService.unRegisterServer(String.valueOf(id))) {
            LOGGER.warn("Server not registered in meta service");
        }
    }
}

package com.github.sosozhuang.handler;

import com.github.sosozhuang.conf.ServerConfigGetter;
import com.github.sosozhuang.service.MessageService;
import com.github.sosozhuang.service.MetaService;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.handler.stream.ChunkedWriteHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.traffic.ChannelTrafficShapingHandler;
import io.netty.util.internal.StringUtil;

import java.io.File;
import java.security.cert.CertificateException;
import java.util.concurrent.TimeUnit;


public class ChatInitializer extends ChannelInitializer<SocketChannel> {
    private ServerConfigGetter config;
    private SslContext sslCtx;
    private MetaService metaService;
    private MessageService messageService;

    public ChatInitializer(ServerConfigGetter config,
                           MetaService metaService,
                           MessageService messageService) throws Exception {
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
        this.config = config;
        this.metaService = metaService;
        this.messageService = messageService;
        HttpHandler.addStaticFiles(config.getStaticFiles());
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(channel.alloc()));
        }
        if (config.getIdleClose() && config.getIdleTimeout() > 0) {
            p.addLast(new IdleStateHandler(config.getIdleTimeout(), 0, 0, TimeUnit.MINUTES));
            p.addLast(new IdleStateTrigger());
        }
        if (config.getTrafficShaping() && config.getTrafficLimit() > 0) {
            p.addLast(new ChannelTrafficShapingHandler(0, config.getTrafficLimit(), 500, 5000));
        }
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(65536));
        if (sslCtx != null) {
            p.addLast(new ChunkedWriteHandler());
        }
        p.addLast(new WebSocketServerCompressionHandler());
        p.addLast(new WebSocketServerProtocolHandler(config.getWebsocketPath("/websocket"),
                null, true));
        p.addLast(new HttpHandler(metaService));
        p.addLast(new StaticFileHandler());
        p.addLast(new ChatHandler(config.getId(), metaService, messageService));
    }
}

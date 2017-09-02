package com.github.sosozhuang;

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
import io.netty.handler.timeout.IdleStateHandler;

import java.util.concurrent.TimeUnit;


public class ChatInitializer extends ChannelInitializer<SocketChannel> {
    private final long id;
    private final boolean idleClose;
    private final long readIdleTime;
    private String websocketPath;
    private SslContext sslCtx;
    private MetaService metaService;
    private MessageService messageService;

    public ChatInitializer(long id,
                           boolean idleClose,
                           long readIdleTime,
                           String websocketPath,
                           SslContext sslCtx,
                           MetaService metaService,
                           MessageService messageService) {
        this.id = id;
        this.idleClose = idleClose;
        this.readIdleTime = readIdleTime;
        this.websocketPath = websocketPath;
        this.sslCtx = sslCtx;
        this.metaService = metaService;
        this.messageService = messageService;
    }

    @Override
    protected void initChannel(SocketChannel channel) throws Exception {
        ChannelPipeline p = channel.pipeline();
        if (sslCtx != null) {
            p.addLast(sslCtx.newHandler(channel.alloc()));
        }
        if (idleClose && readIdleTime > 0) {
            p.addLast(new IdleStateHandler(readIdleTime, 0, 0, TimeUnit.MINUTES));
            p.addLast(new IdleStateTrigger());
        }
        p.addLast(new HttpServerCodec());
        p.addLast(new HttpObjectAggregator(65536));
        p.addLast(new WebSocketServerCompressionHandler());
        p.addLast(new WebSocketServerProtocolHandler(websocketPath, null, true));
        p.addLast(new HttpHandler(websocketPath, metaService));
        p.addLast(new ChatHandler(id, metaService, messageService));
    }
}

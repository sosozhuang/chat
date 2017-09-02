package com.github.sosozhuang;

import com.github.sosozhuang.protobuf.Chat;
import com.github.sosozhuang.service.MetaService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpHandler.class);
    private String path;
    private MetaService metaService;

    public HttpHandler(String path, MetaService metaService) {
        this.path = path;
        this.metaService = metaService;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        if (request.method() == GET) {
            handleGet(ctx, request);
        } else if (request.method() == POST) {
            handlePost(ctx, request);
        } else {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, METHOD_NOT_ALLOWED));
        }
    }

    private void handleGet(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        try {
            String p = new URI(uri).getPath();
            if (!"/chat".equals(p)) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
                return;
            }
        } catch (URISyntaxException e) {
            LOGGER.warn("Request uri {} invalid.", uri, e);
        }

        Map<String, List<String>> params = (new QueryStringDecoder(uri)).parameters();
        List<String> userParams = params.get("user");
        if (userParams == null || userParams.size() == 0) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }
        String user = userParams.get(0);
        if (StringUtil.isNullOrEmpty(user)) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        List<String> groupParams = params.get("group");
        if (groupParams == null || groupParams.size() == 0) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }
        String groupID = groupParams.get(0);
        if (StringUtil.isNullOrEmpty(groupID)) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        List<String> tokenParams = params.get("token");
        if (tokenParams == null || tokenParams.size() == 0) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }
        String token = tokenParams.get(0);
        if (StringUtil.isNullOrEmpty(token)) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        groupID = groupID.trim();
        token = token.trim();

        Chat.Group group;
        try {
            group = metaService.groupInfo(groupID);
        } catch (IOException e) {
            LOGGER.warn("Get group[{}] info error.", groupID, e);
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR));
            return;
        }

        if (group == null) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
            return;
        }

        if (!token.equals(group.getToken())) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
            return;
        }

        if (metaService.groupMembersCount(groupID) > 1000) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, NOT_ACCEPTABLE));
            return;
        }

        if (!metaService.joinGroup(groupID, user)) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, CONFLICT));
            return;
        }

        String location = getWebSocketLocation(ctx.pipeline(), request, path);
        ByteBuf content = ChatPage.getContent(location, groupID, user);
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK, content);

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        HttpUtil.setContentLength(response, content.readableBytes());

        sendHttpResponse(ctx, request, response);
//        ctx.fireUserEventTriggered(groupID);
    }

    private void handlePost(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri();
        try {
            String path = new URI(uri).getPath();
            if (!"/chat".equals(path)) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
                return;
            }
        } catch (URISyntaxException e) {
            LOGGER.warn("Request uri {} invalid.", uri, e);
        }


        List<String> userParams = new QueryStringDecoder(uri).parameters().get("user");
        if (userParams == null || userParams.size() == 0) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }
        String user = userParams.get(0);
        if (StringUtil.isNullOrEmpty(user)) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        String token = request.headers().get("token");
        if (StringUtil.isNullOrEmpty(token) || StringUtil.isNullOrEmpty(token.trim())) {
            sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, BAD_REQUEST));
            return;
        }

        Chat.Group.Builder builder = Chat.Group.newBuilder();
        String groupID = metaService.nextGroupID();
        builder.setId(groupID);
        builder.setToken(token.trim());
        builder.setOwner(user);
        builder.setCreateAt(System.currentTimeMillis());

        ByteBuf content;
        FullHttpResponse response;
        if (metaService.createGroup(builder.build())) {
            content = Unpooled.copiedBuffer("group created.", CharsetUtil.US_ASCII);
            response = new DefaultFullHttpResponse(HTTP_1_1, CREATED, content);
            response.headers().set("group", groupID);
        } else {
            content = Unpooled.copiedBuffer("group exists.", CharsetUtil.US_ASCII);
            response = new DefaultFullHttpResponse(HTTP_1_1, CONFLICT, content);
        }

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");
        HttpUtil.setContentLength(response, content.readableBytes());
        sendHttpResponse(ctx, request, response);
    }

    private void handleDel(ChannelHandlerContext ctx, FullHttpRequest request) {

    }

    private static String getWebSocketLocation(ChannelPipeline pipeline, HttpRequest req, String path) {
        String protocol = "ws";
        if (pipeline.get(SslHandler.class) != null) {
            protocol = "wss";
        }
        return protocol + "://" + req.headers().get(HttpHeaderNames.HOST) + path;
    }

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        ChannelFuture f = ctx.channel().writeAndFlush(response);
        if (!HttpUtil.isKeepAlive(request) || !response.status().equals(OK)) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("HttpHandler caught an error", cause);
        ctx.close();
    }


}

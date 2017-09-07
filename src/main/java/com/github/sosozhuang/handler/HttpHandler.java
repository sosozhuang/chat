package com.github.sosozhuang.handler;

import com.github.sosozhuang.protobuf.Chat;
import com.github.sosozhuang.service.MetaService;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import io.netty.handler.codec.http.cookie.ServerCookieEncoder;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpMethod.POST;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpHandler.class);
    private static final Set<String> STATIC_FILES = new HashSet<>();
    private MetaService metaService;
    private SecureRandom random;

    public HttpHandler(MetaService metaService) {
        this.metaService = metaService;
        try {
            random = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            random = new SecureRandom();
        }
        random.setSeed(System.currentTimeMillis());
    }

    public static void addStaticFiles(String[] files) {
        STATIC_FILES.addAll(Arrays.asList(files));
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

    private static String getQueryParam(Map<String, List<String>> params, String key) {
        List<String> values = params.get(key);
        if (values == null || values.size() == 0) {
            return null;
        }
        return values.get(0);
    }

    private void handleGet(ChannelHandlerContext ctx, FullHttpRequest request) throws URISyntaxException {
        String uri = request.uri();
        String p = new URI(uri).getPath();
        if ("/".equals(p)) {
            sendRedirectResponse(ctx, request, "/index.html", null);
            return;
        }

        if ("/chat".equals(p)) {
            Map<String, List<String>> params = (new QueryStringDecoder(uri)).parameters();
            String user = getQueryParam(params, "user");
            if (StringUtil.isNullOrEmpty(user)) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                return;
            }

            String groupID = getQueryParam(params, "group");
            if (StringUtil.isNullOrEmpty(groupID)) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                return;
            }

            String token = getQueryParam(params, "token");
            if (StringUtil.isNullOrEmpty(token)) {
                sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, UNAUTHORIZED));
                return;
            }

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

            byte[] bytes = new byte[12];
            random.nextBytes(bytes);
            String t = Base64.getEncoder().encodeToString(bytes);
            Cookie cookie = new DefaultCookie("access-token", t);
            cookie.setMaxAge(TimeUnit.SECONDS.toSeconds(8));

            Chat.Access.Builder builder = Chat.Access.newBuilder();
            builder.setGroupId(groupID);
            builder.setUser(user);
            metaService.setExpireToken(t.getBytes(), builder.build(), 8);
            sendRedirectResponse(ctx, request, "/index.html", cookie);
            return;
        }

        int index = p.lastIndexOf(".");
        if (index != -1 && STATIC_FILES.contains(p.substring(index + 1))) {
            ctx.fireChannelRead(request.retainedDuplicate());
            return;
        }

        sendHttpResponse(ctx, request, new DefaultFullHttpResponse(HTTP_1_1, NOT_FOUND));
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

    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest request, FullHttpResponse response) {
        ChannelFuture f = ctx.channel().writeAndFlush(response);
        if (!HttpUtil.isKeepAlive(request) || !response.status().equals(OK)) {
            f.addListener(ChannelFutureListener.CLOSE);
        }
    }

    private static void sendRedirectResponse(ChannelHandlerContext ctx, FullHttpRequest request, String location, Cookie cookie) {
        DefaultFullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, TEMPORARY_REDIRECT);
        response.headers().set(HttpHeaderNames.LOCATION, location);
        if (cookie != null) {
            response.headers().set(HttpHeaderNames.SET_COOKIE, ServerCookieEncoder.STRICT.encode(cookie));
        }
        ChannelFuture f = ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("{} caught an exception.", HttpHandler.class.getSimpleName(), cause);
        ctx.close();
    }


}

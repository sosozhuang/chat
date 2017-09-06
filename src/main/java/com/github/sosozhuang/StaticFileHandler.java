package com.github.sosozhuang;


import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class StaticFileHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StaticFileHandler.class);
    private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    private static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(HTTP_DATE_FORMAT).withLocale(Locale.US).withZone(ZoneId.of(HTTP_DATE_GMT_TIMEZONE));
    private static final int HTTP_CACHE_SECONDS = 600;

    @Override
    public void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendErrorResponse(ctx, BAD_REQUEST);
            return;
        }

        if (request.method() != GET) {
            sendErrorResponse(ctx, METHOD_NOT_ALLOWED);
            return;
        }

        final String uri = request.uri();
        final String path = sanitizeUri(uri);
        if (path == null) {
            sendErrorResponse(ctx, FORBIDDEN);
            return;
        }

        File file = new File(path);
        if (file.isHidden() || !file.exists() || file.isDirectory()) {
            sendErrorResponse(ctx, NOT_FOUND);
            return;
        }

        if (!ALLOWED_FILE_NAME.matcher(file.getName()).matches() || !file.isFile()) {
            sendErrorResponse(ctx, FORBIDDEN);
            return;
        }

        String ifModifiedSince = request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
        if (!StringUtil.isNullOrEmpty(ifModifiedSince)) {
            LocalDateTime ifModifiedSinceDate = LocalDateTime.from(FORMATTER.parse(ifModifiedSince));
            LocalDateTime lastModified =
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.of(HTTP_DATE_GMT_TIMEZONE));
            if (lastModified.isAfter(ifModifiedSinceDate)) {
                sendNotModified(ctx);
                return;
            }
        }

        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException e) {
            sendErrorResponse(ctx, NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpUtil.setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        // Write the initial line and the header.
        ctx.write(response);

        // Write the content.
        ChannelFuture lastContentFuture;
        if (ctx.pipeline().get(SslHandler.class) == null) {
            ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength));
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            lastContentFuture =
                    ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)));
        }

        if (!HttpUtil.isKeepAlive(request)) {
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.error("{} caught an exception.", StaticFileHandler.class.getSimpleName(), cause);
        if (ctx.channel().isActive()) {
            sendErrorResponse(ctx, INTERNAL_SERVER_ERROR);
        }
    }

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");
    private static final String STATIC_DIR = StaticFileHandler.class.getResource("/static").getPath();

    private static String sanitizeUri(String uri) {
        String p = null;
        try {
            p = new URI(uri).getPath();
        } catch (URISyntaxException e) {
            throw new Error(e);
        }

        if (p.isEmpty() || p.charAt(0) != '/') {
            return null;
        }

        p = p.replace('/', File.separatorChar);

        if (p.contains(File.separator + '.') ||
                p.contains('.' + File.separator) ||
                p.charAt(0) == '.' || p.charAt(p.length() - 1) == '.' ||
                INSECURE_URI.matcher(p).matches()) {
            return null;
        }

        return Paths.get(STATIC_DIR, p).toString();
    }

    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[^-\\._]?[^<>&\\\"]*");


    private static void sendErrorResponse(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendNotModified(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
        response.headers().set(HttpHeaderNames.DATE, FORMATTER.format(Instant.now()));

        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        Instant now = Instant.now();
        response.headers().set(HttpHeaderNames.DATE, FORMATTER.format(now));

        Instant expire = now.plusSeconds(HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaderNames.EXPIRES, FORMATTER.format(expire));
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(
                HttpHeaderNames.LAST_MODIFIED, FORMATTER.format(Instant.ofEpochMilli(fileToCache.lastModified())));
    }

    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }
}
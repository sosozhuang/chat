package com.github.sosozhuang.handler;

import com.github.sosozhuang.protobuf.Chat;
import com.github.sosozhuang.service.MessageRecord;
import com.github.sosozhuang.service.MessageService;
import com.github.sosozhuang.service.MetaService;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.googlecode.protobuf.format.JsonFormat;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ChatHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);
    private static final Map<String, ChannelGroup> CHANNEL_GROUP_MAP = new ConcurrentHashMap<>();
    private static final JsonFormat JSON_FORMAT = new JsonFormat();
    private ChannelGroup channels;
    private Chat.Group group;
    private String user;
    private final long serverID;
    private final MetaService metaService;
    private final MessageService messageService;
    private int i;

    public ChatHandler(long serverID, MetaService metaService, MessageService messageService) {
        this.serverID = serverID;
        this.metaService = metaService;
        this.messageService = messageService;
    }

    public static void receiveMessage(Chat.Message message) {
        ChannelGroup channelGroup = CHANNEL_GROUP_MAP.get(message.getGroupId());
        if (channelGroup == null) {
            return;
        }
        WebSocketFrame out = messageToWebSocketFrame(message);
        for (Channel c : channelGroup) {
            c.writeAndFlush(out.retainedDuplicate());
        }
        out.release();
    }

    private void userLogin(ChannelHandlerContext ctx) {
        String groupID = group.getId();
        if (!metaService.joinGroup(groupID, user)) {
            ctx.close();
            return;
        }

        long[] chat = new long[1];
        long[] lastLoginTime = new long[1];
        ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
            if (lastLoginTime[0] == 0L) {
                String value = metaService.lastLoginTime(groupID, user);
                if (StringUtil.isNullOrEmpty(value)) {
                    throw new NoMoreMessageException();
                }

                try {
                    lastLoginTime[0] = Math.max(Long.parseLong(value), System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L);
                } catch (NumberFormatException e) {
                    LOGGER.error("Parse last login time {} error.", value, e);
                    throw new NoMoreMessageException(e);
                }
            }

            Iterable<MessageRecord<String, byte[]>> records = records = messageService.receive(user, group, lastLoginTime[0]);
            if (records == null) {
                throw new NoMoreMessageException("time to stop task.");
            }

            Chat.Message message;
            long count = 0;
            for (MessageRecord<String, byte[]> record : records) {
                count++;
                try {
                    message = Chat.Message.parseFrom(record.getValue());
                } catch (InvalidProtocolBufferException e) {
                    LOGGER.error("Parse record to message error.", e);
                    continue;
                }

                if (Chat.MessageType.CHAT == message.getType() && groupID.equals(message.getGroupId())) {
                    chat[0]++;
                    ctx.write(messageToWebSocketFrame(message));
                }
            }
            if (count <= 0) {
                throw new NoMoreMessageException("time to stop task.");
            }
        }, 0, 80, TimeUnit.MILLISECONDS).addListener(future -> {
            Throwable cause = future.cause();
            if (cause != null) {
                if (cause instanceof NoMoreMessageException) {
                    if (chat[0] > 0) {
                        Chat.Message.Builder builder = Chat.Message.newBuilder();
                        builder.setType(Chat.MessageType.UNREAD);
                        builder.setServerId(serverID);
                        builder.setGroupId(groupID);
                        builder.setFromUser("");
                        builder.setContent(String.valueOf(chat[0]));
                        builder.setCreateAt(lastLoginTime[0]);
                        ctx.writeAndFlush(messageToWebSocketFrame(builder.build()));
                    }
                    LOGGER.info("Poll unread messages task completed.");
                    channels.add(ctx.channel());
                    metaService.setLastLoginTime(groupID, user, String.valueOf(System.currentTimeMillis()));
                } else {
                    ctx.fireExceptionCaught(cause);
                }
            }

        });

        Iterable<String> members = metaService.groupMembers(groupID);
        Chat.Message.Builder builder = Chat.Message.newBuilder();
        builder.setType(Chat.MessageType.MEMBERS);
        builder.setServerId(serverID);
        builder.setGroupId(groupID);
        builder.setFromUser("");
        builder.setContent("");
        builder.setCreateAt(0);
        builder.addAllMembers(members);
        ctx.writeAndFlush(messageToWebSocketFrame(builder.build()));

        Instant timestamp = Instant.now();
        builder.clear();
        builder.setType(Chat.MessageType.LOGIN);
        builder.setGroupId(group.getId());
        builder.setServerId(serverID);
        builder.setFromUser(user);
        builder.setCreateAt(timestamp.toEpochMilli());
        Chat.Message message = builder.build();

        WebSocketFrame frame = messageToWebSocketFrame(message);
        for (Channel c : channels) {
            c.writeAndFlush(frame.retainedDuplicate());
        }
        frame.release();
        messageService.send(user, group, new MessageRecord(group.getId(), message.toByteArray()));

        builder.setType(Chat.MessageType.CONFIRM);
        message = builder.build();
        ctx.writeAndFlush(messageToWebSocketFrame(message));

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (group != null && !StringUtil.isNullOrEmpty(user)) {
            String groupID = group.getId();
            if (!metaService.leaveGroup(groupID, user)) {
                LOGGER.warn("groupID {} does not contains user {}", groupID, user);
            }
            metaService.setLastLoginTime(groupID, user, String.valueOf(System.currentTimeMillis()));

            Instant timestamp = Instant.now();
            Chat.Message.Builder builder = Chat.Message.newBuilder();
            builder.setType(Chat.MessageType.LOGOUT);
            builder.setGroupId(group.getId());
            builder.setServerId(serverID);
            builder.setFromUser(user);
            builder.setCreateAt(timestamp.toEpochMilli());
            Chat.Message message = builder.build();

            WebSocketFrame out = messageToWebSocketFrame(message);
            for (Channel c : channels) {
                if (c != ctx.channel()) {
                    c.writeAndFlush(out.retainedDuplicate());
                }
            }
            out.release();

            messageService.send(user, group, new MessageRecord(group.getId(), message.toByteArray()));
        }
    }

    private static WebSocketFrame messageToWebSocketFrame(Message message) {
        return new TextWebSocketFrame(JSON_FORMAT.printToString(message));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame) {
            String content = ((TextWebSocketFrame) frame).text();
            if (channels == null) {
                Chat.Access access = metaService.getTokenThenDelete(content.getBytes());
                if (access == null) {
                    ctx.close();
                    return;
                }
                user = access.getUser();
                group = metaService.groupInfo(access.getGroupId());
                channels = CHANNEL_GROUP_MAP.computeIfAbsent(group.getId(), key -> {
                    return new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
                });
                userLogin(ctx);
                return;
            }

            if (":quit!".equals(content.toLowerCase())) {
                ctx.close();
            } else {
                Instant timestamp = Instant.now();
                Chat.Message.Builder builder = Chat.Message.newBuilder();
                builder.setType(Chat.MessageType.CHAT);
                builder.setContent(content);
                builder.setGroupId(group.getId());
                builder.setServerId(serverID);
                builder.setFromUser(user);
                builder.setCreateAt(timestamp.toEpochMilli());
                Chat.Message message = builder.build();
                WebSocketFrame out = messageToWebSocketFrame(message);

                for (Channel c : channels) {
                    if (c != ctx.channel()) {
                        c.writeAndFlush(out.retainedDuplicate());
                    }
                }
                out.release();

                messageService.send(user, group, new MessageRecord(group.getId(), message.toByteArray()));
            }
        } else {
            String message = "unsupported frame type: " + frame.getClass().getName();
            throw new UnsupportedOperationException(message);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        LOGGER.warn("{} caught an exception.", ChatHandler.class.getSimpleName(), cause);
        ctx.close();
    }

    private static class NoMoreMessageException extends RuntimeException {
        public NoMoreMessageException(String message, Throwable cause) {
            super(message, cause);
        }

        public NoMoreMessageException(String message) {
            super(message);
        }

        public NoMoreMessageException(Throwable cause) {
            super(cause);
        }

        public NoMoreMessageException() {
            super();
        }
    }
}

package com.github.sosozhuang;

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
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);
    private static final Map<String, ChannelGroup> CHANNEL_GROUP_MAP = new ConcurrentHashMap<>();
//    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final JsonFormat JSON_FORMAT = new JsonFormat();
    private ChannelGroup channels;
    private Chat.Group group;
    private String user;
    private final long serverID;
    private final MetaService metaService;
    private final MessageService messageService;

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
//        Instant timestamp = Instant.ofEpochMilli(message.getCreateAt());
//        String dateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(FORMATTER);

//        TextWebSocketFrame frame = null;
//        switch (message.getType()) {
//            case CHAT:
//                frame = new TextWebSocketFrame(dateTime + " [" + message.getFromUser() + "]: " + message.getContent() + "\n");
//                break;
//            case LOGIN:
//                frame = new TextWebSocketFrame(dateTime + " -->[" + message.getFromUser() + "] just joined.<--\n");
//                break;
//            case LOGOUT:
//                frame = new TextWebSocketFrame(dateTime + " <--[" + message.getFromUser() + "] just left.-->\n");
//                break;
//            default:
//                return;
//        }
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
        ctx.channel().eventLoop().submit(() -> {
            String value = metaService.lastLoginTime(groupID, user);
            if (StringUtil.isNullOrEmpty(value)) {
                return;
            }

            long lastLoginTime;
            try {
                lastLoginTime = Math.max(Long.parseLong(value), System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L);
            } catch (NumberFormatException e) {
                LOGGER.error("Parse last login time {} error.", value, e);
                return;
            }

            Iterable<MessageRecord<String, byte[]>> records = null;
            try {
                records = messageService.receive(user, group, lastLoginTime);
                if (records == null) {
                    return;
                }
            } catch (Exception e) {
                LOGGER.warn("Receive message since time {} error.",
                        lastLoginTime, e);
                return;
            }
            Chat.Message message;
            int count = 0;
//            Instant timestamp;
//            String dateTime;
            for (MessageRecord<String, byte[]> record : records) {
                try {
                    message = Chat.Message.parseFrom(record.getValue());
                } catch (InvalidProtocolBufferException e) {
                    LOGGER.error("Parse record to message error.", e);
                    continue;
                }

                if (Chat.MessageType.CHAT == message.getType() && groupID.equals(message.getGroupId())) {
//                    timestamp = Instant.ofEpochMilli(message.getCreateAt());
//                    dateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(FORMATTER);
                    count++;
//                    if (message.getFromUser().equals(user)) {
//                        ctx.write(new TextWebSocketFrame(dateTime + " #you#: " + message.getContent() + "\n"));
//                    } else {
//                        ctx.write(new TextWebSocketFrame(dateTime + " [" + message.getFromUser() + "]: " + message.getContent() + "\n"));
//                    }
                    ctx.write(messageToWebSocketFrame(message));
                }
            }
            if (count > 0) {
//                Instant timestamp = Instant.ofEpochMilli(lastLoginTime);
//                String dateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(FORMATTER);
//                ctx.writeAndFlush(new TextWebSocketFrame(count + " unread messages since " + dateTime + ".\n"));
                Chat.Message.Builder builder = Chat.Message.newBuilder();
                builder.setType(Chat.MessageType.UNREAD);
                builder.setServerId(serverID);
                builder.setGroupId(groupID);
                builder.setFromUser("");
                builder.setContent(String.valueOf(count));
                builder.setCreateAt(lastLoginTime);
                ctx.writeAndFlush(messageToWebSocketFrame(builder.build()));
            }

        }).addListener(future -> {
            channels.add(ctx.channel());
            if (future.isSuccess()) {
                metaService.setLastLoginTime(groupID, user, String.valueOf(System.currentTimeMillis()));
            }
        });

//        Long count = metaService.groupMembersCount(groupID);
//        if (count > 1) {
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

//            StringBuilder sb = new StringBuilder();
//            sb.append("Now you can chat with ");
//            for (String member : members) {
//                if (!user.equals(member)) {
//                    sb.append("[");
//                    sb.append(member);
//                    sb.append("], ");
//                }
//            }
//            sb.append(count);
//            sb.append(" members in the chatroom.\n");
//            ctx.writeAndFlush(new TextWebSocketFrame(sb.toString()));
//        } else {
//            ctx.writeAndFlush(new TextWebSocketFrame("Currently only yourself in the chatroom.\n"));
//        }

        Instant timestamp = Instant.now();
//        String dateTime = LocalDateTime.ofInstant(timestamp, ZoneId.systemDefault()).format(FORMATTER);
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
//                out = new TextWebSocketFrame(dateTime + " <--[" + user + "] just left.-->\n");

//                builder.setType(Chat.MessageType.LOGOUT);
            } else {
//                ctx.writeAndFlush(new TextWebSocketFrame(dateTime + " #you#: " + content + "\n"));
//                out = new TextWebSocketFrame(dateTime + " [" + user + "]: " + content + "\n");
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
}

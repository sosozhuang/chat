package com.github.sosozhuang.service;

import com.github.sosozhuang.protobuf.Chat;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public interface MetaService {
    public boolean registerServer(Chat.Server server);
    public boolean unRegisterServer(String serverID);
    public Chat.Server serverInfo(String serverID) throws IOException;
    public Iterable<Chat.Server> listServers() throws IOException;

    public Chat.Group groupInfo(String groupID) throws IOException;
    public String nextGroupID();
    public boolean createGroup(Chat.Group group);
    public boolean deleteGroup(String groupID);
    public boolean joinGroup(String groupID, String user);
    public boolean leaveGroup(String groupID, String user);
    public long groupMembersCount(String groupID);
    public Iterable<String> groupMembers(String groupID);
    public Iterable<String> groupMembers(String groupID, int limit);

    public String lastLoginTime(String groupID, String user);
    public void setLastLoginTime(String groupID, String user, String time);
    public void setToken(byte[] token, Chat.Access access);
    public void setExpireToken(byte[] token, Chat.Access access, int seconds);
    public Chat.Access getToken(byte[] token) throws IOException;
    public boolean deleteToken(byte[] token);
    public Chat.Access getTokenThenDelete(byte[] token) throws IOException;
}

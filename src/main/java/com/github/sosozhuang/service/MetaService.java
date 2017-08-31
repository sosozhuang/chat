package com.github.sosozhuang.service;

import com.github.sosozhuang.protobuf.Chat;

import java.io.Closeable;
import java.io.IOException;

public interface MetaService extends Closeable {
    public boolean registerServer(Chat.Server server);
    public boolean unRegisterServer(String serverID);

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
}

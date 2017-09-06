package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.RedisConfiguration;
import com.github.sosozhuang.protobuf.Chat;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RedisMetaService implements CloseableMetaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMetaService.class);
    private RedisConfiguration config;
    private volatile JedisCluster jedisCluster;
    private final byte[] SERVER_KEY;
    private final byte[] GROUP_KEY;
    private final String GROUP_MEMBER_KEY;
    private final String SEQUENCE_KEY;
    private final String LAST_LOGIN_TIME_KEY;
    private final byte[] TOKEN_KEY;

    public RedisMetaService(RedisConfiguration config) {
        this.config = config;
        String[] servers = config.getServers().split(",");
        Set<HostAndPort> nodes = new HashSet<>(servers.length);
        for (String server : servers) {
            String[] hostPort = server.split(":");
            if (hostPort.length != 2) {
                LOGGER.warn("Redis server configuration {} error.", server);
                continue;
            }
            try {
                nodes.add(new HostAndPort(hostPort[0].trim(), Integer.parseInt(hostPort[1])));
            } catch (NumberFormatException e) {
                LOGGER.warn("Parse server port {} to number error.", hostPort[1], e);
            }
        }
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(config.getMaxTotal(100));
        poolConfig.setMaxIdle(config.getMaxIdle(5));
        poolConfig.setMinIdle(config.getMinIdle(1));
        poolConfig.setMaxWaitMillis(config.getMaxWait(10000L));
        jedisCluster = new JedisCluster(nodes, config.getConnTimeout(10000),
                config.getSoTimeout(10000), config.getMaxAttempt(3), poolConfig);

        String prefix = config.getKeyPrefix("chat");
        String seperator = config.getKeySeparator("::");
        SERVER_KEY = String.format("%s%s%s", prefix, seperator, "svr").getBytes();
        GROUP_KEY = String.format("%s%s%s", prefix, seperator, "grp").getBytes();
        GROUP_MEMBER_KEY = String.format("%s%s%s%s", prefix, seperator, "grp", seperator);
        SEQUENCE_KEY = String.format("%s%s%s", prefix, seperator, "seq");
        LAST_LOGIN_TIME_KEY = String.format("%s%s%s%s", prefix, seperator, "llt", seperator);
        TOKEN_KEY = String.format("%s%s%s%s", prefix, seperator, "tok", seperator).getBytes();
    }

    @Override
    public boolean registerServer(Chat.Server server) {
        return jedisCluster.hsetnx(SERVER_KEY, server.getId().getBytes(), server.toByteArray()) == 1 ? true : false;
    }

    @Override
    public boolean unRegisterServer(String serverID) {
        return jedisCluster.hdel(SERVER_KEY, serverID.getBytes()) == 1 ? true : false;
    }

    @Override
    public Chat.Server serverInfo(String serverID) throws IOException {
        byte[] value = jedisCluster.hget(SERVER_KEY, serverID.getBytes());
        if (value == null) {
            return null;
        }
        return Chat.Server.parseFrom(value);
    }

    public Iterable<Chat.Server> listServers() {
        Map<byte[], byte[]> servers = jedisCluster.hgetAll(SERVER_KEY);
        if (servers == null || servers.size() == 0) {
            return null;
        }
        return servers.values().stream().map(server -> {
            try {
                return Chat.Server.parseFrom(server);
            } catch (InvalidProtocolBufferException e) {
                LOGGER.warn("Parse server info error.", e);
            }
            return null;
        }).collect(Collectors.toList());
    }

    @Override
    public Chat.Group groupInfo(String groupID) throws IOException {
        byte[] value = jedisCluster.hget(GROUP_KEY, groupID.getBytes());
        if (value == null) {
            return null;
        }
        return Chat.Group.parseFrom(value);
    }

    @Override
    public String nextGroupID() {
        return String.valueOf(jedisCluster.incr(SEQUENCE_KEY));
    }

    @Override
    public boolean createGroup(Chat.Group group) {
        return jedisCluster.hsetnx(GROUP_KEY, group.getId().getBytes(), group.toByteArray()) == 1L ? true : false;
    }

    @Override
    public boolean deleteGroup(String groupID) {
        return jedisCluster.hdel(GROUP_KEY, groupID.getBytes()) == 1L ? true : false;
    }

    @Override
    public boolean joinGroup(String groupID, String user) {
        return jedisCluster.sadd(GROUP_MEMBER_KEY + groupID, user) == 1L ? true : false;
    }

    @Override
    public boolean leaveGroup(String groupID, String user) {
        return jedisCluster.srem(GROUP_MEMBER_KEY + groupID, user) == 1L ? true : false;
    }

    @Override
    public long groupMembersCount(String groupID) {
        return jedisCluster.scard(GROUP_MEMBER_KEY + groupID);
    }

    @Override
    public Iterable<String> groupMembers(String groupID) {
        return jedisCluster.smembers(GROUP_MEMBER_KEY + groupID);
    }

    @Override
    public Iterable<String> groupMembers(String groupID, int limit) {
        return jedisCluster.srandmember(GROUP_MEMBER_KEY + groupID, limit);
    }

    @Override
    public String lastLoginTime(String groupID, String user) {
        return jedisCluster.hget(LAST_LOGIN_TIME_KEY + groupID, user);
    }

    @Override
    public void setLastLoginTime(String groupID, String user, String time) {
        jedisCluster.hset(LAST_LOGIN_TIME_KEY + groupID, user, time);
    }

    private byte[] formatTokenKey(byte[] token) {
        byte[] key = new byte[TOKEN_KEY.length + token.length];
        System.arraycopy(TOKEN_KEY, 0, key, 0, TOKEN_KEY.length);
        System.arraycopy(token, 0, key, TOKEN_KEY.length, token.length);
        return key;
    }

    @Override
    public void setToken(byte[] token, Chat.Access access) {
        jedisCluster.set(formatTokenKey(token), access.toByteArray());
    }

    @Override
    public void setExpireToken(byte[] token, Chat.Access access, int seconds) {
        jedisCluster.setex(formatTokenKey(token), seconds, access.toByteArray());
    }

    @Override
    public Chat.Access getToken(byte[] token) throws IOException {
        byte[] value = jedisCluster.get(formatTokenKey(token));
        if (value == null) {
            return null;
        }
        return Chat.Access.parseFrom(value);
    }

    @Override
    public boolean deleteToken(byte[] token) {
        return jedisCluster.del(formatTokenKey(token)) == 1L ? true : false;
    }

    @Override
    public Chat.Access getTokenThenDelete(byte[] token) throws IOException {
        byte[] key = formatTokenKey(token);
        if (!jedisCluster.exists(key)) {
            return null;
        }
        byte[] value = jedisCluster.getSet(key, new byte[]{});
        if (value == null || value.length == 0) {
            return null;
        }
        return Chat.Access.parseFrom(value);
    }

    @Override
    public void close() throws IOException {
        if (jedisCluster != null) {
            synchronized (this) {
                if (jedisCluster != null) {
                    jedisCluster.close();
                    jedisCluster = null;
                }
            }
        }
    }
}

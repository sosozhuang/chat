package com.github.sosozhuang.service;

import com.github.sosozhuang.conf.RedisConfiguration;
import com.github.sosozhuang.protobuf.Chat;
import io.netty.util.internal.StringUtil;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class RedisMetaService implements MetaService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisMetaService.class);
    private RedisConfiguration config;
    private volatile JedisCluster jedisCluster;
    private final String SERVER_KEY;
    private final String GROUP_KEY;
    private final String GROUP_MEMBER_KEY;
    private final String SEQUENCE_KEY;
    private final String LAST_LOGIN_TIME_KEY;

    public RedisMetaService(RedisConfiguration config) {
        this.config = config;
        String[] servers = config.getServers().split(",");
        Set<HostAndPort> nodes = new HashSet<>(servers.length);
        for (String server : servers) {
            String[] hostPort = server.split(":");
            if (hostPort.length != 2) {
                LOGGER.warn("{}", server);
                continue;
            }
            try {
                nodes.add(new HostAndPort(hostPort[0].trim(), Integer.parseInt(hostPort[1])));
            } catch (NumberFormatException e) {
                LOGGER.warn("Parse server port error", e);
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
        String seperator = config.getKeySeperator("::");
        SERVER_KEY = String.format("%s%s%s", prefix, seperator, "server");
        GROUP_KEY = String.format("%s%s%s", prefix, seperator, "group");
        GROUP_MEMBER_KEY = String.format("%s%s%s%s", prefix, seperator, "group", seperator);
        SEQUENCE_KEY = String.format("%s%s%s", prefix, seperator, "seq");
        LAST_LOGIN_TIME_KEY = String.format("%s%s%s%s", prefix, seperator, "llt", seperator);
    }

    @Override
    public boolean registerServer(Chat.Server server) {
        return jedisCluster.hsetnx(SERVER_KEY, server.getId(), server.toString()) == 1 ? true : false;
    }

    @Override
    public boolean unRegisterServer(String serverID) {
        return jedisCluster.hdel(SERVER_KEY, serverID) == 1 ? true : false;
    }

    @Override
    public Chat.Group groupInfo(String groupID) throws IOException {
        String value = jedisCluster.hget(GROUP_KEY, groupID);
        if (StringUtil.isNullOrEmpty(value)) {
            return null;
        }
        return Chat.Group.parseFrom(value.getBytes());
    }

    @Override
    public String nextGroupID() {
        return String.valueOf(jedisCluster.incr(SEQUENCE_KEY));
    }

    @Override
    public boolean createGroup(Chat.Group group) {
        return jedisCluster.hsetnx(GROUP_KEY, group.getId(), group.toString()) == 1 ? true : false;
    }

    @Override
    public boolean deleteGroup(String groupID) {
        return jedisCluster.hdel(GROUP_KEY, groupID) == 1 ? true : false;
    }

    @Override
    public boolean joinGroup(String groupID, String user) {
        return jedisCluster.sadd(GROUP_MEMBER_KEY + groupID, user) == 1 ? true : false;

    }

    @Override
    public boolean leaveGroup(String groupID, String user) {
        return jedisCluster.srem(GROUP_MEMBER_KEY + groupID, user) == 1 ? true : false;
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
        return jedisCluster.srandmember(GROUP_MEMBER_KEY, limit);
    }

    @Override
    public String lastLoginTime(String groupID, String user) {
        return jedisCluster.hget(LAST_LOGIN_TIME_KEY + groupID, user);
    }

    @Override
    public void setLastLoginTime(String groupID, String user, String time) {
        jedisCluster.hset(LAST_LOGIN_TIME_KEY + groupID, user, time);
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
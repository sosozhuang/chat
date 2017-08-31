package com.github.sosozhuang.conf;

public class RedisConfiguration {
    private Configuration config;
    public RedisConfiguration(Configuration config) {
        this.config = config;
    }

    public String getServers() {
        return getServers(null);
    }
    public String getServers(String defaultValue) {
        return config.getString("redis.servers", defaultValue);
    }

    public int getConnTimeout() {
        return getConnTimeout(0);
    }
    public int getConnTimeout(int defaultValue) {
        return config.getInteger("redis.conn_timeout", defaultValue);
    }

    public int getSoTimeout() {
        return getSoTimeout(0);
    }
    public int getSoTimeout(int defaultValue) {
        return config.getInteger("redis.so_timeout", defaultValue);
    }

    public int getMaxAttempt() {
        return getMaxAttempt(0);
    }
    public int getMaxAttempt(int defaultValue) {
        return config.getInteger("redis.max_attempt", defaultValue);
    }

    public int getMaxTotal() {
        return getMaxTotal(0);
    }
    public int getMaxTotal(int defaultValue) {
        return config.getInteger("redis.max_total", defaultValue);
    }

    public int getMaxIdle() {
        return getMaxIdle(0);
    }
    public int getMaxIdle(int defaultValue) {
        return config.getInteger("redis.max_idle", defaultValue);
    }

    public int getMinIdle() {
        return getMinIdle(0);
    }
    public int getMinIdle(int defaultValue) {
        return config.getInteger("redis.min_idle", defaultValue);
    }

    public long getMaxWait() {
        return getMaxWait(0);
    }
    public long getMaxWait(long defaultValue) {
        return config.getLong("redis.max_wait", defaultValue);
    }

    public String getKeyPrefix() {
        return getKeyPrefix(null);
    }
    public String getKeyPrefix(String defaultValue) {
        return config.getString("redis.key_prefix", defaultValue);
    }

    public String getKeySeperator() {
        return getKeySeperator(null);
    }
    public String getKeySeperator(String defaultValue) {
        return config.getString("redis.key_seperator", defaultValue);
    }

}

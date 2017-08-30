package com.github.sosozhuang.conf;

import java.util.HashMap;
import java.util.Map;

public class ServerConfiguration {
    private Configuration config;
    public ServerConfiguration(Configuration config) {
        this.config = config;
    }

    public String getHost() {
        return getHost(null);
    }

    public String getHost(String defaultValue) {
        return config.getString("server.host", defaultValue);
    }

    public int getPort() {
        return getPort(0);
    }

    public int getPort(int defaultValue) {
        return config.getInteger("server.port", defaultValue);
    }

    public long getId() {
        return getId(0);
    }

    public long getId(long defaultValue) {
        return config.getLong("server.id", defaultValue);
    }

    public boolean getSsl() {
        return getSsl(true);
    }

    public boolean getSsl(boolean defaultValue) {
        return config.getBoolean("server.ssl", defaultValue);
    }

    public String getCert() {
        return getCert(null);
    }
    public String getCert(String defaultValue) {
        return config.getString("server.cert", defaultValue);
    }

    public String getKey() {
        return getKey(null);
    }
    public String getKey(String defaultValue) {
        return config.getString("server.key", defaultValue);
    }

    public long getExecutorScheduleRate() {
        return getExecutorScheduleRate(0);
    }
    public long getExecutorScheduleRate(long defaultValue) {
        return config.getLong("server.executor.schedule_rate", defaultValue);
    }
}

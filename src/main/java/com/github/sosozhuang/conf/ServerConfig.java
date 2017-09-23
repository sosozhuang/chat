package com.github.sosozhuang.conf;

public class ServerConfig implements ServerConfigGetter {
    private Configuration config;
    private long id;
    public ServerConfig(Configuration config) {
        this.config = config;
    }

    public String getHost(String defaultValue) {
        return config.getString("server.host", defaultValue);
    }

    public int getPort(int defaultValue) {
        return config.getInteger("server.port", defaultValue);
    }

    public long getId(long defaultValue) {
        return config.getLong("server.id", defaultValue);
    }

    public void setId(long id) {
        config.setLong("server.id", id);
    }

    public boolean getSsl(boolean defaultValue) {
        return config.getBoolean("server.ssl", defaultValue);
    }

    public String getCert(String defaultValue) {
        return config.getString("server.cert", defaultValue);
    }

    public String getKey(String defaultValue) {
        return config.getString("server.key", defaultValue);
    }

    public long getExecutorScheduleRate(long defaultValue) {
        return config.getLong("server.executor.schedule_rate", defaultValue);
    }

    public boolean getIdleClose(boolean defaultValue) {
        return config.getBoolean("server.idle.close", defaultValue);
    }

    public long getIdleTimeout(long defaultValue) {
        return config.getLong("server.idle.timeout", defaultValue);
    }

    public String getWebsocketPath(String defaultValue) {
        return config.getString("server.websocket.path", defaultValue);
    }

    public String[] getStaticFiles(String[] defaultValues) {
        return config.getStringArray("server.static.files", defaultValues);
    }

    public boolean getTrafficShaping(boolean defaultValue) {
        return config.getBoolean("server.traffic.shaping", defaultValue);
    }

    @Override
    public long getTrafficLimit(long defaultValue) {
        return config.getLong("server.traffic.limit", defaultValue);
    }

    @Override
    public String toString() {
        return config.toString();
    }
}

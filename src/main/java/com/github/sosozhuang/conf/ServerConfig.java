package com.github.sosozhuang.conf;

public class ServerConfiguration implements ServerConfigurationGetter {
    private Configuration config;
    private long id;
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

    public void setId(long id) {
        config.setLong("server.id", id);
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

    public boolean getIdleClose() {
        return getIdleClose(false);
    }
    public boolean getIdleClose(boolean defauleValue) {
        return config.getBoolean("server.idle.close", defauleValue);
    }

    public long getIdleTimeout() {
        return getIdleTimeout(0);
    }
    public long getIdleTimeout(long defaultValue) {
        return config.getLong("server.idle.timeout", defaultValue);
    }

    public String getWebsocketPath() {
        return getWebsocketPath(null);
    }
    public String getWebsocketPath(String defaultValue) {
        return config.getString("server.websocket.path", defaultValue);
    }

    public String[] getStaticFiles() {
        return getStaticFiles(null);
    }
    public String[] getStaticFiles(String[] defaultValues) {
        return config.getStringArray("server.static.files", defaultValues);
    }

    @Override
    public String toString() {
        return config.toString();
    }
}

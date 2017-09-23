package com.github.sosozhuang.conf;

public interface ServerConfigGetter {
    default public String getHost() {
        return getHost(null);
    }

    public String getHost(String defaultValue);

    default public int getPort() {
        return getPort(80);
    }

    public int getPort(int defaultValue);

    default public long getId() {
        return getId(0);
    }

    public long getId(long defaultValue);

    default public boolean getSsl() {
        return getSsl(true);
    }

    public boolean getSsl(boolean defaultValue);

    default public String getCert() {
        return getCert(null);
    }
    public String getCert(String defaultValue);

    default public String getKey() {
        return getKey(null);
    }
    public String getKey(String defaultValue);

    default public long getExecutorScheduleRate() {
        return getExecutorScheduleRate(0);
    }
    public long getExecutorScheduleRate(long defaultValue);

    default public boolean getIdleClose() {
        return getIdleClose(false);
    }
    public boolean getIdleClose(boolean defaultValue);

    default public long getIdleTimeout() {
        return getIdleTimeout(0);
    }
    public long getIdleTimeout(long defaultValue);

    default public String getWebsocketPath() {
        return getWebsocketPath(null);
    }
    public String getWebsocketPath(String defaultValue);

    default public String[] getStaticFiles() {
        return getStaticFiles(null);
    }
    public String[] getStaticFiles(String[] defaultValues);

    default public boolean getTrafficShaping() {
        return getTrafficShaping(false);
    }
    public boolean getTrafficShaping(boolean defaultValue);

    default public long getTrafficLimit() {
        return getTrafficLimit(0);
    }
    public long getTrafficLimit(long defaultValue);
}

package com.github.sosozhuang.conf;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class Configuration {
    private static final String DEFAULT_CONFIG_FILE = "/config.properties";
    Map<String, String> params;

    private void initParams() {
        params = new HashMap<>();
    }
    public Configuration() throws IOException {
        this(DEFAULT_CONFIG_FILE);
    }

    public Configuration(Map<String, String> params) {
        initParams();
        this.params.putAll(params);
    }

    public Configuration(File file) throws IOException {
        initParams();
        Properties props = new Properties();
        props.load(new BufferedInputStream(new FileInputStream(file)));
        putAll(props);
    }

    public Configuration(String file) throws IOException {
        initParams();
        Properties props = new Properties();
        props.load(Configuration.class.getResourceAsStream(file));
        putAll(props);
    }

    private void putAll(Properties props) {
        Set<String> keys = props.stringPropertyNames();
        for (String key : keys) {
            this.params.put(key, props.getProperty(key));
        }
    }

    public String getString(String key) {
        return getString(key, null);
    }
    public String getString(String key, String defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    public short getShort(String key) {
        return getShort(key, (short) 0);
    }
    public short getShort(String key, short defaultValue) {
        String value = params.get(key);
        if (value != null) {
            return Short.parseShort(value);
        }
        return defaultValue;
    }

    public int getInteger(String key) {
        return getInteger(key, 0);
    }
    public int getInteger(String key, int defaultValue) {
        String value = params.get(key);
        if (value != null) {
            return Integer.parseInt(value);
        }
        return defaultValue;
    }

    public long getLong(String key) {
        return getLong(key, 0);
    }
    public long getLong(String key, long defaultValue) {
        String value = params.get(key);
        if (value != null) {
            return Long.parseLong(value);
        }
        return defaultValue;
    }

    public boolean getBoolean(String key) {
        return getBoolean(key, false);
    }
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = params.get(key);
        if (value != null) {
            return Boolean.parseBoolean(value);
        }
        return defaultValue;
    }

    @Override
    public String toString() {
        return params.toString();
    }
}

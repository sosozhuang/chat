package com.github.sosozhuang.service;

public interface Receiver {
    public static class KeyValueRecord<K, V> {
        private K key;
        private V value;
        public KeyValueRecord() {}
        public KeyValueRecord(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public void setKey(K key) {
            this.key = key;
        }

        public V getValue() {
            return value;
        }

        public void setValue(V value) {
            this.value = value;
        }
    }
    public <K, V> Iterable<KeyValueRecord<K, V>> receive() throws Exception;
}

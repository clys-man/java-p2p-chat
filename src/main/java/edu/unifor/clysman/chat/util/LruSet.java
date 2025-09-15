package edu.unifor.clysman.chat.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class LruSet<T> {
    private final int capacity;
    private final LinkedHashMap<T, Boolean> map;

    public LruSet(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<T, Boolean>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<T, Boolean> eldest) {
                return size() > LruSet.this.capacity;
            }
        };
    }

    public synchronized boolean add(T value) {
        boolean exists = map.containsKey(value);
        map.put(value, Boolean.TRUE);
        return !exists;
    }
}
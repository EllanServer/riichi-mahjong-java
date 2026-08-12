package top.ellan.mahjong.rules.riichi.internal;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Small bounded cache that never waits on a provider-owned monitor. */
final class BoundedConcurrentCache<K, V> {
    private final int maximumSize;
    private final ConcurrentMap<K, V> values;

    BoundedConcurrentCache(int maximumSize) {
        if (maximumSize <= 0) throw new IllegalArgumentException("maximumSize must be positive");
        this.maximumSize = maximumSize;
        values = new ConcurrentHashMap<>(Math.min(maximumSize, 128));
    }

    V get(K key) {
        return values.get(key);
    }

    void put(K key, V value) {
        V existing = values.putIfAbsent(key, value);
        if (existing == null && values.size() > maximumSize) {
            values.remove(key, value);
        }
    }
}

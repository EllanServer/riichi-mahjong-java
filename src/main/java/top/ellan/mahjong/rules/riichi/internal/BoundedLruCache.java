package top.ellan.mahjong.rules.riichi.internal;

import java.util.LinkedHashMap;

/** Small synchronized cache for expensive deterministic backend calls. */
final class BoundedLruCache<K, V> {
    private final int maximumSize;
    private final LinkedHashMap<K, V> values;

    BoundedLruCache(int maximumSize) {
        if (maximumSize <= 0) throw new IllegalArgumentException("maximumSize must be positive");
        this.maximumSize = maximumSize;
        values = new LinkedHashMap<>(128, 0.75f, true);
    }

    synchronized V get(K key) {
        return values.get(key);
    }

    synchronized void put(K key, V value) {
        values.put(key, value);
        if (values.size() > maximumSize) {
            values.pollFirstEntry();
        }
    }
}

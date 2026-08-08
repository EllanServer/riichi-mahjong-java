package top.ellan.mahjong.rules.riichi.model;

/** Compact identity of one physical tile within a deterministic game. */
public record TileId(long value) implements Comparable<TileId> {
    public TileId {
        if (value < 0) {
            throw new IllegalArgumentException("tile id must be non-negative");
        }
    }

    @Override
    public int compareTo(TileId other) {
        return Long.compare(value, other.value);
    }
}

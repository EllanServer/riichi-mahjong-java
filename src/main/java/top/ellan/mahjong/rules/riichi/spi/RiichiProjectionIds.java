package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;

/** Per-hand opaque scene IDs produced by a deterministic Sattolo permutation. */
public final class RiichiProjectionIds {
    public static final int PHYSICAL_TILE_COUNT = 136;

    private final short[] byPhysicalId;
    private final short[] byProjection;

    private RiichiProjectionIds(short[] byPhysicalId, short[] byProjection) {
        this.byPhysicalId = byPhysicalId;
        this.byProjection = byProjection;
    }

    public static RiichiProjectionIds forHand(long handSalt) {
        short[] values = new short[PHYSICAL_TILE_COUNT];
        for (short id = 0; id < values.length; id++) {
            values[id] = id;
        }
        StableRandom random = new StableRandom(handSalt ^ 0x5249_4348_5052_4f4aL);
        for (int index = values.length - 1; index > 0; index--) {
            int other = random.nextInt(index);
            short swap = values[index];
            values[index] = values[other];
            values[other] = swap;
        }
        short[] inverse = new short[PHYSICAL_TILE_COUNT];
        for (int physical = 0; physical < values.length; physical++) {
            inverse[values[physical]] = (short) physical;
        }
        return new RiichiProjectionIds(values, inverse);
    }

    public long project(TileInstance tile) {
        if (tile == null) {
            throw new IllegalArgumentException("tile is required");
        }
        return byPhysicalId[Math.toIntExact(tile.id().value())];
    }

    public long project(TileId tile) {
        if (tile == null) {
            throw new IllegalArgumentException("tile is required");
        }
        return byPhysicalId[Math.toIntExact(tile.value())];
    }

    public TileId resolve(long projection) {
        if (projection < 0 || projection >= PHYSICAL_TILE_COUNT) {
            throw new IllegalArgumentException("invalid projection id: " + projection);
        }
        return new TileId(byProjection[(int) projection]);
    }

    private static final class StableRandom {
        private long state;

        private StableRandom(long seed) {
            state = seed;
        }

        private long nextLong() {
            long value = state += 0x9E3779B97F4A7C15L;
            value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
            value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
            return value ^ value >>> 31;
        }

        private int nextInt(int bound) {
            long limit = Long.MAX_VALUE - Long.MAX_VALUE % bound;
            long candidate;
            do {
                candidate = nextLong() >>> 1;
            } while (candidate >= limit);
            return (int) (candidate % bound);
        }
    }
}

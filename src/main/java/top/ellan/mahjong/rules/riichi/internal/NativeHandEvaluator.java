package top.ellan.mahjong.rules.riichi.internal;

import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Allocation-light native shanten evaluator for the 34-kind Riichi tile set.
 *
 * <p>The recursive search mutates and restores one compact count vector. Public
 * inputs and results never expose that vector, so one evaluator instance is safe
 * for concurrent callers. The only shared state is the synchronized bounded
 * result cache.</p>
 */
final class NativeHandEvaluator implements HandEvaluator {
    private static final int KIND_COUNT = 34;
    private static final int FIRST_HONOR = 27;
    private static final int MAX_MELDS = 4;
    private static final int CACHE_SIZE = 4_096;
    private static final TileKind[] KINDS = TileKind.values();

    private final BoundedLruCache<AnalysisKey, HandAnalysis> cache =
            new BoundedLruCache<>(CACHE_SIZE);

    @Override
    public HandAnalysis analyze(List<Tile> concealedTiles, List<Meld> melds) {
        Objects.requireNonNull(concealedTiles, "concealedTiles");
        Objects.requireNonNull(melds, "melds");
        int meldCount = melds.size();
        if (meldCount > MAX_MELDS) {
            throw new IllegalArgumentException("a hand cannot contain more than four melds");
        }

        int concealedCount = concealedTiles.size();
        int expectedWithoutDraw = 13 - meldCount * 3;
        if (concealedCount != expectedWithoutDraw && concealedCount != expectedWithoutDraw + 1) {
            throw new IllegalArgumentException(
                    "invalid concealed tile count: expected " + expectedWithoutDraw
                            + " or " + (expectedWithoutDraw + 1) + ", got " + concealedCount);
        }

        byte[] concealed = new byte[KIND_COUNT];
        byte[] physical = new byte[KIND_COUNT];
        long[] exposedTileIds = new long[MAX_MELDS * 4];
        int exposedTileCount = 0;
        for (Tile tile : concealedTiles) {
            addPhysical(Objects.requireNonNull(tile, "concealed tile"), concealed, physical);
        }
        for (Meld meld : melds) {
            Objects.requireNonNull(meld, "meld");
            for (var tile : meld.tiles()) {
                long tileId = tile.id().value();
                for (int index = 0; index < exposedTileCount; index++) {
                    if (exposedTileIds[index] == tileId) {
                        throw new IllegalArgumentException(
                                "physical tile id appears in more than one meld: " + tile.id());
                    }
                }
                exposedTileIds[exposedTileCount++] = tileId;
                addPhysical(tile.tile(), null, physical);
            }
        }

        AnalysisKey key = AnalysisKey.of(concealed, physical, meldCount);
        HandAnalysis cached = cache.get(key);
        if (cached != null) return cached;

        int shanten = standardShanten(concealed, meldCount);
        if (meldCount == 0) {
            shanten = Math.min(shanten, sevenPairsShanten(concealed));
            shanten = Math.min(shanten, thirteenOrphansShanten(concealed));
        }

        Set<TileKind> waits = Set.of();
        if (shanten == 0 && concealedCount == expectedWithoutDraw) {
            EnumSet<TileKind> candidates = EnumSet.noneOf(TileKind.class);
            for (int kind = 0; kind < KIND_COUNT; kind++) {
                if (physical[kind] >= 4) continue;
                concealed[kind]++;
                if (isComplete(concealed, meldCount)) candidates.add(KINDS[kind]);
                concealed[kind]--;
            }
            waits = Set.copyOf(candidates);
        }

        HandAnalysis result = new HandAnalysis(shanten, waits);
        cache.put(key, result);
        return result;
    }

    private static void addPhysical(Tile tile, byte[] concealed, byte[] physical) {
        int kind = tile.kind().ordinal();
        if (physical[kind] >= 4) {
            throw new IllegalArgumentException("more than four physical copies of " + tile.kind());
        }
        physical[kind]++;
        if (concealed != null) concealed[kind]++;
    }

    private static int standardShanten(byte[] counts, int openMelds) {
        StandardSearch search = new StandardSearch(counts, openMelds);
        search.run(0, 0, 0, false);
        return search.best;
    }

    private static int sevenPairsShanten(byte[] counts) {
        int pairs = 0;
        int distinct = 0;
        for (byte count : counts) {
            if (count > 0) distinct++;
            if (count >= 2) pairs++;
        }
        return 6 - pairs + Math.max(0, 7 - distinct);
    }

    private static int thirteenOrphansShanten(byte[] counts) {
        int unique = 0;
        boolean pair = false;
        for (int kind = 0; kind < KIND_COUNT; kind++) {
            if (!isTerminalOrHonor(kind) || counts[kind] == 0) continue;
            unique++;
            if (counts[kind] >= 2) pair = true;
        }
        return 13 - unique - (pair ? 1 : 0);
    }

    private static boolean isComplete(byte[] counts, int openMelds) {
        if (openMelds == 0
                && (isSevenPairsComplete(counts) || isThirteenOrphansComplete(counts))) {
            return true;
        }
        int concealedMelds = MAX_MELDS - openMelds;
        for (int kind = 0; kind < KIND_COUNT; kind++) {
            if (counts[kind] < 2) continue;
            counts[kind] -= 2;
            boolean complete = removeMelds(counts, concealedMelds);
            counts[kind] += 2;
            if (complete) return true;
        }
        return false;
    }

    private static boolean removeMelds(byte[] counts, int remaining) {
        int kind = firstOccupied(counts, 0);
        if (kind == KIND_COUNT) return remaining == 0;
        if (remaining == 0) return false;

        if (counts[kind] >= 3) {
            counts[kind] -= 3;
            boolean complete = removeMelds(counts, remaining - 1);
            counts[kind] += 3;
            if (complete) return true;
        }
        if (canStartSequence(kind) && counts[kind + 1] > 0 && counts[kind + 2] > 0) {
            counts[kind]--;
            counts[kind + 1]--;
            counts[kind + 2]--;
            boolean complete = removeMelds(counts, remaining - 1);
            counts[kind]++;
            counts[kind + 1]++;
            counts[kind + 2]++;
            return complete;
        }
        return false;
    }

    private static boolean isSevenPairsComplete(byte[] counts) {
        int pairs = 0;
        for (byte count : counts) {
            if (count == 0) continue;
            if (count != 2) return false;
            pairs++;
        }
        return pairs == 7;
    }

    private static boolean isThirteenOrphansComplete(byte[] counts) {
        int unique = 0;
        boolean pair = false;
        for (int kind = 0; kind < KIND_COUNT; kind++) {
            if (isTerminalOrHonor(kind)) {
                if (counts[kind] == 0) return false;
                unique++;
                if (counts[kind] == 2) pair = true;
                else if (counts[kind] != 1) return false;
            } else if (counts[kind] != 0) {
                return false;
            }
        }
        return unique == 13 && pair;
    }

    private static boolean isTerminalOrHonor(int kind) {
        return kind >= FIRST_HONOR || kind % 9 == 0 || kind % 9 == 8;
    }

    private static boolean canStartSequence(int kind) {
        return kind < FIRST_HONOR && kind % 9 <= 6;
    }

    private static int firstOccupied(byte[] counts, int start) {
        int kind = start;
        while (kind < KIND_COUNT && counts[kind] == 0) kind++;
        return kind;
    }

    /** Full decomposition search for the standard four-meld-and-a-pair family. */
    private static final class StandardSearch {
        private final byte[] counts;
        private final int openMelds;
        private int best = 8;

        private StandardSearch(byte[] counts, int openMelds) {
            this.counts = counts;
            this.openMelds = openMelds;
        }

        private void run(int start, int melds, int taatsu, boolean pair) {
            int totalMelds = openMelds + melds;
            int usefulTaatsu = Math.min(taatsu, MAX_MELDS - totalMelds);
            best = Math.min(best, 8 - totalMelds * 2 - usefulTaatsu - (pair ? 1 : 0));
            if (best == -1) return;

            int kind = firstOccupied(counts, start);
            if (kind == KIND_COUNT) return;

            if (totalMelds < MAX_MELDS && counts[kind] >= 3) {
                counts[kind] -= 3;
                run(kind, melds + 1, taatsu, pair);
                counts[kind] += 3;
            }
            if (totalMelds < MAX_MELDS && canStartSequence(kind)
                    && counts[kind + 1] > 0 && counts[kind + 2] > 0) {
                counts[kind]--;
                counts[kind + 1]--;
                counts[kind + 2]--;
                run(kind, melds + 1, taatsu, pair);
                counts[kind]++;
                counts[kind + 1]++;
                counts[kind + 2]++;
            }
            if (counts[kind] >= 2) {
                counts[kind] -= 2;
                if (!pair) run(kind, melds, taatsu, true);
                if (totalMelds + taatsu < MAX_MELDS) run(kind, melds, taatsu + 1, pair);
                counts[kind] += 2;
            }
            if (totalMelds + taatsu < MAX_MELDS && kind < FIRST_HONOR) {
                int rank = kind % 9;
                if (rank <= 7 && counts[kind + 1] > 0) {
                    counts[kind]--;
                    counts[kind + 1]--;
                    run(kind, melds, taatsu + 1, pair);
                    counts[kind]++;
                    counts[kind + 1]++;
                }
                if (rank <= 6 && counts[kind + 2] > 0) {
                    counts[kind]--;
                    counts[kind + 2]--;
                    run(kind, melds, taatsu + 1, pair);
                    counts[kind]++;
                    counts[kind + 2]++;
                }
            }

            counts[kind]--;
            run(kind, melds, taatsu, pair);
            counts[kind]++;
        }
    }

    private record AnalysisKey(
            long concealedLow,
            long concealedHigh,
            long physicalLow,
            long physicalHigh,
            int meldCount) {

        private static AnalysisKey of(byte[] concealed, byte[] physical, int meldCount) {
            return new AnalysisKey(
                    packLow(concealed), packHigh(concealed),
                    packLow(physical), packHigh(physical), meldCount);
        }

        private static long packLow(byte[] counts) {
            long packed = 0;
            for (int kind = 0; kind < 21; kind++) {
                packed |= (long) counts[kind] << (kind * 3);
            }
            return packed;
        }

        private static long packHigh(byte[] counts) {
            long packed = 0;
            for (int kind = 21; kind < KIND_COUNT; kind++) {
                packed |= (long) counts[kind] << ((kind - 21) * 3);
            }
            return packed;
        }
    }
}

package top.ellan.mahjong.rules.riichi.spi;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import top.ellan.mahjong.rules.riichi.RiichiServices;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;

/**
 * Shanten-aware simulation helpers shared by every Riichi automated decision.
 *
 * <p>All scoring goes through the same {@link HandEvaluator} the referee uses, so a bot can never
 * believe in a shape the engine would reject.</p>
 */
final class RiichiBotDecisionService {
    /**
     * Distinct terminal-and-honour kinds required before the bot aborts the hand.
     *
     * <p>The rules allow nine; 1.5.0 only ever declared at eleven, keeping the marginal hands in
     * play, and that threshold is preserved here.</p>
     */
    private static final int NINE_TERMINALS_ABORT_THRESHOLD = 11;

    private final HandEvaluator evaluator = RiichiServices.handEvaluator();

    /** Analyses a hand that already has the shape the evaluator expects. */
    HandQuality quality(List<Tile> concealed, List<Meld> melds) {
        HandAnalysis analysis = evaluator.analyze(concealed, melds);
        return new HandQuality(analysis.shanten(), analysis.waits().size());
    }

    /**
     * Best quality reachable from a hand that holds one tile too many.
     *
     * <p>A claimed chow or pung leaves the claimant with a discard still to make, so the claim is
     * only worth as much as the best hand that discard can reach.</p>
     */
    HandQuality bestAfterDiscard(List<Tile> concealed, List<Meld> melds) {
        HandQuality best = null;
        EnumSet<TileKind> tried = EnumSet.noneOf(TileKind.class);
        ArrayList<Tile> remaining = new ArrayList<>(concealed.size() - 1);
        for (int index = 0; index < concealed.size(); index++) {
            Tile discarded = concealed.get(index);
            // Two copies of one kind give the same shape, and red fives never change shanten.
            if (!tried.add(discarded.kind())) {
                continue;
            }
            remaining.clear();
            for (int copy = 0; copy < concealed.size(); copy++) {
                if (copy != index) {
                    remaining.add(concealed.get(copy));
                }
            }
            HandQuality candidate = quality(remaining, melds);
            if (best == null || candidate.betterThan(best)) {
                best = candidate;
            }
        }
        return best == null ? new HandQuality(8, 0) : best;
    }

    /** True when the concealed hand is wide enough that 1.5.0 would have aborted it. */
    boolean shouldAbortNineTerminals(List<TileInstance> hand) {
        EnumSet<TileKind> distinct = EnumSet.noneOf(TileKind.class);
        for (TileInstance tile : hand) {
            if (tile.tile().kind().isTerminalOrHonor()) {
                distinct.add(tile.tile().kind());
            }
        }
        return distinct.size() >= NINE_TERMINALS_ABORT_THRESHOLD;
    }

    static List<Tile> logical(List<TileInstance> hand) {
        ArrayList<Tile> tiles = new ArrayList<>(hand.size());
        for (TileInstance tile : hand) {
            tiles.add(tile.tile());
        }
        return tiles;
    }

    /** Shanten first, then wait width. */
    record HandQuality(int shanten, int waits) {
        boolean betterThan(HandQuality other) {
            if (shanten != other.shanten) {
                return shanten < other.shanten;
            }
            return waits > other.waits;
        }

        boolean strictlyBetterThan(HandQuality other) {
            return shanten < other.shanten;
        }
    }
}

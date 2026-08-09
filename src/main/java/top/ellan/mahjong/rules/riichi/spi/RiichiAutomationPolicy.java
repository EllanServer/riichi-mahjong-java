package top.ellan.mahjong.rules.riichi.spi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.rules.riichi.RiichiServices;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.spi.AutomatedPlayerActions;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.ScheduledRuleAction;

/** Shanten-aware deterministic baseline for Riichi bots and trustee control. */
final class RiichiAutomationPolicy {
    private static final Duration WIN_DELAY = Duration.ofMillis(250);
    private static final Duration REACTION_DELAY = Duration.ofMillis(400);
    private static final Duration DISCARD_DELAY = Duration.ofMillis(900);

    private final HandEvaluator evaluator = RiichiServices.handEvaluator();

    Optional<ScheduledRuleAction> next(
            RiichiProviderState state, List<AutomatedPlayerActions> candidates) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        for (AutomatedPlayerActions candidate : candidates) {
            LegalAction action = select(state, candidate);
            if (action != null) {
                return Optional.of(new ScheduledRuleAction(
                        candidate.actor(), action.action(), delay(action), "automation.riichi"));
            }
        }
        return Optional.empty();
    }

    private LegalAction select(
            RiichiProviderState state, AutomatedPlayerActions candidate) {
        LegalAction win = keyed(candidate.legalActions(), "declare_tsumo");
        if (win == null) {
            win = keyed(candidate.legalActions(), "ron");
        }
        if (win != null) {
            return win;
        }
        LegalAction skip = keyed(candidate.legalActions(), "skip");
        if (skip != null) {
            return skip;
        }
        try {
            LegalAction evaluated = evaluatedDiscard(state, candidate);
            return evaluated == null ? fallbackDiscard(state, candidate) : evaluated;
        } catch (RuntimeException evaluatorFailure) {
            return fallbackDiscard(state, candidate);
        }
    }

    private LegalAction evaluatedDiscard(
            RiichiProviderState state, AutomatedPlayerActions candidate) {
        int initialSeat = state.initialSeat(candidate.actor());
        if (initialSeat < 0) {
            return null;
        }
        PlayerId actor = state.match().seats().get(initialSeat);
        List<TileInstance> hand = state.match().roundState().concealedHand(actor);
        List<Meld> melds = state.match().roundState().publicSnapshot().melds().get(actor);
        ArrayList<Tile> concealed = new ArrayList<>(Math.max(0, hand.size() - 1));
        LegalAction selected = null;
        DiscardQuality best = null;
        for (LegalAction action : candidate.legalActions()) {
            TileId discard = projectedDiscard(state, action);
            if (discard == null) {
                continue;
            }
            TileInstance discarded = null;
            concealed.clear();
            for (TileInstance tile : hand) {
                if (tile.id().equals(discard)) {
                    discarded = tile;
                } else {
                    concealed.add(tile.tile());
                }
            }
            if (discarded == null) {
                continue;
            }
            HandAnalysis analysis = evaluator.analyze(concealed, melds);
            DiscardQuality quality = new DiscardQuality(
                    analysis.shanten(),
                    analysis.waits().size(),
                    action.key().startsWith("discard_riichi:"),
                    discarded.tile().red(),
                    keepValue(discarded.tile().kind(), hand),
                    discarded.tile().kind().ordinal());
            if (best == null || quality.betterThan(best)) {
                selected = action;
                best = quality;
            }
        }
        return selected;
    }

    private static LegalAction fallbackDiscard(
            RiichiProviderState state, AutomatedPlayerActions candidate) {
        int initialSeat = state.initialSeat(candidate.actor());
        if (initialSeat < 0) {
            return null;
        }
        PlayerId actor = state.match().seats().get(initialSeat);
        List<TileInstance> hand = state.match().roundState().concealedHand(actor);
        LegalAction selected = null;
        int selectedValue = Integer.MAX_VALUE;
        int selectedKind = Integer.MAX_VALUE;
        for (LegalAction action : candidate.legalActions()) {
            TileId discard = projectedDiscard(state, action);
            if (discard == null) {
                continue;
            }
            for (TileInstance tile : hand) {
                if (!tile.id().equals(discard)) {
                    continue;
                }
                int value = keepValue(tile.tile().kind(), hand) + (tile.tile().red() ? 1 : 0);
                int kind = tile.tile().kind().ordinal();
                if (value < selectedValue || (value == selectedValue && kind < selectedKind)) {
                    selected = action;
                    selectedValue = value;
                    selectedKind = kind;
                }
                break;
            }
        }
        return selected;
    }

    private static TileId projectedDiscard(RiichiProviderState state, LegalAction action) {
        if (!action.action().type().equals("discard")) {
            return null;
        }
        byte[] payload = action.action().payload();
        return payload.length == 2
                ? state.projectionIds().resolve(Byte.toUnsignedInt(payload[0]))
                : null;
    }

    private static int keepValue(TileKind tile, List<TileInstance> hand) {
        int value = tile.isHonor() ? -1 : 0;
        for (TileInstance other : hand) {
            TileKind kind = other.tile().kind();
            if (kind == tile) {
                value += 10;
            } else if (!tile.isHonor() && kind.suit() == tile.suit()) {
                int distance = Math.abs(kind.rank() - tile.rank());
                value += distance == 1 ? 5 : distance == 2 ? 2 : 0;
            }
        }
        return value;
    }

    private static Duration delay(LegalAction action) {
        if (action.key().equals("declare_tsumo") || action.key().equals("ron")) {
            return WIN_DELAY;
        }
        return action.key().equals("skip") ? REACTION_DELAY : DISCARD_DELAY;
    }

    private static LegalAction keyed(List<LegalAction> actions, String key) {
        for (LegalAction action : actions) {
            if (action.key().equals(key)) {
                return action;
            }
        }
        return null;
    }

    private record DiscardQuality(
            int shanten,
            int waits,
            boolean riichi,
            boolean red,
            int keepValue,
            int kind) {
        boolean betterThan(DiscardQuality other) {
            if (shanten != other.shanten) return shanten < other.shanten;
            if (waits != other.waits) return waits > other.waits;
            if (riichi != other.riichi) return riichi;
            if (red != other.red) return !red;
            if (keepValue != other.keepValue) return keepValue < other.keepValue;
            return kind < other.kind;
        }
    }
}

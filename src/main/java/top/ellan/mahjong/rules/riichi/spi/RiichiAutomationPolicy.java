package top.ellan.mahjong.rules.riichi.spi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.rules.riichi.engine.ReactionType;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.spi.AutomatedPlayerActions;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.ScheduledRuleAction;

/**
 * Shanten-aware deterministic baseline for Riichi bots and trustee control.
 *
 * <p>Aligned with the 1.5.0 bot contract: a win is always taken, a call is taken when the resulting
 * hand beats passing, a self kan is declared when it does not damage the hand, and the nine
 * terminals abort uses 1.5.0's eleven-kind threshold rather than the bare legal minimum.</p>
 */
final class RiichiAutomationPolicy {
    private static final Duration WIN_DELAY = Duration.ofMillis(250);
    private static final Duration REACTION_DELAY = Duration.ofMillis(400);
    private static final Duration DISCARD_DELAY = Duration.ofMillis(900);

    private final RiichiBotDecisionService brain = new RiichiBotDecisionService();

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

    private LegalAction select(RiichiProviderState state, AutomatedPlayerActions candidate) {
        LegalAction win = keyed(candidate.legalActions(), "declare_tsumo");
        if (win == null) {
            win = keyed(candidate.legalActions(), "ron");
        }
        if (win != null) {
            return win;
        }
        LegalAction skip = keyed(candidate.legalActions(), "skip");
        Seat seat = Seat.resolve(state, candidate.actor());
        if (seat == null) {
            return skip;
        }
        try {
            if (skip != null) {
                return claim(state, seat, candidate, skip);
            }
            return turnAction(state, seat, candidate);
        } catch (RuntimeException evaluatorFailure) {
            return skip != null ? skip : fallbackDiscard(state, candidate);
        }
    }

    /**
     * Chooses between passing and every offered chii, pon and open kan.
     *
     * <p>A call always costs the closed hand, so it needs a strict shanten gain; only a hand that is
     * already open takes a call for extra waits alone.</p>
     */
    private LegalAction claim(
            RiichiProviderState state,
            Seat seat,
            AutomatedPlayerActions candidate,
            LegalAction skip) {
        RiichiBotDecisionService.HandQuality passing =
                brain.quality(RiichiBotDecisionService.logical(seat.hand()), seat.melds());
        boolean alreadyOpen = seat.melds().stream().anyMatch(Meld::open);
        LegalAction best = null;
        RiichiBotDecisionService.HandQuality bestQuality = null;
        int bestPriority = Integer.MIN_VALUE;
        for (LegalAction action : candidate.legalActions()) {
            Claim claim = Claim.decode(state, seat, action);
            if (claim == null) {
                continue;
            }
            RiichiBotDecisionService.HandQuality quality = claim.exactShape()
                    ? brain.quality(claim.concealed(), claim.melds())
                    : brain.bestAfterDiscard(claim.concealed(), claim.melds());
            int priority = claim.priority();
            if (bestQuality == null
                    || quality.betterThan(bestQuality)
                    || (!quality.strictlyBetterThan(bestQuality)
                            && !bestQuality.betterThan(quality)
                            && priority > bestPriority)) {
                best = action;
                bestQuality = quality;
                bestPriority = priority;
            }
        }
        if (best == null) {
            return skip;
        }
        boolean worthIt = bestQuality.strictlyBetterThan(passing)
                || (alreadyOpen
                        && bestQuality.shanten() == passing.shanten()
                        && bestQuality.waits() > passing.waits());
        return worthIt ? best : skip;
    }

    /** Own-turn decision order: abort, self kan, then the best discard. */
    private LegalAction turnAction(
            RiichiProviderState state, Seat seat, AutomatedPlayerActions candidate) {
        LegalAction nineTerminals = keyed(candidate.legalActions(), "declare_nine_terminals");
        if (nineTerminals != null && brain.shouldAbortNineTerminals(seat.hand())) {
            return nineTerminals;
        }
        RiichiBotDecisionService.HandQuality baseline =
                brain.bestAfterDiscard(RiichiBotDecisionService.logical(seat.hand()), seat.melds());
        LegalAction kan = selfKan(state, seat, candidate, baseline);
        if (kan != null) {
            return kan;
        }
        LegalAction evaluated = evaluatedDiscard(state, seat, candidate);
        return evaluated == null ? fallbackDiscard(state, candidate) : evaluated;
    }

    /** Declares an ankan or kakan only when the resulting hand is no worse than discarding. */
    private LegalAction selfKan(
            RiichiProviderState state,
            Seat seat,
            AutomatedPlayerActions candidate,
            RiichiBotDecisionService.HandQuality baseline) {
        LegalAction best = null;
        RiichiBotDecisionService.HandQuality bestQuality = null;
        for (LegalAction action : candidate.legalActions()) {
            Claim kan = Claim.decodeSelfKan(state, seat, action);
            if (kan == null) {
                continue;
            }
            RiichiBotDecisionService.HandQuality quality =
                    brain.quality(kan.concealed(), kan.melds());
            if (quality.shanten() > baseline.shanten()
                    || (quality.shanten() == baseline.shanten()
                            && quality.waits() < baseline.waits())) {
                continue;
            }
            if (bestQuality == null || quality.betterThan(bestQuality)) {
                best = action;
                bestQuality = quality;
            }
        }
        return best;
    }

    private LegalAction evaluatedDiscard(
            RiichiProviderState state, Seat seat, AutomatedPlayerActions candidate) {
        List<TileInstance> hand = seat.hand();
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
            RiichiBotDecisionService.HandQuality shape = brain.quality(concealed, seat.melds());
            DiscardQuality quality = new DiscardQuality(
                    shape.shanten(),
                    shape.waits(),
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
        return action.key().equals("skip") || action.action().type().equals("respond")
                ? REACTION_DELAY
                : DISCARD_DELAY;
    }

    private static LegalAction keyed(List<LegalAction> actions, String key) {
        for (LegalAction action : actions) {
            if (action.key().equals(key)) {
                return action;
            }
        }
        return null;
    }

    /** One automated seat's authoritative view of its own hand. */
    private record Seat(PlayerId actor, List<TileInstance> hand, List<Meld> melds) {

        static Seat resolve(RiichiProviderState state, top.ellan.mahjong.spi.PlayerId actor) {
            int initialSeat = state.initialSeat(actor);
            if (initialSeat < 0) {
                return null;
            }
            PlayerId player = state.match().seats().get(initialSeat);
            List<Meld> melds =
                    state.match().roundState().publicSnapshot().melds().get(player);
            return new Seat(
                    player,
                    state.match().roundState().concealedHand(player),
                    melds == null ? List.of() : melds);
        }
    }

    /** A simulated post-call hand. */
    private record Claim(
            List<Tile> concealed, List<Meld> melds, boolean exactShape, int priority) {

        /** Decodes a chii, pon or open kan into the hand it would produce. */
        static Claim decode(RiichiProviderState state, Seat seat, LegalAction action) {
            if (!action.action().type().equals("respond")) {
                return null;
            }
            byte[] payload = action.action().payload();
            if (payload.length < 1) {
                return null;
            }
            int ordinal = Byte.toUnsignedInt(payload[0]);
            ReactionType[] types = ReactionType.values();
            if (ordinal >= types.length) {
                return null;
            }
            ReactionType type = types[ordinal];
            MeldType meldType = switch (type) {
                case PON -> MeldType.PON;
                case CHII -> MeldType.CHII;
                case MINKAN -> MeldType.MINKAN;
                case RON, SKIP -> null;
            };
            if (meldType == null) {
                return null;
            }
            Optional<TileInstance> claimed = state.match().roundState().pendingDiscard();
            if (claimed.isEmpty()) {
                return null;
            }
            List<TileInstance> consumed = consumed(state, seat, payload);
            if (consumed == null) {
                return null;
            }
            ArrayList<TileInstance> meldTiles = new ArrayList<>(consumed);
            meldTiles.add(claimed.get());
            Meld meld;
            try {
                meld = new Meld(
                        meldType,
                        meldTiles,
                        Optional.of(state.match().roundState().publicSnapshot().currentPlayer()),
                        Optional.of(claimed.get().id()));
            } catch (IllegalArgumentException impossibleMeld) {
                return null;
            }
            return new Claim(
                    remaining(seat, consumed),
                    appended(seat.melds(), meld),
                    meldType == MeldType.MINKAN,
                    switch (meldType) {
                        case MINKAN -> 2;
                        case PON -> 1;
                        default -> 0;
                    });
        }

        /** Decodes an own-turn ankan or kakan into the hand it would produce. */
        static Claim decodeSelfKan(RiichiProviderState state, Seat seat, LegalAction action) {
            if (!action.action().type().equals("declare_self_kan")) {
                return null;
            }
            byte[] payload = action.action().payload();
            if (payload.length != 1) {
                return null;
            }
            int ordinal = Byte.toUnsignedInt(payload[0]);
            TileKind[] kinds = TileKind.values();
            if (ordinal >= kinds.length) {
                return null;
            }
            TileKind kind = kinds[ordinal];
            ArrayList<TileInstance> matching = new ArrayList<>(4);
            for (TileInstance tile : seat.hand()) {
                if (tile.tile().kind() == kind) {
                    matching.add(tile);
                }
            }
            if (matching.size() >= 4) {
                List<TileInstance> quad = matching.subList(0, 4);
                Meld ankan;
                try {
                    ankan = new Meld(
                            MeldType.ANKAN, List.copyOf(quad), Optional.empty(), Optional.empty());
                } catch (IllegalArgumentException impossibleMeld) {
                    return null;
                }
                return new Claim(
                        remaining(seat, quad), appended(seat.melds(), ankan), true, 1);
            }
            if (matching.isEmpty()) {
                return null;
            }
            TileInstance fourth = matching.getFirst();
            ArrayList<Meld> melds = new ArrayList<>(seat.melds().size());
            boolean upgraded = false;
            for (Meld meld : seat.melds()) {
                if (!upgraded
                        && meld.type() == MeldType.PON
                        && meld.tiles().getFirst().tile().kind() == kind) {
                    ArrayList<TileInstance> tiles = new ArrayList<>(meld.tiles());
                    tiles.add(fourth);
                    try {
                        melds.add(new Meld(
                                MeldType.KAKAN, tiles, meld.claimedFrom(), meld.claimedTile()));
                    } catch (IllegalArgumentException impossibleMeld) {
                        return null;
                    }
                    upgraded = true;
                } else {
                    melds.add(meld);
                }
            }
            if (!upgraded) {
                return null;
            }
            return new Claim(remaining(seat, List.of(fourth)), List.copyOf(melds), true, 0);
        }

        private static List<TileInstance> consumed(
                RiichiProviderState state, Seat seat, byte[] payload) {
            ArrayList<TileInstance> consumed = new ArrayList<>(payload.length - 1);
            for (int index = 1; index < payload.length; index++) {
                TileId id = state.projectionIds().resolve(Byte.toUnsignedInt(payload[index]));
                TileInstance found = null;
                for (TileInstance tile : seat.hand()) {
                    if (tile.id().equals(id)) {
                        found = tile;
                        break;
                    }
                }
                if (found == null) {
                    return null;
                }
                consumed.add(found);
            }
            return consumed.isEmpty() ? null : consumed;
        }

        private static List<Tile> remaining(Seat seat, List<TileInstance> removed) {
            ArrayList<Tile> tiles = new ArrayList<>(seat.hand().size());
            for (TileInstance tile : seat.hand()) {
                boolean consumed = false;
                for (TileInstance gone : removed) {
                    if (gone.id().equals(tile.id())) {
                        consumed = true;
                        break;
                    }
                }
                if (!consumed) {
                    tiles.add(tile.tile());
                }
            }
            return tiles;
        }

        private static List<Meld> appended(List<Meld> melds, Meld addition) {
            ArrayList<Meld> result = new ArrayList<>(melds.size() + 1);
            result.addAll(melds);
            result.add(addition);
            return List.copyOf(result);
        }
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

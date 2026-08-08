package top.ellan.mahjong.rules.riichi.engine;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.evaluation.EvaluationException;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.scoring.Limit;
import top.ellan.mahjong.rules.riichi.scoring.RonPayment;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleInvariantRegressionTest {
    private static final PlayerId A = new PlayerId("a");
    private static final PlayerId B = new PlayerId("b");
    private static final PlayerId C = new PlayerId("c");
    private static final PlayerId D = new PlayerId("d");
    private static final List<PlayerId> SEATS = List.of(A, B, C, D);
    private static final HandEvaluator NOT_TENPAI = (hand, melds) -> new HandAnalysis(1, Set.of());
    private static final ScoreCalculator NEVER_SCORE = request -> {
        throw new AssertionError("score calculator should not be called");
    };

    @Test
    void chiiEnumeratesEveryPhysicalRedAndNonRedChoiceAndAcceptsEitherOrder() {
        long[] id = {0};
        TileInstance discarded = tile(id, Tile.of(TileKind.M4));
        TileInstance m3 = tile(id, Tile.of(TileKind.M3));
        TileInstance normalM5 = tile(id, Tile.of(TileKind.M5));
        TileInstance redM5 = tile(id, Tile.red(TileKind.M5));
        TileInstance m6 = tile(id, Tile.of(TileKind.M6));
        Scenario scenario = base(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, List.of(m3, normalM5, redM5, m6))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);

        assertTrue(round.apply(new RoundCommand.Discard(A, discarded.id(), false)).accepted());
        ReactionOptions options = round.availableReactions(B).orElseThrow();
        assertEquals(4, options.chiiChoices().size());
        assertEquals(2, options.chiiChoices().stream().filter(choice -> choice.contains(normalM5.id())).count());
        assertEquals(2, options.chiiChoices().stream().filter(choice -> choice.contains(redM5.id())).count());

        List<TileId> selected = options.chiiChoices().getFirst().reversed();
        CommandResult result = round.apply(new RoundCommand.Respond(
                B, new Reaction(ReactionType.CHII, selected)));
        assertTrue(result.accepted());
        assertEquals(1, result.snapshot().melds().get(B).size());
    }

    @Test
    void chiiForbidsSameTileAndForwardSujiKuikaeUntilALegalDiscard() {
        long[] id = {0};
        TileInstance discarded = tile(id, Tile.of(TileKind.M1));
        TileInstance retainedM1 = tile(id, Tile.of(TileKind.M1));
        TileInstance m2 = tile(id, Tile.of(TileKind.M2));
        TileInstance m3 = tile(id, Tile.of(TileKind.M3));
        TileInstance m4 = tile(id, Tile.of(TileKind.M4));
        TileInstance m9 = tile(id, Tile.of(TileKind.M9));
        Scenario scenario = base(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, List.of(retainedM1, m2, m3, m4, m9))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);

        assertTrue(round.apply(new RoundCommand.Discard(A, discarded.id(), false)).accepted());
        assertTrue(round.apply(new RoundCommand.Respond(B, new Reaction(
                ReactionType.CHII, List.of(m2.id(), m3.id())))).accepted());

        CommandResult sameTile = round.apply(new RoundCommand.Discard(B, retainedM1.id(), false));
        assertFalse(sameTile.accepted());
        assertEquals(RuleViolation.KUIKAE_FORBIDDEN, sameTile.violation().orElseThrow());
        CommandResult forwardSuji = round.apply(new RoundCommand.Discard(B, m4.id(), false));
        assertFalse(forwardSuji.accepted());
        assertEquals(RuleViolation.KUIKAE_FORBIDDEN, forwardSuji.violation().orElseThrow());
        assertTrue(round.apply(new RoundCommand.Discard(B, m9.id(), false)).accepted());
    }

    @Test
    void nineTerminalsCannotBeDeclaredOnThePlayersSecondTurn() {
        long[] id = {0};
        TileInstance firstDiscard = tile(id, Tile.of(TileKind.P2));
        List<TileInstance> aHand = new ArrayList<>(List.of(firstDiscard));
        for (TileKind kind : List.of(
                TileKind.M1, TileKind.M9, TileKind.P1, TileKind.P9, TileKind.S1,
                TileKind.S9, TileKind.EAST, TileKind.WHITE_DRAGON, TileKind.RED_DRAGON)) {
            aHand.add(tile(id, Tile.of(kind)));
        }
        Scenario scenario = base(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, aHand)
                .liveWall(List.of(
                        tile(id, Tile.of(TileKind.M2)), tile(id, Tile.of(TileKind.M3)),
                        tile(id, Tile.of(TileKind.M4)), tile(id, Tile.of(TileKind.M5))))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);

        assertTrue(round.apply(new RoundCommand.Discard(A, firstDiscard.id(), false)).accepted());
        for (PlayerId player : List.of(B, C, D)) {
            assertTrue(round.apply(new RoundCommand.Draw(player)).accepted());
            TileId drawn = round.concealedHand(player).getLast().id();
            assertTrue(round.apply(new RoundCommand.Discard(player, drawn, false)).accepted());
        }
        assertTrue(round.apply(new RoundCommand.Draw(A)).accepted());
        CommandResult result = round.apply(new RoundCommand.DeclareNineTerminals(A));
        assertFalse(result.accepted());
        assertEquals(RuleViolation.ILLEGAL_PHASE, result.violation().orElseThrow());
    }

    @Test
    void lastLiveWallDiscardAllowsRonOnlyAndSelfKanIsForbidden() {
        long[] id = {0};
        TileInstance discarded = tile(id, Tile.of(TileKind.P3));
        List<TileInstance> bTriplet = List.of(
                tile(id, Tile.of(TileKind.P3)),
                tile(id, Tile.of(TileKind.P3)),
                tile(id, Tile.of(TileKind.P3)));
        Scenario lastDiscard = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, bTriplet)
                .liveWall(List.of())
                .rinshan(List.of(tile(id, Tile.of(TileKind.EAST))))
                .build();
        RiichiRound round = RiichiRound.fromScenario(lastDiscard, NOT_TENPAI, NEVER_SCORE);
        CommandResult discardResult = round.apply(new RoundCommand.Discard(A, discarded.id(), false));
        assertTrue(discardResult.accepted());
        assertTrue(round.availableReactions(B).isEmpty());
        assertEquals(RoundPhase.ENDED, discardResult.snapshot().phase());

        long[] secondIds = {100};
        List<TileInstance> four = List.of(
                tile(secondIds, Tile.of(TileKind.M1)), tile(secondIds, Tile.of(TileKind.M1)),
                tile(secondIds, Tile.of(TileKind.M1)), tile(secondIds, Tile.of(TileKind.M1)));
        Scenario lastDraw = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, four)
                .liveWall(List.of())
                .rinshan(List.of(tile(secondIds, Tile.of(TileKind.SOUTH))))
                .build();
        RiichiRound selfKanRound = RiichiRound.fromScenario(lastDraw, NOT_TENPAI, NEVER_SCORE);
        CommandResult kan = selfKanRound.apply(new RoundCommand.DeclareSelfKan(A, TileKind.M1));
        assertFalse(kan.accepted());
        assertEquals(RuleViolation.ILLEGAL_PHASE, kan.violation().orElseThrow());
        assertEquals(0, kan.snapshot().kanCount());
    }

    @Test
    void rinshanDrawIsCarriedIntoTheIntegratedScoreRequest() {
        long[] id = {0};
        List<TileInstance> hand = new ArrayList<>();
        for (int copy = 0; copy < 4; copy++) hand.add(tile(id, Tile.of(TileKind.M1)));
        for (TileKind kind : List.of(
                TileKind.M2, TileKind.M3, TileKind.M4,
                TileKind.P2, TileKind.P3, TileKind.P4,
                TileKind.S2, TileKind.S3, TileKind.S4, TileKind.P9)) {
            hand.add(tile(id, Tile.of(kind)));
        }
        Scenario scenario = base(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, hand)
                .rinshan(List.of(tile(id, Tile.of(TileKind.P9))))
                .build();
        AtomicReference<ScoreRequest> captured = new AtomicReference<>();
        ScoreCalculator fixedTsumo = request -> {
            captured.set(request);
            return legalTsumo();
        };
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, fixedTsumo);

        assertTrue(round.apply(new RoundCommand.DeclareSelfKan(A, TileKind.M1)).accepted());
        assertTrue(round.apply(new RoundCommand.DeclareTsumo(A)).accepted());
        assertNotNull(captured.get());
        assertTrue(captured.get().rinshan());
    }

    @Test
    void failedPostMinkanDiscardDoesNotRevealDoraOrDiscardTheTile() {
        long[] id = {0};
        TileInstance discarded = tile(id, Tile.of(TileKind.M1));
        List<TileInstance> bHand = List.of(
                tile(id, Tile.of(TileKind.M1)), tile(id, Tile.of(TileKind.M1)),
                tile(id, Tile.of(TileKind.M1)), tile(id, Tile.of(TileKind.P2)));
        Scenario scenario = base(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, bHand)
                .rinshan(List.of(tile(id, Tile.of(TileKind.S9))))
                .doraIndicatorSequence(List.of(Tile.of(TileKind.EAST), Tile.of(TileKind.SOUTH)))
                .build();
        AtomicBoolean fail = new AtomicBoolean();
        HandEvaluator evaluator = (hand, melds) -> {
            if (fail.get()) throw new EvaluationException("forced after minkan");
            return new HandAnalysis(1, Set.of());
        };
        RiichiRound round = RiichiRound.fromScenario(scenario, evaluator, NEVER_SCORE);

        assertTrue(round.apply(new RoundCommand.Discard(A, discarded.id(), false)).accepted());
        assertTrue(round.apply(new RoundCommand.Respond(B, new Reaction(
                ReactionType.MINKAN, bHand.subList(0, 3).stream().map(TileInstance::id).toList()))).accepted());
        assertEquals(1, round.snapshot().revealedDoraCount());

        fail.set(true);
        TileId attempted = bHand.getLast().id();
        CommandResult rejected = round.apply(new RoundCommand.Discard(B, attempted, false));
        assertFalse(rejected.accepted());
        assertEquals(RuleViolation.EVALUATION_FAILED, rejected.violation().orElseThrow());
        assertEquals(1, rejected.snapshot().revealedDoraCount());
        assertTrue(round.concealedHand(B).stream().anyMatch(tile -> tile.id().equals(attempted)));
    }

    @Test
    void failedRonSettlementRestoresTheReactionWindowAndScores() {
        long[] id = {0};
        TileInstance discarded = tile(id, Tile.of(TileKind.P3));
        List<TileInstance> bHand = new ArrayList<>();
        for (TileKind kind : List.of(
                TileKind.M1, TileKind.M2, TileKind.M3, TileKind.M4, TileKind.M5, TileKind.M6,
                TileKind.M7, TileKind.M8, TileKind.M9, TileKind.P1, TileKind.P2, TileKind.P4, TileKind.P5)) {
            bHand.add(tile(id, Tile.of(kind)));
        }
        Scenario scenario = base(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, bHand)
                .build();
        HandEvaluator bWait = (hand, melds) -> hand.size() == 13
                ? new HandAnalysis(0, Set.of(TileKind.P3)) : new HandAnalysis(1, Set.of());
        ScoreCalculator overflowing = request -> new ScoreResult(
                true, true, true, List.of(), 1, 1, 30, 0, 0, 0, 0,
                Limit.NONE, Optional.of(new RonPayment(Integer.MAX_VALUE)), Optional.empty());
        RiichiRound round = RiichiRound.fromScenario(scenario, bWait, overflowing);

        assertTrue(round.apply(new RoundCommand.Discard(A, discarded.id(), false)).accepted());
        CommandResult first = round.apply(new RoundCommand.Respond(B, Reaction.ron()));
        assertFalse(first.accepted());
        assertEquals(RuleViolation.UNSUPPORTED_RULE_FLOW, first.violation().orElseThrow());
        assertEquals(RoundPhase.AWAITING_REACTIONS, first.snapshot().phase());
        assertEquals(Integer.valueOf(25_000), first.snapshot().scores().get(A));
        assertEquals(Integer.valueOf(25_000), first.snapshot().scores().get(B));
        assertTrue(round.availableReactions(B).orElseThrow().ron());

        CommandResult retry = round.apply(new RoundCommand.Respond(B, Reaction.ron()));
        assertFalse(retry.accepted());
        assertEquals(RuleViolation.UNSUPPORTED_RULE_FLOW, retry.violation().orElseThrow());
    }

    @Test
    void furitenKindsHaveTheirRequiredLifetimes() {
        FuritenState ownDiscard = new FuritenState();
        ownDiscard.recordOwnDiscard(TileKind.M3);
        assertTrue(ownDiscard.isFuriten(Set.of(TileKind.M3, TileKind.P4)));
        assertFalse(ownDiscard.isFuriten(Set.of(TileKind.P4)));

        FuritenState temporary = new FuritenState();
        temporary.missLegalRon(false);
        assertTrue(temporary.isFuriten(Set.of(TileKind.P4)));
        temporary.onOwnDraw();
        assertFalse(temporary.isFuriten(Set.of(TileKind.P4)));

        FuritenState riichi = new FuritenState();
        riichi.missLegalRon(true);
        riichi.onOwnDraw();
        assertTrue(riichi.isFuriten(Set.of(TileKind.P4)));
    }

    private static Scenario.Builder base(long[] id) {
        return Scenario.builder(SEATS)
                .liveWall(List.of(tile(id, Tile.of(TileKind.NORTH))))
                .rinshan(List.of(tile(id, Tile.of(TileKind.GREEN_DRAGON))));
    }

    private static TileInstance tile(long[] id, Tile tile) {
        return new TileInstance(new TileId(id[0]++), tile);
    }

    private static ScoreResult legalTsumo() {
        return new ScoreResult(
                true, true, true, List.of(), 1, 1, 30, 0, 0, 0, 0,
                Limit.NONE, Optional.empty(), Optional.of(new TsumoPayment(0, 1_000)));
    }
}

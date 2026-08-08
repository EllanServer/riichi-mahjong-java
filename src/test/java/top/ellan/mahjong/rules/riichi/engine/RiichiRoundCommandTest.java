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
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiichiRoundCommandTest {
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
    void deterministicStandardScenarioReplaysFromSeed() {
        Scenario left = Scenario.standard(top.ellan.mahjong.rules.riichi.model.RiichiRules.mahjongSoul(), SEATS, 42L);
        Scenario right = Scenario.standard(top.ellan.mahjong.rules.riichi.model.RiichiRules.mahjongSoul(), SEATS, 42L);
        assertEquals(left.hands(), right.hands());
        assertEquals(left.liveWall(), right.liveWall());
        assertEquals(14, left.hands().get(A).size());
        assertEquals(13, left.hands().get(B).size());
        assertEquals(69, left.liveWall().size());
        assertEquals(4, left.rinshan().size());
    }

    @Test
    void integratedDiscardPathDoesNotOfferOrAcceptFifthKan() {
        long[] id = {0};
        TileInstance discarded = tile(id, TileKind.M1);
        List<TileInstance> bHand = List.of(tile(id, TileKind.M1), tile(id, TileKind.M1), tile(id, TileKind.M1));
        Scenario scenario = baseScenario(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, bHand)
                .kanCounts(Map.of(A, 4))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);

        CommandResult discardResult = round.apply(new RoundCommand.Discard(A, discarded.id(), false));
        assertTrue(discardResult.accepted());
        ReactionOptions options = round.availableReactions(B).orElseThrow();
        assertTrue(options.pon());
        assertFalse(options.minkan());

        CommandResult kan = round.apply(new RoundCommand.Respond(
                B, new Reaction(ReactionType.MINKAN, bHand.stream().map(TileInstance::id).toList())));
        assertFalse(kan.accepted());
        assertEquals(RuleViolation.INVALID_REACTION_PAYLOAD, kan.violation().orElseThrow());
    }

    @Test
    void integratedReactionCannotBeOverwritten() {
        long[] id = {0};
        TileInstance discarded = tile(id, TileKind.P3);
        List<TileInstance> bHand = List.of(tile(id, TileKind.P3), tile(id, TileKind.P3));
        List<TileInstance> cHand = new ArrayList<>();
        for (TileKind kind : List.of(
                TileKind.M1, TileKind.M2, TileKind.M3, TileKind.M4, TileKind.M5, TileKind.M6,
                TileKind.M7, TileKind.M8, TileKind.M9, TileKind.P1, TileKind.P2, TileKind.P4, TileKind.P5)) {
            cHand.add(tile(id, kind));
        }
        Scenario scenario = baseScenario(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, bHand)
                .hand(C, cHand)
                .build();
        HandEvaluator cWaits = (hand, melds) -> hand.size() == 13
                ? new HandAnalysis(0, Set.of(TileKind.P3))
                : new HandAnalysis(1, Set.of());
        ScoreCalculator fixedRon = request -> new ScoreResult(
                true, true, true, List.of(), 1, 1, 30, 0, 0, 0, 0,
                Limit.NONE, Optional.of(new RonPayment(1_000)), Optional.empty());
        RiichiRound round = RiichiRound.fromScenario(scenario, cWaits, fixedRon);
        assertTrue(round.apply(new RoundCommand.Discard(A, discarded.id(), false)).accepted());
        assertTrue(round.apply(new RoundCommand.Respond(B, Reaction.skip())).accepted());

        CommandResult overwrite = round.apply(new RoundCommand.Respond(
                B, new Reaction(ReactionType.PON, bHand.stream().map(TileInstance::id).toList())));
        assertFalse(overwrite.accepted());
        assertEquals(RuleViolation.REACTION_ALREADY_SUBMITTED, overwrite.violation().orElseThrow());
        assertEquals(RoundPhase.AWAITING_REACTIONS, overwrite.snapshot().phase());
    }

    @Test
    void evaluatorFailureIsTypedAndDiscardIsNotApplied() {
        long[] id = {0};
        TileInstance discarded = tile(id, TileKind.S7);
        Scenario scenario = baseScenario(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .build();
        HandEvaluator failing = (hand, melds) -> {
            throw new EvaluationException("forced evaluator failure");
        };
        RiichiRound round = RiichiRound.fromScenario(scenario, failing, NEVER_SCORE);

        CommandResult result = round.apply(new RoundCommand.Discard(A, discarded.id(), false));
        assertFalse(result.accepted());
        assertEquals(RuleViolation.EVALUATION_FAILED, result.violation().orElseThrow());
        assertEquals(RoundPhase.AWAITING_DISCARD, result.snapshot().phase());
        assertEquals(1, result.snapshot().handSizes().get(A));
        assertTrue(result.snapshot().discards().get(A).isEmpty());
    }

    @Test
    void nineDistinctTerminalsAndHonorsAbortDeterministically() {
        long[] id = {0};
        List<TileInstance> hand = new ArrayList<>();
        for (TileKind kind : List.of(
                TileKind.M1, TileKind.M9, TileKind.P1, TileKind.P9, TileKind.S1,
                TileKind.S9, TileKind.EAST, TileKind.WHITE_DRAGON, TileKind.RED_DRAGON)) {
            hand.add(tile(id, kind));
        }
        Scenario scenario = baseScenario(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, hand)
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);
        CommandResult result = round.apply(new RoundCommand.DeclareNineTerminals(A));
        assertTrue(result.accepted());
        assertEquals(RoundPhase.ENDED, result.snapshot().phase());
        assertEquals(AbortiveDraw.NINE_TERMINALS, result.snapshot().abortiveDraw().orElseThrow());
    }

    @Test
    void tsumoSettlementUpdatesEveryPlayerExactlyOnce() {
        long[] id = {0};
        List<TileInstance> winnerHand = new ArrayList<>();
        for (int index = 0; index < 14; index++) {
            winnerHand.add(tile(id, TileKind.values()[index]));
        }
        Scenario scenario = baseScenario(id)
                .phase(RoundPhase.AWAITING_DISCARD)
                .honba(1)
                .riichiSticks(2)
                .hand(A, winnerHand)
                .build();
        ScoreCalculator fixed = request -> new ScoreResult(
                true, true, true, List.of(), 3, 3, 40, 0, 0, 0, 0,
                Limit.NONE, Optional.empty(), Optional.of(new TsumoPayment(0, 2_000)));
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, fixed);

        CommandResult result = round.apply(new RoundCommand.DeclareTsumo(A));
        assertTrue(result.accepted());
        assertEquals(Integer.valueOf(33_300), result.snapshot().scores().get(A));
        assertEquals(Integer.valueOf(22_900), result.snapshot().scores().get(B));
        assertEquals(Integer.valueOf(22_900), result.snapshot().scores().get(C));
        assertEquals(Integer.valueOf(22_900), result.snapshot().scores().get(D));
        assertEquals(4, result.snapshot().settlement().orElseThrow().playerDeltas().size());
    }

    private static Scenario.Builder baseScenario(long[] id) {
        LinkedHashMap<PlayerId, Integer> ignored = new LinkedHashMap<>();
        ignored.put(A, 0);
        return Scenario.builder(SEATS)
                .liveWall(List.of(tile(id, TileKind.NORTH)))
                .rinshan(List.of(tile(id, TileKind.GREEN_DRAGON)));
    }

    private static TileInstance tile(long[] id, TileKind kind) {
        return new TileInstance(new TileId(id[0]++), Tile.of(kind));
    }
}

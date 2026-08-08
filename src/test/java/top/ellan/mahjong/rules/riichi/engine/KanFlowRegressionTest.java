package top.ellan.mahjong.rules.riichi.engine;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
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
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KanFlowRegressionTest {
    private static final PlayerId A = new PlayerId("a");
    private static final PlayerId B = new PlayerId("b");
    private static final PlayerId C = new PlayerId("c");
    private static final PlayerId D = new PlayerId("d");
    private static final List<PlayerId> SEATS = List.of(A, B, C, D);
    private static final HandEvaluator NOT_TENPAI =
            (hand, melds) -> new HandAnalysis(1, Set.of());
    private static final ScoreCalculator NEVER_SCORE = request -> {
        throw new AssertionError("score calculator should not be called");
    };

    @Test
    void kakanOpensAChankanOnlyWindowAndRobbedTileNeverEntersTheMeld() {
        long[] id = {0};
        Meld pon = pon(id, TileKind.M1, B);
        TileInstance added = tile(id, TileKind.M1);
        List<TileInstance> waiting = distinctTiles(id, List.of(
                TileKind.M2, TileKind.M3, TileKind.M4,
                TileKind.P2, TileKind.P3, TileKind.P4,
                TileKind.S2, TileKind.S3, TileKind.S4,
                TileKind.EAST, TileKind.SOUTH, TileKind.WHITE_DRAGON, TileKind.GREEN_DRAGON));
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(added))
                .melds(A, List.of(pon))
                .hand(B, waiting)
                .liveWall(List.of(tile(id, TileKind.NORTH)))
                .rinshan(List.of(tile(id, TileKind.RED_DRAGON)))
                .build();
        HandEvaluator bWait = (hand, melds) -> hand.size() == 13
                ? new HandAnalysis(0, Set.of(TileKind.M1))
                : new HandAnalysis(1, Set.of());
        AtomicReference<ScoreRequest> scored = new AtomicReference<>();
        ScoreCalculator chankan = request -> {
            scored.set(request);
            return legalRon("CHANKAN");
        };
        RiichiRound round = RiichiRound.fromScenario(scenario, bWait, chankan);

        CommandResult declared = round.apply(new RoundCommand.DeclareSelfKan(A, TileKind.M1));

        assertTrue(declared.accepted());
        assertEquals(RoundPhase.AWAITING_REACTIONS, declared.snapshot().phase());
        ReactionOptions options = round.availableReactions(B).orElseThrow();
        assertTrue(options.ron());
        assertFalse(options.pon());
        assertFalse(options.minkan());
        assertTrue(options.chiiChoices().isEmpty());
        assertEquals(0, declared.snapshot().kanCount());

        CommandResult robbed = round.apply(new RoundCommand.Respond(B, Reaction.ron()));

        assertTrue(robbed.accepted());
        assertTrue(scored.get().chankan());
        assertEquals(Optional.of("CHANKAN"), robbed.snapshot().endReason());
        assertEquals(0, robbed.snapshot().kanCount());
        assertEquals(0, round.concealedHand(A).size());
        assertEquals(MeldType.PON, robbed.snapshot().melds().get(A).getFirst().type());
        assertEquals(Integer.valueOf(24_000), robbed.snapshot().scores().get(A));
        assertEquals(Integer.valueOf(26_000), robbed.snapshot().scores().get(B));
    }

    @Test
    void unrobbedKakanReplacesThePonAndUsesDelayedMahjongSoulDoraTiming() {
        long[] id = {100};
        Meld pon = pon(id, TileKind.P5, B);
        TileInstance added = tile(id, TileKind.P5);
        TileInstance rinshan = tile(id, TileKind.S9);
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(added))
                .melds(A, List.of(pon))
                .liveWall(List.of(tile(id, TileKind.NORTH)))
                .rinshan(List.of(rinshan))
                .doraIndicatorSequence(List.of(
                        tile(id, TileKind.EAST), tile(id, TileKind.SOUTH)))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);

        CommandResult result = round.apply(new RoundCommand.DeclareSelfKan(A, TileKind.P5));

        assertTrue(result.accepted());
        assertEquals(1, result.snapshot().kanCount());
        assertEquals(1, result.snapshot().revealedDoraCount());
        assertEquals(MeldType.KAKAN, result.snapshot().melds().get(A).getFirst().type());
        assertEquals(List.of(rinshan), round.concealedHand(A));

        CommandResult discarded = round.apply(new RoundCommand.Discard(A, rinshan.id(), false));
        assertTrue(discarded.accepted());
        assertEquals(2, discarded.snapshot().revealedDoraCount());
    }

    @Test
    void kokushiMayRobAnkanButAnOrdinaryRonMayNot() {
        long[] id = {200};
        List<TileInstance> fourEast = distinctTiles(
                id, List.of(TileKind.EAST, TileKind.EAST, TileKind.EAST, TileKind.EAST));
        List<TileInstance> kokushiWait = distinctTiles(id, List.of(
                TileKind.M1, TileKind.M1, TileKind.M9, TileKind.P1, TileKind.P9,
                TileKind.S1, TileKind.S9, TileKind.SOUTH, TileKind.WEST, TileKind.NORTH,
                TileKind.WHITE_DRAGON, TileKind.GREEN_DRAGON, TileKind.RED_DRAGON));
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, fourEast)
                .hand(B, kokushiWait)
                .liveWall(List.of(tile(id, TileKind.M2)))
                .rinshan(List.of(tile(id, TileKind.M3)))
                .build();
        HandEvaluator bWait = (hand, melds) -> hand.size() == 13
                ? new HandAnalysis(0, Set.of(TileKind.EAST))
                : new HandAnalysis(1, Set.of());
        RiichiRound kokushiRound = RiichiRound.fromScenario(
                scenario, bWait, request -> legalYakumanRon("KOKUSHIMUSO"));

        assertTrue(kokushiRound.apply(new RoundCommand.DeclareSelfKan(A, TileKind.EAST)).accepted());
        assertTrue(kokushiRound.availableReactions(B).orElseThrow().ron());
        CommandResult robbed = kokushiRound.apply(new RoundCommand.Respond(B, Reaction.ron()));
        assertTrue(robbed.accepted());
        assertEquals(3, kokushiRound.concealedHand(A).size());
        assertTrue(robbed.snapshot().melds().get(A).isEmpty());
        assertEquals(0, robbed.snapshot().kanCount());

        RiichiRound ordinaryRound = RiichiRound.fromScenario(
                scenario, bWait, request -> legalRon("CHANKAN"));
        CommandResult ordinary = ordinaryRound.apply(new RoundCommand.DeclareSelfKan(A, TileKind.EAST));
        assertTrue(ordinary.accepted());
        assertTrue(ordinaryRound.availableReactions(B).isEmpty());
        assertEquals(1, ordinary.snapshot().kanCount());
        assertEquals(MeldType.ANKAN, ordinary.snapshot().melds().get(A).getFirst().type());
    }

    @Test
    void riichiAnkanRequiresTheDrawnFourthTileAndAnUnchangedWaitSet() {
        HandEvaluator preservedWait = riichiKanEvaluator(Set.of(TileKind.P9));
        RiichiRound acceptedRound = advanceToRiichiFourthM1(preservedWait);

        CommandResult accepted = acceptedRound.apply(new RoundCommand.DeclareSelfKan(A, TileKind.M1));

        assertTrue(accepted.accepted());
        assertEquals(1, accepted.snapshot().kanCount());
        assertEquals(MeldType.ANKAN, accepted.snapshot().melds().get(A).getFirst().type());

        HandEvaluator changedWait = riichiKanEvaluator(Set.of(TileKind.S9));
        RiichiRound rejectedRound = advanceToRiichiFourthM1(changedWait);
        List<TileInstance> handBefore = rejectedRound.concealedHand(A);

        CommandResult rejected = rejectedRound.apply(new RoundCommand.DeclareSelfKan(A, TileKind.M1));

        assertFalse(rejected.accepted());
        assertEquals(RuleViolation.RIICHI_KAN_CHANGES_WAIT, rejected.violation().orElseThrow());
        assertEquals(handBefore, rejectedRound.concealedHand(A));
        assertEquals(0, rejected.snapshot().kanCount());
        assertTrue(rejected.snapshot().melds().get(A).isEmpty());
    }

    private static RiichiRound advanceToRiichiFourthM1(HandEvaluator evaluator) {
        long[] id = {400};
        ArrayList<TileInstance> initial = new ArrayList<>(distinctTiles(id, List.of(
                TileKind.M1, TileKind.M1, TileKind.M1,
                TileKind.M2, TileKind.M3, TileKind.M4,
                TileKind.P2, TileKind.P3, TileKind.P4,
                TileKind.S2, TileKind.S3, TileKind.S4,
                TileKind.P9)));
        TileInstance riichiDiscard = tile(id, TileKind.EAST);
        initial.add(riichiDiscard);
        TileInstance bDraw = tile(id, TileKind.S9);
        TileInstance cDraw = tile(id, TileKind.S8);
        TileInstance dDraw = tile(id, TileKind.S7);
        TileInstance fourthM1 = tile(id, TileKind.M1);
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, initial)
                .liveWall(List.of(bDraw, cDraw, dDraw, fourthM1, tile(id, TileKind.NORTH)))
                .rinshan(List.of(tile(id, TileKind.GREEN_DRAGON)))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, evaluator, NEVER_SCORE);

        assertTrue(round.apply(new RoundCommand.Discard(A, riichiDiscard.id(), true)).accepted());
        drawAndDiscard(round, B, bDraw);
        drawAndDiscard(round, C, cDraw);
        drawAndDiscard(round, D, dDraw);
        assertTrue(round.apply(new RoundCommand.Draw(A)).accepted());
        assertTrue(round.concealedHand(A).contains(fourthM1));
        return round;
    }

    private static HandEvaluator riichiKanEvaluator(Set<TileKind> afterKanWait) {
        return (hand, melds) -> {
            long m1 = hand.stream().filter(tile -> tile.kind() == TileKind.M1).count();
            if (hand.size() == 13 && m1 == 3) {
                return new HandAnalysis(0, Set.of(TileKind.P9));
            }
            if (hand.size() == 10 && melds.stream().anyMatch(meld -> meld.type() == MeldType.ANKAN)) {
                return new HandAnalysis(0, afterKanWait);
            }
            return new HandAnalysis(1, Set.of());
        };
    }

    private static void drawAndDiscard(RiichiRound round, PlayerId player, TileInstance expected) {
        assertTrue(round.apply(new RoundCommand.Draw(player)).accepted());
        assertTrue(round.apply(new RoundCommand.Discard(player, expected.id(), false)).accepted());
    }

    private static Meld pon(long[] id, TileKind kind, PlayerId source) {
        List<TileInstance> tiles = distinctTiles(id, List.of(kind, kind, kind));
        return new Meld(MeldType.PON, tiles, Optional.of(source), Optional.of(tiles.getFirst().id()));
    }

    private static List<TileInstance> distinctTiles(long[] id, List<TileKind> kinds) {
        return kinds.stream().map(kind -> tile(id, kind)).toList();
    }

    private static TileInstance tile(long[] id, TileKind kind) {
        return new TileInstance(new TileId(id[0]++), Tile.of(kind));
    }

    private static ScoreResult legalRon(String yaku) {
        return new ScoreResult(
                true,
                true,
                true,
                List.of(new YakuAward(yaku, 1, 0, false)),
                1,
                1,
                30,
                0,
                0,
                0,
                0,
                Limit.NONE,
                Optional.of(new RonPayment(1_000)),
                Optional.empty());
    }

    private static ScoreResult legalYakumanRon(String yaku) {
        return new ScoreResult(
                true,
                true,
                true,
                List.of(new YakuAward(yaku, 0, 1, false)),
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                Limit.YAKUMAN,
                Optional.of(new RonPayment(32_000)),
                Optional.empty());
    }
}

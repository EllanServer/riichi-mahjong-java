package top.ellan.mahjong.rules.riichi.engine;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.Limit;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiichiMatchProgressionTest {
    private static final PlayerId A = new PlayerId("a");
    private static final PlayerId B = new PlayerId("b");
    private static final PlayerId C = new PlayerId("c");
    private static final PlayerId D = new PlayerId("d");
    private static final List<PlayerId> SEATS = List.of(A, B, C, D);
    private static final RiichiMatchRules RULES = RiichiMatchRules.mahjongSoulHanchan();
    private static final HandEvaluator NOT_TENPAI =
            (hand, melds) -> new HandAnalysis(1, Set.of());
    private static final ScoreCalculator FIXED_TSUMO = request -> new ScoreResult(
            true,
            true,
            true,
            List.of(new YakuAward("TEST", 1, 0, false)),
            1,
            1,
            30,
            0,
            0,
            0,
            0,
            Limit.NONE,
            Optional.empty(),
            Optional.of(new TsumoPayment(1_000, 2_000)));

    @Test
    void nonDealerWinAdvancesDealerWhileDealerTenpaiDrawRepeatsWithHonba() {
        RiichiMatchPosition eastOne = RiichiMatchPosition.eastOne();
        RiichiMatchAdvance win = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                eastOne,
                win(scores(20_000, 35_000, 25_000, 20_000), Set.of(B), 0));

        assertFalse(win.matchEnded());
        assertFalse(win.dealerContinues());
        assertEquals(new RiichiMatchPosition(Wind.EAST, 2, 1, 0, 0), win.position());

        RiichiMatchAdvance draw = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                eastOne,
                draw(scores(25_000, 25_000, 25_000, 25_000), Set.of(A), 2));

        assertFalse(draw.matchEnded());
        assertTrue(draw.dealerContinues());
        assertEquals(new RiichiMatchPosition(Wind.EAST, 1, 0, 1, 2), draw.position());
    }

    @Test
    void allLastDealerTopEndsAndReceivesTheUnclaimedRiichiPool() {
        RiichiMatchPosition southFour = new RiichiMatchPosition(Wind.SOUTH, 4, 3, 2, 2);
        Map<PlayerId, Integer> scores = scores(24_000, 22_000, 21_000, 31_000);

        RiichiMatchAdvance advance = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                southFour,
                win(scores, Set.of(D), 2));

        assertTrue(advance.matchEnded());
        assertEquals(Optional.of("ALL_LAST_DEALER_TOP"), advance.endReason());
        assertEquals(Integer.valueOf(33_000), advance.scores().get(D));
        assertEquals(0, advance.position().riichiSticks());
        assertEquals(D, advance.ranking().getFirst());
    }

    @Test
    void allLastWithoutTargetEntersWestSuddenDeath() {
        RiichiMatchPosition southFour = new RiichiMatchPosition(Wind.SOUTH, 4, 3, 0, 0);

        RiichiMatchAdvance advance = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                southFour,
                win(scores(26_000, 27_000, 24_000, 23_000), Set.of(B), 0));

        assertFalse(advance.matchEnded());
        assertEquals(new RiichiMatchPosition(Wind.WEST, 1, 0, 0, 0), advance.position());
    }

    @Test
    void westRoundEndsAtTargetExceptForDealerMultiRonContinuation() {
        RiichiMatchPosition westOne = new RiichiMatchPosition(Wind.WEST, 1, 1, 0, 0);
        Map<PlayerId, Integer> suddenScores = scores(20_000, 29_000, 31_000, 20_000);

        RiichiMatchAdvance doubleRon = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                westOne,
                win(suddenScores, Set.of(B, C), 0));
        assertFalse(doubleRon.matchEnded());
        assertTrue(doubleRon.dealerContinues());
        assertEquals(new RiichiMatchPosition(Wind.WEST, 1, 1, 1, 0), doubleRon.position());

        RiichiMatchAdvance singleRon = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                westOne,
                win(suddenScores, Set.of(C), 0));
        assertTrue(singleRon.matchEnded());
        assertEquals(Optional.of("SUDDEN_DEATH_TARGET"), singleRon.endReason());
    }

    @Test
    void bustAndMaximumWestBoundaryEndTheMatch() {
        RiichiMatchAdvance bust = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                RiichiMatchPosition.eastOne(),
                win(scores(-1_000, 51_000, 25_000, 25_000), Set.of(B), 0));
        assertTrue(bust.matchEnded());
        assertEquals(Optional.of("BUST"), bust.endReason());

        RiichiMatchPosition westFour = new RiichiMatchPosition(Wind.WEST, 4, 2, 0, 0);
        RiichiMatchAdvance maximum = RiichiMatchProgression.advance(
                RULES,
                SEATS,
                westFour,
                win(scores(28_000, 24_000, 23_000, 25_000), Set.of(D), 0));
        assertTrue(maximum.matchEnded());
        assertEquals(Optional.of("MAXIMUM_WIND_COMPLETE"), maximum.endReason());
    }

    @Test
    void matchEngineOwnsRoundBoundariesAndRejectsCommandsOutsideTheActivePhase() {
        RiichiMatchState initial = RiichiMatchState.start(
                RULES, SEATS, 20260809L, NOT_TENPAI, FIXED_TSUMO);
        RiichiMatchEngine engine = new RiichiMatchEngine();

        RiichiMatchTransition endedRound = engine.applyRoundCommand(
                initial, new RoundCommand.DeclareTsumo(A));

        assertTrue(endedRound.accepted());
        assertEquals(RiichiMatchPhase.BETWEEN_ROUNDS, endedRound.state().phase());
        assertEquals(new RiichiMatchPosition(Wind.EAST, 1, 0, 1, 0), endedRound.state().position());
        assertEquals(1, endedRound.state().revision());
        assertEquals(RiichiMatchEvent.Type.ROUND_ENDED, endedRound.matchEvents().getFirst().type());

        RiichiMatchTransition rejected = engine.applyRoundCommand(
                endedRound.state(), new RoundCommand.DeclareTsumo(A));
        assertFalse(rejected.accepted());
        assertSame(endedRound.state(), rejected.state());

        RiichiMatchTransition next = engine.startNextRound(endedRound.state());
        assertTrue(next.accepted());
        assertEquals(RiichiMatchPhase.ACTIVE_ROUND, next.state().phase());
        assertEquals(1, next.state().handSerial());
        assertEquals(2, next.state().revision());
        assertEquals(14, next.state().roundState().concealedHand(A).size());
    }

    private static RiichiRoundResult win(
            Map<PlayerId, Integer> scores,
            Set<PlayerId> winners,
            int sticks) {
        return new RiichiRoundResult(
                scores,
                winners,
                Set.of(),
                Set.of(),
                Optional.empty(),
                "RON",
                sticks);
    }

    private static RiichiRoundResult draw(
            Map<PlayerId, Integer> scores,
            Set<PlayerId> tenpai,
            int sticks) {
        return new RiichiRoundResult(
                scores,
                Set.of(),
                tenpai,
                Set.of(),
                Optional.empty(),
                "EXHAUSTIVE_DRAW",
                sticks);
    }

    private static Map<PlayerId, Integer> scores(int a, int b, int c, int d) {
        LinkedHashMap<PlayerId, Integer> result = new LinkedHashMap<>();
        result.put(A, a);
        result.put(B, b);
        result.put(C, c);
        result.put(D, d);
        return Map.copyOf(result);
    }
}

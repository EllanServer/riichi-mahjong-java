package top.ellan.mahjong.rules.riichi.engine;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiichiRoundEngineTest {
    private static final PlayerId EAST = new PlayerId("east");
    private static final PlayerId SOUTH = new PlayerId("south");
    private static final PlayerId WEST = new PlayerId("west");
    private static final PlayerId NORTH = new PlayerId("north");
    private static final List<PlayerId> PLAYERS = List.of(EAST, SOUTH, WEST, NORTH);
    private static final HandEvaluator NOT_TENPAI =
            (hand, melds) -> new HandAnalysis(1, Set.of());
    private static final ScoreCalculator NEVER_SCORE = request -> {
        throw new AssertionError("score calculator should not be called");
    };

    private final RiichiRoundEngine engine = new RiichiRoundEngine();

    @Test
    void acceptedTransitionForksWithoutMutatingItsInputAndReplaysDeterministically() {
        RiichiRoundState initial = state();
        RoundSnapshot initialSnapshot = initial.publicSnapshot();
        List<TileInstance> initialHand = initial.concealedHand(EAST);
        RoundCommand command = new RoundCommand.Discard(EAST, initialHand.getFirst().id(), false);

        RiichiRoundTransition left = engine.apply(initial, command);
        RiichiRoundTransition right = engine.apply(initial, command);

        assertTrue(left.accepted());
        assertNotSame(initial, left.state());
        assertEquals(1, left.state().revision());
        assertEquals(initialSnapshot, initial.publicSnapshot());
        assertEquals(initialHand, initial.concealedHand(EAST));
        assertEquals(left.state().publicSnapshot(), right.state().publicSnapshot());
        assertEquals(left.events(), right.events());
    }

    @Test
    void rejectedTransitionReturnsTheIdenticalRevisionWithNoEvents() {
        RiichiRoundState initial = state();
        RoundCommand command = new RoundCommand.Discard(
                SOUTH, initial.concealedHand(SOUTH).getFirst().id(), false);

        RiichiRoundTransition result = engine.apply(initial, command);

        assertFalse(result.accepted());
        assertSame(initial, result.state());
        assertEquals(RuleViolation.NOT_YOUR_TURN, result.violation().orElseThrow());
        assertTrue(result.events().isEmpty());
    }

    @Test
    void parallelForksFromOneRevisionRemainIndependentAndDeterministic() {
        RiichiRoundState initial = state();
        RoundSnapshot initialSnapshot = initial.publicSnapshot();
        RoundCommand command = new RoundCommand.Discard(
                EAST, initial.concealedHand(EAST).getFirst().id(), false);

        List<RiichiRoundTransition> results = IntStream.range(0, 24)
                .parallel()
                .mapToObj(ignored -> engine.apply(initial, command))
                .toList();

        assertTrue(results.stream().allMatch(RiichiRoundTransition::accepted));
        RoundSnapshot expected = results.getFirst().state().publicSnapshot();
        assertTrue(results.stream().allMatch(result -> expected.equals(result.state().publicSnapshot())));
        assertTrue(results.stream().allMatch(result -> result.state().revision() == 1));
        assertEquals(initialSnapshot, initial.publicSnapshot());
        assertEquals(0, initial.revision());
    }

    private static RiichiRoundState state() {
        Scenario scenario = Scenario.standard(RiichiRules.mahjongSoul(), PLAYERS, 20260808L);
        return RiichiRoundState.start(scenario, NOT_TENPAI, NEVER_SCORE);
    }
}

package top.ellan.mahjong.rules.riichi.engine;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.TileId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class P0RuleRegressionTest {
    private static final PlayerId A = new PlayerId("a");
    private static final PlayerId B = new PlayerId("b");
    private static final PlayerId C = new PlayerId("c");

    @Test
    void selfKanSubmissionRejectsTheFifthKan() {
        KanTracker tracker = new KanTracker(Map.of(A, 4));
        assertFalse(tracker.canDeclareSelfKan());
        RuleViolationException error = assertThrows(
                RuleViolationException.class, () -> tracker.registerSelfKan(A));
        assertEquals(RuleViolation.FIFTH_KAN_FORBIDDEN, error.violation());
    }

    @Test
    void discardReactionNeverOffersOrAcceptsTheFifthKan() {
        KanTracker tracker = new KanTracker(Map.of(A, 4));
        ReactionOptions raw = new ReactionOptions(false, false, true, List.of());
        ReactionWindow window = new ReactionWindow(
                List.of(B), Map.of(B, raw), RiichiRules.RonMode.MULTI_RON, tracker);
        assertTrue(window.options().isEmpty());
        RuleViolationException error = assertThrows(
                RuleViolationException.class,
                () -> window.submit(B, new Reaction(ReactionType.MINKAN, List.of(
                        new TileId(1), new TileId(2), new TileId(3)))));
        assertEquals(RuleViolation.REACTION_WINDOW_CLOSED, error.violation());
    }

    @Test
    void fourthKanByMoreThanOnePlayerDefersAbortUntilDiscard() {
        KanTracker tracker = new KanTracker(Map.of(A, 3));
        assertEquals(KanTracker.Registration.ABORT_AFTER_DISCARD, tracker.registerDiscardKan(B));
        assertEquals(4, tracker.total());
    }

    @Test
    void playerCannotOverwriteAnEarlierReaction() {
        ReactionWindow window = new ReactionWindow(
                List.of(B, C),
                Map.of(
                        B, new ReactionOptions(true, false, false, List.of()),
                        C, new ReactionOptions(true, false, false, List.of())),
                RiichiRules.RonMode.MULTI_RON,
                new KanTracker());
        assertEquals(ReactionWindow.SubmitStatus.ACCEPTED, window.submit(B, Reaction.skip()));
        RuleViolationException error = assertThrows(
                RuleViolationException.class, () -> window.submit(B, Reaction.ron()));
        assertEquals(RuleViolation.REACTION_ALREADY_SUBMITTED, error.violation());
        assertEquals(ReactionType.SKIP, window.responses().get(B).type());
    }

    @Test
    void ronBeatsCallsAndMultiRonPreservesSeatPriority() {
        LinkedHashMap<PlayerId, ReactionOptions> options = new LinkedHashMap<>();
        options.put(B, new ReactionOptions(false, true, false, List.of()));
        options.put(C, new ReactionOptions(true, false, false, List.of()));
        ReactionWindow window = new ReactionWindow(
                List.of(B, C), options, RiichiRules.RonMode.MULTI_RON, new KanTracker());
        window.submit(B, new Reaction(ReactionType.PON, List.of(new TileId(1), new TileId(2))));
        window.submit(C, Reaction.ron());
        ReactionResolution resolution = window.resolution().orElseThrow();
        assertEquals(List.of(C), resolution.ronWinners());
        assertTrue(resolution.caller().isEmpty());
    }

    @Test
    void majsoulAndEarlyProfilesOnlyDifferForOpenKanTiming() {
        assertFalse(KanDoraPolicy.revealBeforeRinshan(RiichiRules.Profile.MAJSOUL, MeldType.MINKAN));
        assertTrue(KanDoraPolicy.revealBeforeNextDiscard(RiichiRules.Profile.MAJSOUL, MeldType.MINKAN));
        assertTrue(KanDoraPolicy.revealBeforeRinshan(RiichiRules.Profile.EARLY_KAN_DORA, MeldType.MINKAN));
        assertFalse(KanDoraPolicy.revealBeforeNextDiscard(RiichiRules.Profile.EARLY_KAN_DORA, MeldType.MINKAN));
        assertTrue(KanDoraPolicy.revealBeforeRinshan(RiichiRules.Profile.MAJSOUL, MeldType.ANKAN));
    }
}

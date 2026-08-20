package top.ellan.mahjong.rules.riichi.spi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.spi.AutomatedPlayerActions;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.MatchPlayer;
import top.ellan.mahjong.spi.MatchSeed;
import top.ellan.mahjong.spi.MatchSetup;
import top.ellan.mahjong.spi.PlayerId;
import top.ellan.mahjong.spi.RuleState;
import top.ellan.mahjong.spi.RuleTransition;
import top.ellan.mahjong.spi.ScheduledRuleAction;
import top.ellan.mahjong.spi.SeatId;

/**
 * Guards the decision quality of the Riichi automation, not merely its legality.
 *
 * <p>Before this suite existed the bot could pass on every call and never kan while still satisfying
 * the "returns some accepted action" smoke test, so those regressions were invisible.</p>
 */
class RiichiAutomationQualityTest {
    private static final int MAX_STEPS = 20_000;

    private final RiichiRulePackProvider provider = new RiichiRulePackProvider();
    private final List<MatchPlayer> players = players();

    @Test
    void nineTerminalsAbortsOnlyFromElevenDistinctTerminalKinds() {
        RiichiBotDecisionService brain = new RiichiBotDecisionService();

        assertTrue(
                brain.shouldAbortNineTerminals(hand(
                        TileKind.M1, TileKind.M9, TileKind.P1, TileKind.P9,
                        TileKind.S1, TileKind.S9, TileKind.EAST, TileKind.SOUTH,
                        TileKind.WEST, TileKind.NORTH, TileKind.WHITE_DRAGON, TileKind.M5,
                        TileKind.M6, TileKind.M7)),
                "eleven distinct terminal-or-honour kinds must abort");
        assertFalse(
                brain.shouldAbortNineTerminals(hand(
                        TileKind.M1, TileKind.M9, TileKind.P1, TileKind.P9,
                        TileKind.S1, TileKind.S9, TileKind.EAST, TileKind.SOUTH,
                        TileKind.WEST, TileKind.M4, TileKind.M5, TileKind.M6,
                        TileKind.P4, TileKind.P5)),
                "ten distinct kinds is legal to abort but 1.5.0 kept playing");
    }

    @Test
    void bestAfterDiscardFindsTheTenpaiDiscard() {
        RiichiBotDecisionService brain = new RiichiBotDecisionService();
        // 123m 456m 789m 11p2p3p plus an isolated 9s: dropping 9s leaves a tenpai hand.
        List<Tile> fourteen = tiles(
                TileKind.M1, TileKind.M2, TileKind.M3,
                TileKind.M4, TileKind.M5, TileKind.M6,
                TileKind.M7, TileKind.M8, TileKind.M9,
                TileKind.P1, TileKind.P1, TileKind.P2, TileKind.P3,
                TileKind.S9);

        RiichiBotDecisionService.HandQuality best = brain.bestAfterDiscard(fourteen, List.of());

        assertTrue(best.shanten() <= 0, "an isolated tile away from tenpai must reach tenpai");
        assertTrue(best.waits() > 0, "a tenpai hand must report its waits");
    }

    @Test
    void automatedMatchesCallMeldsAndDeclareKans() {
        Set<String> observed = new LinkedHashSet<>();
        for (long seed = 1; seed <= 6; seed++) {
            observed.addAll(drive(seed));
        }

        assertTrue(
                observed.stream().anyMatch(key -> key.equals("pon")
                        || key.startsWith("pon:")
                        || key.startsWith("chii")
                        || key.startsWith("minkan")),
                "the Riichi bot never called a meld across six matches: " + observed);
        assertTrue(
                observed.stream().anyMatch(key -> key.startsWith("declare_self_kan")),
                "the Riichi bot never declared a self kan across six matches: " + observed);
        assertFalse(observed.isEmpty());
    }

    /** Plays one fully automated match and returns every action key the bot chose. */
    private Set<String> drive(long seed) {
        RuleState state = provider.createMatch(new MatchSetup(
                RiichiRulePackProvider.PROFILE_ID,
                new MatchSeed(seed, seed * 31 + 7),
                players,
                Map.of()));
        Set<String> chosen = new LinkedHashSet<>();
        for (int step = 0; step < MAX_STEPS; step++) {
            ArrayList<AutomatedPlayerActions> candidates = new ArrayList<>(4);
            for (MatchPlayer player : players) {
                List<LegalAction> legal = provider.legalActions(state, player.playerId());
                if (!legal.isEmpty()) {
                    candidates.add(new AutomatedPlayerActions(player.playerId(), legal));
                }
            }
            ScheduledRuleAction next = null;
            if (!candidates.isEmpty()) {
                next = provider.automatedAction(state, candidates).orElse(null);
                if (next != null) {
                    chosen.add(keyOf(state, next));
                }
            }
            if (next == null) {
                next = provider.scheduledAction(state).orElse(null);
            }
            if (next == null) {
                return chosen;
            }
            RuleTransition transition = provider.transition(state, next.actor(), next.action());
            assertTrue(
                    transition.accepted(),
                    "automation produced a rejected action: " + keyOf(state, next)
                            + " actor=" + next.actor()
                            + " reason=" + transition.reasonCode()
                            + " candidates="
                            + candidates.stream()
                                    .map(entry -> entry.actor() + "->"
                                            + entry.legalActions().stream()
                                                    .map(LegalAction::key)
                                                    .toList())
                                    .toList());
            state = transition.nextState();
        }
        throw new AssertionError(
                "automated Riichi match did not finish within " + MAX_STEPS + " steps");
    }

    private String keyOf(RuleState state, ScheduledRuleAction scheduled) {
        for (LegalAction legal : provider.legalActions(state, scheduled.actor())) {
            if (legal.action().equals(scheduled.action())) {
                return legal.key();
            }
        }
        return scheduled.action().type();
    }

    private static List<Tile> tiles(TileKind... kinds) {
        ArrayList<Tile> result = new ArrayList<>(kinds.length);
        for (TileKind kind : kinds) {
            result.add(Tile.of(kind));
        }
        return List.copyOf(result);
    }

    private static List<TileInstance> hand(TileKind... kinds) {
        ArrayList<TileInstance> result = new ArrayList<>(kinds.length);
        for (int index = 0; index < kinds.length; index++) {
            result.add(new TileInstance(new TileId(index + 1), Tile.of(kinds[index])));
        }
        return List.copyOf(result);
    }

    private static List<MatchPlayer> players() {
        ArrayList<MatchPlayer> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            result.add(new MatchPlayer(
                    new PlayerId(new UUID(0, index + 1L)), new SeatId(index)));
        }
        return List.copyOf(result);
    }
}

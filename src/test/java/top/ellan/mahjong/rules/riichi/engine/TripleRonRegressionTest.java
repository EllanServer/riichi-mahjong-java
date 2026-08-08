package top.ellan.mahjong.rules.riichi.engine;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.scoring.Limit;
import top.ellan.mahjong.rules.riichi.scoring.RonPayment;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TripleRonRegressionTest {
    private static final PlayerId A = new PlayerId("a");
    private static final PlayerId B = new PlayerId("b");
    private static final PlayerId C = new PlayerId("c");
    private static final PlayerId D = new PlayerId("d");
    private static final List<PlayerId> SEATS = List.of(A, B, C, D);

    @Test
    void threeSimultaneousRonDeclarationsBecomeAnAbortiveDraw() {
        long[] id = {0};
        TileInstance discarded = tile(id, TileKind.P3);
        List<TileKind> pattern = List.of(
                TileKind.M1, TileKind.M2, TileKind.M3, TileKind.M4, TileKind.M5,
                TileKind.P1, TileKind.P2, TileKind.P3, TileKind.P4,
                TileKind.S1, TileKind.S2, TileKind.S3, TileKind.EAST);
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(discarded))
                .hand(B, hand(id, pattern))
                .hand(C, hand(id, pattern))
                .hand(D, hand(id, pattern))
                .liveWall(List.of(tile(id, TileKind.NORTH)))
                .build();
        RiichiRound round = RiichiRound.fromScenario(
                scenario,
                (hand, melds) -> hand.size() == 13
                        ? new HandAnalysis(0, Set.of(TileKind.P3))
                        : new HandAnalysis(1, Set.of()),
                request -> legalRon());

        assertTrue(round.apply(new RoundCommand.Discard(A, discarded.id(), false)).accepted());
        assertTrue(round.apply(new RoundCommand.Respond(B, Reaction.ron())).accepted());
        assertTrue(round.apply(new RoundCommand.Respond(C, Reaction.ron())).accepted());
        CommandResult result = round.apply(new RoundCommand.Respond(D, Reaction.ron()));

        assertTrue(result.accepted());
        assertEquals(Optional.of(AbortiveDraw.TRIPLE_RON), result.snapshot().abortiveDraw());
        assertEquals(Optional.of("TRIPLE_RON"), result.snapshot().endReason());
        assertTrue(result.snapshot().winners().isEmpty());
        assertTrue(result.snapshot().scores().values().stream().allMatch(score -> score == 25_000));
    }

    private static List<TileInstance> hand(long[] id, List<TileKind> kinds) {
        ArrayList<TileInstance> result = new ArrayList<>();
        kinds.forEach(kind -> result.add(tile(id, kind)));
        return List.copyOf(result);
    }

    private static TileInstance tile(long[] id, TileKind kind) {
        return new TileInstance(new TileId(id[0]++), Tile.of(kind));
    }

    private static ScoreResult legalRon() {
        return new ScoreResult(
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
                Optional.of(new RonPayment(1_000)),
                Optional.empty());
    }
}

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
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;
import top.ellan.mahjong.rules.riichi.settlement.PaymentReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExhaustiveAndPaoIntegrationTest {
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
    void exhaustiveDrawPaysTheThreeThousandPointNotenPoolAndCarriesRiichiSticks() {
        long[] id = {0};
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DRAW)
                .riichiSticks(2)
                .hand(A, List.of(tile(id, TileKind.M1)))
                .hand(B, List.of(tile(id, TileKind.P1)))
                .hand(C, List.of(tile(id, TileKind.M2)))
                .hand(D, List.of(tile(id, TileKind.P2)))
                .liveWall(List.of())
                .build();
        HandEvaluator evaluator = (hand, melds) -> hand.stream()
                .anyMatch(tile -> tile.kind() == TileKind.M1 || tile.kind() == TileKind.P1)
                ? new HandAnalysis(0, Set.of(TileKind.S9))
                : new HandAnalysis(1, Set.of());
        RiichiRound round = RiichiRound.fromScenario(scenario, evaluator, NEVER_SCORE);

        CommandResult result = round.apply(new RoundCommand.Draw(A));

        assertTrue(result.accepted());
        assertEquals(Optional.of("EXHAUSTIVE_DRAW"), result.snapshot().endReason());
        assertEquals(Set.of(A, B), result.snapshot().tenpaiPlayers());
        assertTrue(result.snapshot().nagashiWinners().isEmpty());
        assertEquals(Integer.valueOf(26_500), result.snapshot().scores().get(A));
        assertEquals(Integer.valueOf(26_500), result.snapshot().scores().get(B));
        assertEquals(Integer.valueOf(23_500), result.snapshot().scores().get(C));
        assertEquals(Integer.valueOf(23_500), result.snapshot().scores().get(D));
        assertEquals(2, result.snapshot().riichiSticks());
        assertTrue(result.snapshot().settlement().orElseThrow().transfers().stream()
                .allMatch(transfer -> transfer.reason() == PaymentReason.NOTEN));
    }

    @Test
    void dealerNagashiManganUsesTsumoPaymentsAndDoesNotConsumeRiichiPool() {
        long[] id = {100};
        TileInstance aDraw = tile(id, TileKind.M1);
        TileInstance bDraw = tile(id, TileKind.M2);
        TileInstance cDraw = tile(id, TileKind.M3);
        TileInstance dDraw = tile(id, TileKind.M4);
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DRAW)
                .riichiSticks(1)
                .liveWall(List.of(aDraw, bDraw, cDraw, dDraw))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);

        drawAndDiscard(round, A, aDraw);
        drawAndDiscard(round, B, bDraw);
        drawAndDiscard(round, C, cDraw);
        CommandResult result = drawAndDiscard(round, D, dDraw);

        assertEquals(Optional.of("NAGASHI_MANGAN"), result.snapshot().endReason());
        assertEquals(Set.of(A), result.snapshot().nagashiWinners());
        assertEquals(Integer.valueOf(37_000), result.snapshot().scores().get(A));
        assertEquals(Integer.valueOf(21_000), result.snapshot().scores().get(B));
        assertEquals(Integer.valueOf(21_000), result.snapshot().scores().get(C));
        assertEquals(Integer.valueOf(21_000), result.snapshot().scores().get(D));
        assertEquals(1, result.snapshot().riichiSticks());
        assertTrue(result.snapshot().settlement().orElseThrow().transfers().stream()
                .allMatch(transfer -> transfer.reason() == PaymentReason.NAGASHI_MANGAN));
    }

    @Test
    void aClaimedTerminalDiscardDisqualifiesNagashiMangan() {
        long[] id = {200};
        TileInstance aDiscard = tile(id, TileKind.M1);
        List<TileInstance> bHand = List.of(
                tile(id, TileKind.M1), tile(id, TileKind.M1), tile(id, TileKind.M2));
        TileInstance cDraw = tile(id, TileKind.M3);
        TileInstance dDraw = tile(id, TileKind.M4);
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(aDiscard))
                .hand(B, bHand)
                .liveWall(List.of(cDraw, dDraw))
                .build();
        RiichiRound round = RiichiRound.fromScenario(scenario, NOT_TENPAI, NEVER_SCORE);

        assertTrue(round.apply(new RoundCommand.Discard(A, aDiscard.id(), false)).accepted());
        assertTrue(round.apply(new RoundCommand.Respond(B, new Reaction(
                ReactionType.PON,
                bHand.subList(0, 2).stream().map(TileInstance::id).toList()))).accepted());
        assertTrue(round.apply(new RoundCommand.Discard(B, bHand.getLast().id(), false)).accepted());
        drawAndDiscard(round, C, cDraw);
        CommandResult result = drawAndDiscard(round, D, dDraw);

        assertEquals(Optional.of("EXHAUSTIVE_DRAW"), result.snapshot().endReason());
        assertTrue(result.snapshot().nagashiWinners().isEmpty());
    }

    @Test
    void thirdOpenDragonSetRegistersAndAppliesTsumoPaoAutomatically() {
        long[] id = {300};
        TileInstance redDiscard = tile(id, TileKind.RED_DRAGON);
        List<TileInstance> bHand = new ArrayList<>(List.of(
                tile(id, TileKind.RED_DRAGON),
                tile(id, TileKind.RED_DRAGON),
                tile(id, TileKind.RED_DRAGON),
                tile(id, TileKind.M2),
                tile(id, TileKind.M3),
                tile(id, TileKind.M4),
                tile(id, TileKind.M5)));
        Meld white = pon(id, TileKind.WHITE_DRAGON, C);
        Meld green = pon(id, TileKind.GREEN_DRAGON, D);
        Scenario scenario = Scenario.builder(SEATS)
                .phase(RoundPhase.AWAITING_DISCARD)
                .hand(A, List.of(redDiscard))
                .hand(B, bHand)
                .melds(B, List.of(white, green))
                .liveWall(List.of(tile(id, TileKind.P9)))
                .rinshan(List.of(tile(id, TileKind.M5)))
                .build();
        RiichiRound round = RiichiRound.fromScenario(
                scenario, NOT_TENPAI, request -> daisangenTsumo());

        assertTrue(round.apply(new RoundCommand.Discard(A, redDiscard.id(), false)).accepted());
        CommandResult kan = round.apply(new RoundCommand.Respond(B, new Reaction(
                ReactionType.MINKAN,
                bHand.subList(0, 3).stream().map(TileInstance::id).toList())));
        assertTrue(kan.accepted());

        CommandResult result = round.apply(new RoundCommand.DeclareTsumo(B));

        assertTrue(result.accepted());
        assertEquals(Integer.valueOf(-7_000), result.snapshot().scores().get(A));
        assertEquals(Integer.valueOf(57_000), result.snapshot().scores().get(B));
        assertEquals(Integer.valueOf(25_000), result.snapshot().scores().get(C));
        assertEquals(Integer.valueOf(25_000), result.snapshot().scores().get(D));
        assertEquals(1, result.snapshot().settlement().orElseThrow().transfers().size());
        assertEquals(
                PaymentReason.PAO,
                result.snapshot().settlement().orElseThrow().transfers().getFirst().reason());
    }

    private static CommandResult drawAndDiscard(
            RiichiRound round,
            PlayerId player,
            TileInstance expected) {
        assertTrue(round.apply(new RoundCommand.Draw(player)).accepted());
        CommandResult result = round.apply(new RoundCommand.Discard(player, expected.id(), false));
        assertTrue(result.accepted());
        return result;
    }

    private static Meld pon(long[] id, TileKind kind, PlayerId source) {
        List<TileInstance> tiles = List.of(tile(id, kind), tile(id, kind), tile(id, kind));
        return new Meld(MeldType.PON, tiles, Optional.of(source), Optional.of(tiles.getFirst().id()));
    }

    private static TileInstance tile(long[] id, TileKind kind) {
        return new TileInstance(new TileId(id[0]++), Tile.of(kind));
    }

    private static ScoreResult daisangenTsumo() {
        return new ScoreResult(
                true,
                true,
                true,
                List.of(new YakuAward("DAISANGEN", 0, 1, false)),
                0,
                0,
                0,
                0,
                0,
                0,
                1,
                Limit.YAKUMAN,
                Optional.empty(),
                Optional.of(new TsumoPayment(8_000, 16_000)));
    }
}

package top.ellan.mahjong.rules.riichi;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MahjongUtilsAdapterTest {
    private static final List<Tile> PINFU_TANYAO = TestFixtures.tiles(
            "2m", "3m", "4m", "3m", "4m", "5m", "4p", "5p", "6p",
            "6s", "7s", "8s", "6p");

    @Test
    void shantenAndWaitsAreExposedWithoutKotlinTypes() {
        HandAnalysis analysis = RiichiServices.handEvaluator().analyze(PINFU_TANYAO, List.of());
        assertEquals(0, analysis.shanten());
        assertTrue(analysis.waits().contains(TileKind.P6));
    }

    @Test
    void representativePinfuTanyaoScoresExactly() {
        ScoreResult result = RiichiServices.scoreCalculator().score(request(
                PINFU_TANYAO, Tile.of(TileKind.P6), RiichiRules.mahjongSoul(), List.of(), false));
        assertTrue(result.completeHand());
        assertTrue(result.hasYaku());
        assertTrue(result.eligibleByMinimumHan());
        assertTrue(result.yaku().stream().anyMatch(yaku -> yaku.id().equals("PINFU")));
        assertTrue(result.yaku().stream().anyMatch(yaku -> yaku.id().equals("TANYAO")));
        assertEquals(2, result.han());
        assertEquals(2, result.yakuHan());
    }

    @Test
    void redFiveAndRepeatedIndicatorsEachAddTheirOwnHan() {
        List<Tile> redHand = TestFixtures.tiles(
                "2m", "3m", "4m", "3m", "4m", "5m", "4p", "0p", "6p",
                "6s", "7s", "8s", "6p");
        ScoreResult result = RiichiServices.scoreCalculator().score(request(
                redHand,
                Tile.of(TileKind.P6),
                RiichiRules.mahjongSoul(),
                List.of(Tile.of(TileKind.P5), Tile.of(TileKind.P5)),
                false));
        assertEquals(6, result.dora());
        assertEquals(1, result.redDora());
        assertEquals(9, result.han());
    }

    @Test
    void minimumHanCountsYakuAndNotBonusDora() {
        List<Tile> hand = TestFixtures.tiles(
                "1m", "2m", "3m", "2p", "3p", "4p", "6s", "7s", "8s",
                "7z", "7z", "7z", "0m");
        ScoreResult result = RiichiServices.scoreCalculator().score(request(
                hand,
                Tile.of(TileKind.M5),
                TestFixtures.rulesWithMinimumHan(2),
                List.of(Tile.of(TileKind.M4)),
                false));
        assertEquals(1, result.yakuHan());
        assertTrue(result.han() >= 4);
        assertFalse(result.eligibleByMinimumHan());
    }

    @Test
    void aSoleFifthCopyWaitIsNotPhysicalTenpai() {
        List<Tile> hand = TestFixtures.tiles(
                "1z", "1z", "1z", "1z",
                "2m", "3m", "4m", "2p", "3p", "4p", "2s", "3s", "4s");
        HandAnalysis analysis = RiichiServices.handEvaluator().analyze(hand, List.of());
        assertEquals(0, analysis.shanten());
        assertTrue(analysis.waits().isEmpty());
        assertFalse(analysis.tenpai());
    }

    private static ScoreRequest request(
            List<Tile> concealed,
            Tile winning,
            RiichiRules rules,
            List<Tile> indicators,
            boolean riichi) {
        return new ScoreRequest(
                concealed,
                List.of(),
                winning,
                false,
                WinMethod.RON,
                Wind.SOUTH,
                Wind.EAST,
                riichi,
                false,
                false,
                false,
                false,
                false,
                false,
                indicators,
                List.of(),
                rules);
    }
}

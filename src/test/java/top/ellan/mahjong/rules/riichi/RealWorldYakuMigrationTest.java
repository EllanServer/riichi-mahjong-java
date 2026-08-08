package top.ellan.mahjong.rules.riichi;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RedFiveConfiguration;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** High-value examples migrated from RiichiRealWorldYakuCoverageTest.kt. */
class RealWorldYakuMigrationTest {
    private static final AtomicLong IDS = new AtomicLong();
    private static final String PINFU_TANYAO =
            "2m 3m 4m 3m 4m 5m 4p 5p 6p 6s 7s 8s 6p";

    @TestFactory
    Stream<DynamicTest> migratedRealWorldExamples() {
        return cases().stream().map(example -> DynamicTest.dynamicTest(example.name(), () -> {
            ScoreResult result = RiichiServices.scoreCalculator().score(example.request());
            Set<String> actual = result.yaku().stream().map(yaku -> yaku.id()).collect(java.util.stream.Collectors.toSet());
            assertTrue(result.completeHand(), example.name() + " must be complete");
            for (String expected : example.expected()) {
                assertTrue(actual.contains(expected),
                        () -> example.name() + " expected " + expected + " but got " + actual);
            }
        }));
    }

    private static List<Example> cases() {
        ArrayList<Example> cases = new ArrayList<>();
        cases.add(basic("pinfu tanyao", PINFU_TANYAO, "6p", "PINFU", "TANYAO"));
        cases.add(basic("iipeikou", "2m 3m 4m 2m 3m 4m 3p 4p 5p 6s 7s 8s 9m", "9m", "IIPEIKOU"));
        cases.add(new Example("seat and round wind", tiles("1z 1z 1z 2m 3m 4m 2p 3p 4p 3s 4s 5s 7m"),
                Tile.parse("7m"), Set.of("SELF_WIND", "ROUND_WIND"), List.of(), WinMethod.RON,
                Wind.EAST, Wind.EAST, false, false, false, false, false, false, false));
        cases.add(basic("white dragon", dragon("5z"), "7m", "HAKU"));
        cases.add(basic("green dragon", dragon("6z"), "7m", "HATSU"));
        cases.add(basic("red dragon", dragon("7z"), "7m", "CHUN"));
        cases.add(basic("sanshoku", "2m 3m 4m 2p 3p 4p 2s 3s 4s 6m 7m 8m 9p", "9p", "SANSHOKU"));
        cases.add(basic("ittsu", "1m 2m 3m 4m 5m 6m 7m 8m 1p 1p 1p 7s 7s", "9m", "ITTSU"));
        cases.add(basic("chanta", "1m 2m 3m 1p 2p 3p 7s 8s 9s 1z 1z 1z 9m", "9m", "CHANTA"));
        cases.add(basic("chiitoitsu", "1m 1m 9m 9m 2p 2p 8p 8p 3s 3s 7s 7s 1z", "1z", "CHITOITSU"));
        cases.add(withMelds("toitoi", "3p 3p 3p 4s 4s 4s 6m 6m 6m 9p", "9p",
                List.of(openPon(TileKind.M2)), "TOITOI"));
        cases.add(basic("sanankou", "2m 2m 2m 3p 3p 3p 4s 4s 4s 6m 7m 9p 9p", "8m", "SANANKOU"));
        cases.add(withMelds("honroutou", "9p 9p 9p 1s 1s 1s 1z 1z 1z 7z", "7z",
                List.of(openPon(TileKind.M1)), "HONROUTOU"));
        cases.add(basic("sanshoku doukou", "2m 2m 2m 2p 2p 2p 2s 2s 2s 3m 4m 5m 9p", "9p", "SANDOKOU"));
        cases.add(withMelds("sankantsu", "2m 3m 9p 9p", "4m",
                List.of(ankan(TileKind.M1), ankan(TileKind.P1), ankan(TileKind.S1)), "SANKANTSU"));
        cases.add(basic("shousangen", "5z 5z 5z 6z 6z 6z 2m 3m 4m 2p 3p 4p 7z", "7z", "SHOUSANGEN"));
        cases.add(basic("honitsu", "1m 2m 3m 4m 5m 6m 7m 8m 9m 1z 1z 1z 7z", "7z", "HONITSU"));
        cases.add(basic("junchan", "1m 2m 3m 1p 2p 3p 7s 8s 9s 7m 8m 9m 9p", "9p", "JUNCHAN"));
        cases.add(basic("ryanpeikou", "2m 3m 4m 2m 3m 4m 6p 7p 8p 6p 7p 8p 5s", "5s", "RYANPEIKOU"));
        cases.add(basic("chinitsu", "1m 1m 1m 2m 3m 4m 4m 5m 6m 7m 7m 9m 9m", "7m", "CHINITSU"));

        cases.add(situational("menzen tsumo", "TSUMO", WinMethod.TSUMO, false, false, false, false, false, false, false, Wind.WEST));
        cases.add(situational("riichi", "REACH", WinMethod.RON, true, false, false, false, false, false, false, Wind.WEST));
        cases.add(situational("ippatsu", "IPPATSU", WinMethod.RON, true, false, true, false, false, false, false, Wind.WEST));
        cases.add(situational("double riichi", "DOUBLE_REACH", WinMethod.RON, false, true, false, false, false, false, false, Wind.WEST));
        cases.add(situational("rinshan", "RINSHAN_KAIHOU", WinMethod.TSUMO, false, false, false, false, true, false, false, Wind.WEST));
        cases.add(situational("chankan", "CHANKAN", WinMethod.RON, false, false, false, true, false, false, false, Wind.WEST));
        cases.add(situational("haitei", "HAITEI", WinMethod.TSUMO, false, false, false, false, false, true, false, Wind.WEST));
        cases.add(situational("houtei", "HOUTEI", WinMethod.RON, false, false, false, false, false, true, false, Wind.WEST));
        cases.add(situational("tenhou", "TENHOU", WinMethod.TSUMO, false, false, false, false, false, false, true, Wind.EAST));
        cases.add(situational("chiihou", "CHIIHOU", WinMethod.TSUMO, false, false, false, false, false, false, true, Wind.SOUTH));

        cases.add(basic("kokushi thirteen wait", "1m 9m 1p 9p 1s 9s 1z 2z 3z 4z 5z 6z 7z", "1m",
                "KOKUSHIMUSO_JUUSANMENMACHI"));
        cases.add(new Example("suuankou", tiles("2m 2m 3p 3p 3p 4s 4s 4s 6m 6m 6m 9p 9p"),
                Tile.parse("2m"), Set.of("SUANKO"), List.of(), WinMethod.TSUMO,
                Wind.WEST, Wind.SOUTH, false, false, false, false, false, false, false));
        cases.add(new Example("suuankou tanki", tiles("2m 2m 2m 3p 3p 3p 4s 4s 4s 6m 6m 6m 9p"),
                Tile.parse("9p"), Set.of("SUANKO_TANKI"), List.of(), WinMethod.TSUMO,
                Wind.WEST, Wind.SOUTH, false, false, false, false, false, false, false));
        cases.add(basic("daisangen", "5z 5z 5z 6z 6z 6z 7z 7z 7z 2m 3m 4m 9p", "9p", "DAISANGEN"));
        cases.add(basic("tsuuiisou", "1z 1z 1z 2z 2z 2z 3z 3z 3z 5z 5z 5z 7z", "7z", "TSUUIISOU"));
        cases.add(basic("shousuushii", "1z 1z 1z 2z 2z 2z 3z 3z 3z 5z 5z 5z 4z", "4z", "SHOUSUUSHII"));
        cases.add(basic("daisuushii", "1z 1z 1z 2z 2z 2z 3z 3z 3z 4z 4z 4z 7z", "7z", "DAISUUSHII"));
        cases.add(basic("ryuuiisou", "2s 3s 4s 2s 3s 4s 6s 6s 6s 8s 8s 8s 6z", "6z", "RYUUIISOU"));
        cases.add(basic("chinroutou", "1m 1m 1m 9p 9p 9p 1s 1s 1s 9m 9m 9m 1p", "1p", "CHINROUTOU"));
        cases.add(withMelds("suukantsu", "9p", "9p",
                List.of(ankan(TileKind.M1), ankan(TileKind.P1), ankan(TileKind.S1), ankan(TileKind.M9)), "SUUKANTSU"));
        cases.add(basic("chuuren", "1m 1m 1m 2m 3m 4m 5m 5m 6m 7m 8m 9m 9m", "9m", "CHUURENPOUTOU"));
        cases.add(basic("junsei chuuren", "1m 1m 1m 2m 3m 4m 5m 6m 7m 8m 9m 9m 9m", "5m",
                "JUNSEI_CHUURENPOUTOU"));
        return List.copyOf(cases);
    }

    private static Example basic(String name, String hand, String winning, String... expected) {
        return new Example(name, tiles(hand), Tile.parse(winning), Set.of(expected), List.of(), WinMethod.RON,
                Wind.WEST, Wind.SOUTH, false, false, false, false, false, false, false);
    }

    private static Example withMelds(
            String name, String hand, String winning, List<Meld> melds, String... expected) {
        return new Example(name, tiles(hand), Tile.parse(winning), Set.of(expected), melds, WinMethod.RON,
                Wind.WEST, Wind.SOUTH, false, false, false, false, false, false, false);
    }

    private static Example situational(
            String name, String expected, WinMethod method,
            boolean riichi, boolean doubleRiichi, boolean ippatsu, boolean chankan,
            boolean rinshan, boolean lastTile, boolean firstTurn, Wind seat) {
        return new Example(name, tiles(PINFU_TANYAO), Tile.parse("6p"), Set.of(expected), List.of(), method,
                seat, Wind.SOUTH, riichi, doubleRiichi, ippatsu, chankan, rinshan, lastTile, firstTurn);
    }

    private static String dragon(String dragon) {
        return dragon + " " + dragon + " " + dragon + " 2m 3m 4m 2p 3p 4p 3s 4s 5s 7m";
    }

    private static List<Tile> tiles(String notation) {
        return Arrays.stream(notation.split(" ")).map(Tile::parse).toList();
    }

    private static Meld openPon(TileKind kind) {
        List<TileInstance> tiles = instances(kind, 3);
        return new Meld(MeldType.PON, tiles, Optional.of(new PlayerId("source")),
                Optional.of(tiles.getFirst().id()));
    }

    private static Meld ankan(TileKind kind) {
        return new Meld(MeldType.ANKAN, instances(kind, 4), Optional.empty(), Optional.empty());
    }

    private static List<TileInstance> instances(TileKind kind, int count) {
        ArrayList<TileInstance> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new TileInstance(new TileId(IDS.getAndIncrement()), Tile.of(kind)));
        }
        return List.copyOf(result);
    }

    private static RiichiRules noRedRules() {
        RiichiRules standard = RiichiRules.mahjongSoul();
        return new RiichiRules(
                standard.profile(), standard.ronMode(), standard.startingPoints(), standard.targetPoints(),
                standard.minimumYakuHan(), RedFiveConfiguration.none(), standard.openTanyao(),
                standard.kiriageMangan(), standard.kazoeYakuman(), standard.multipleYakuman(),
                standard.complexYakuman());
    }

    private record Example(
            String name,
            List<Tile> hand,
            Tile winning,
            Set<String> expected,
            List<Meld> melds,
            WinMethod method,
            Wind seat,
            Wind round,
            boolean riichi,
            boolean doubleRiichi,
            boolean ippatsu,
            boolean chankan,
            boolean rinshan,
            boolean lastTile,
            boolean firstTurn) {
        ScoreRequest request() {
            return new ScoreRequest(
                    hand, melds, winning, false, method, seat, round,
                    riichi, doubleRiichi, ippatsu, chankan, rinshan, lastTile, firstTurn,
                    List.of(), List.of(), noRedRules());
        }
    }
}

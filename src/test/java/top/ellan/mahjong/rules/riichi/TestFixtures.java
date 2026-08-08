package top.ellan.mahjong.rules.riichi;

import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;

import java.util.Arrays;
import java.util.List;

final class TestFixtures {
    private TestFixtures() {
    }

    static List<Tile> tiles(String... notation) {
        return Arrays.stream(notation).map(Tile::parse).toList();
    }

    static RiichiRules rulesWithMinimumHan(int minimumHan) {
        RiichiRules standard = RiichiRules.mahjongSoul();
        return new RiichiRules(
                standard.profile(), standard.ronMode(), standard.startingPoints(), standard.targetPoints(),
                minimumHan, standard.redFives(), standard.openTanyao(), standard.kiriageMangan(),
                standard.kazoeYakuman(), standard.multipleYakuman(), standard.complexYakuman());
    }
}

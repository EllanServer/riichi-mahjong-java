package top.ellan.mahjong.rules.riichi.internal.scoring;

import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;

import java.util.List;

/** Counts dora without string parsing or backend object conversion. */
final class BonusCounter {
    private BonusCounter() {
    }

    static Bonuses count(ScoreRequest request) {
        int dora = countDora(request, request.doraIndicators());
        int ura = request.riichi() || request.doubleRiichi()
                ? countDora(request, request.uraDoraIndicators())
                : 0;
        int red = 0;
        for (Tile tile : request.fullConcealedTiles()) if (tile.red()) red++;
        for (Meld meld : request.melds()) {
            for (Tile tile : meld.logicalTiles()) if (tile.red()) red++;
        }
        return new Bonuses(dora, ura, red);
    }

    private static int countDora(ScoreRequest request, List<Tile> indicators) {
        if (indicators.isEmpty()) return 0;
        int[] multipliers = new int[TileKind.values().length];
        for (Tile indicator : indicators) multipliers[indicator.kind().dora().ordinal()]++;
        int total = 0;
        for (Tile tile : request.fullConcealedTiles()) total += multipliers[tile.kind().ordinal()];
        for (Meld meld : request.melds()) {
            for (Tile tile : meld.logicalTiles()) total += multipliers[tile.kind().ordinal()];
        }
        return total;
    }

    record Bonuses(int dora, int uraDora, int redDora) {
        int total() {
            return dora + uraDora + redDora;
        }
    }
}

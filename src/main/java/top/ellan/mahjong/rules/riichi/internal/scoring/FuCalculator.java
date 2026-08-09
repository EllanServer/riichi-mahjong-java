package top.ellan.mahjong.rules.riichi.internal.scoring;

import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Group;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.GroupType;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Shape;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Wait;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;

/** Japanese fu calculation, including ron-opened triplets and double-wind pairs. */
final class FuCalculator {
    private FuCalculator() {
    }

    static int calculate(HandPattern pattern, ScoreRequest request) {
        if (pattern.shape() == Shape.SEVEN_PAIRS) return 25;
        if (pattern.shape() == Shape.KOKUSHI) return 30;

        int fu = 20;
        if (pattern.wait() == Wait.TANKI
                || pattern.wait() == Wait.KANCHAN
                || pattern.wait() == Wait.PENCHAN) {
            fu += 2;
        }

        TileKind pair = pattern.pair();
        if (pair.isDragon()) fu += 2;
        if (pair == windTile(request.seatWind())) fu += 2;
        if (pair == windTile(request.roundWind())) fu += 2;

        for (int index = 0; index < pattern.groups().size(); index++) {
            Group group = pattern.groups().get(index);
            if (group.type() != GroupType.TRIPLET) continue;
            boolean terminalOrHonor = group.tile().isTerminalOrHonor();
            boolean concealed = pattern.isConcealedTriplet(index, request);
            int groupFu;
            if (group.kan()) groupFu = concealed ? 16 : 8;
            else groupFu = concealed ? 4 : 2;
            if (terminalOrHonor) groupFu *= 2;
            fu += groupFu;
        }

        if (pattern.closed() && request.winMethod() == WinMethod.RON) fu += 10;
        if (request.winMethod() == WinMethod.TSUMO && fu != 20) fu += 2;
        if (!pattern.closed() && fu < 30) fu = 30;
        return roundUpTen(fu);
    }

    private static int roundUpTen(int value) {
        return (value + 9) / 10 * 10;
    }

    private static TileKind windTile(Wind wind) {
        return switch (wind) {
            case EAST -> TileKind.EAST;
            case SOUTH -> TileKind.SOUTH;
            case WEST -> TileKind.WEST;
            case NORTH -> TileKind.NORTH;
        };
    }
}

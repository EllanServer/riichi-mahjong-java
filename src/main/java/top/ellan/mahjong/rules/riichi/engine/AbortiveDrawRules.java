package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.util.List;
import java.util.Set;

public final class AbortiveDrawRules {
    private AbortiveDrawRules() {
    }

    public static boolean canDeclareNineTerminals(boolean firstUninterruptedTurn, List<TileInstance> hand) {
        return firstUninterruptedTurn
                && hand.stream().map(tile -> tile.tile().kind())
                .filter(TileKind::isTerminalOrHonor)
                .distinct()
                .count() >= 9;
    }

    public static boolean isFourWinds(List<TileKind> firstDiscards, boolean anyCallMade) {
        return !anyCallMade
                && firstDiscards.size() == 4
                && firstDiscards.getFirst().isWind()
                && firstDiscards.stream().distinct().count() == 1;
    }

    public static boolean isFourRiichi(Set<PlayerId> declaredPlayers) {
        return declaredPlayers.size() == 4;
    }
}

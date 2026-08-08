package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.settlement.AggregatedSettlement;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Public snapshot deliberately excludes concealed tiles and hidden wall contents. */
public record RoundSnapshot(
        RoundPhase phase,
        PlayerId currentPlayer,
        Map<PlayerId, Integer> scores,
        Map<PlayerId, Integer> handSizes,
        Map<PlayerId, List<TileInstance>> discards,
        Map<PlayerId, List<Meld>> melds,
        Map<PlayerId, Boolean> riichi,
        int liveWallSize,
        int rinshanSize,
        int kanCount,
        int revealedDoraCount,
        int honba,
        int riichiSticks,
        Set<PlayerId> tenpaiPlayers,
        Set<PlayerId> nagashiWinners,
        Optional<AbortiveDraw> abortiveDraw,
        Optional<String> endReason,
        Optional<AggregatedSettlement> settlement) {
    public RoundSnapshot {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(currentPlayer, "currentPlayer");
        scores = Map.copyOf(scores);
        handSizes = Map.copyOf(handSizes);
        discards = deepCopy(discards);
        melds = deepCopy(melds);
        riichi = Map.copyOf(riichi);
        tenpaiPlayers = Set.copyOf(Objects.requireNonNull(tenpaiPlayers, "tenpaiPlayers"));
        nagashiWinners = Set.copyOf(Objects.requireNonNull(nagashiWinners, "nagashiWinners"));
        if (!scores.keySet().containsAll(tenpaiPlayers) || !scores.keySet().containsAll(nagashiWinners)) {
            throw new IllegalArgumentException("draw result references a player outside the round");
        }
        abortiveDraw = Objects.requireNonNull(abortiveDraw, "abortiveDraw");
        endReason = Objects.requireNonNull(endReason, "endReason");
        settlement = Objects.requireNonNull(settlement, "settlement");
    }

    private static <T> Map<PlayerId, List<T>> deepCopy(Map<PlayerId, List<T>> source) {
        java.util.LinkedHashMap<PlayerId, List<T>> copy = new java.util.LinkedHashMap<>();
        source.forEach((player, values) -> copy.put(player, List.copyOf(values)));
        return Map.copyOf(copy);
    }
}

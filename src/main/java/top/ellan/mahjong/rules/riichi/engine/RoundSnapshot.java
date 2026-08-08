package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.settlement.AggregatedSettlement;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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

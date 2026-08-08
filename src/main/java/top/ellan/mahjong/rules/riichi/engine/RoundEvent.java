package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;

import java.util.Objects;
import java.util.Optional;

public record RoundEvent(
        Type type,
        Optional<PlayerId> player,
        Optional<TileInstance> tile,
        String detail) {

    public enum Type {
        DRAWN, DISCARDED, RIICHI_DECLARED, REACTION_OPENED, REACTION_SUBMITTED,
        MELD_DECLARED, KAN_REGISTERED, RINSHAN_DRAWN, ROUND_ENDED
    }

    public RoundEvent {
        Objects.requireNonNull(type, "type");
        player = Objects.requireNonNull(player, "player");
        tile = Objects.requireNonNull(tile, "tile");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static RoundEvent of(Type type, PlayerId player, TileInstance tile, String detail) {
        return new RoundEvent(type, Optional.ofNullable(player), Optional.ofNullable(tile), detail);
    }
}

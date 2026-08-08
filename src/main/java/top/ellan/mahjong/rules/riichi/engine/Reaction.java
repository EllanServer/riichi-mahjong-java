package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.TileId;

import java.util.List;
import java.util.Objects;

public record Reaction(ReactionType type, List<TileId> consumedTiles) {
    public Reaction {
        Objects.requireNonNull(type, "type");
        consumedTiles = List.copyOf(Objects.requireNonNull(consumedTiles, "consumedTiles"));
        if (consumedTiles.stream().distinct().count() != consumedTiles.size()) {
            throw new IllegalArgumentException("reaction repeats a physical tile id");
        }
        int expected = switch (type) {
            case CHII, PON -> 2;
            case MINKAN -> 3;
            case RON, SKIP -> 0;
        };
        if (consumedTiles.size() != expected) {
            throw new IllegalArgumentException(type + " requires " + expected + " consumed tiles");
        }
    }

    public static Reaction ron() {
        return new Reaction(ReactionType.RON, List.of());
    }

    public static Reaction skip() {
        return new Reaction(ReactionType.SKIP, List.of());
    }
}

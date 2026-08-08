package top.ellan.mahjong.rules.riichi.model;

import java.util.Objects;

public record TileInstance(TileId id, Tile tile) {
    public TileInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tile, "tile");
    }
}

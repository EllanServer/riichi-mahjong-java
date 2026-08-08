package top.ellan.mahjong.rules.riichi.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable physical meld. Claimed fields are absent only for an ankan. */
public record Meld(
        MeldType type,
        List<TileInstance> tiles,
        Optional<PlayerId> claimedFrom,
        Optional<TileId> claimedTile) {

    public Meld {
        Objects.requireNonNull(type, "type");
        tiles = List.copyOf(Objects.requireNonNull(tiles, "tiles"));
        claimedFrom = Objects.requireNonNull(claimedFrom, "claimedFrom");
        claimedTile = Objects.requireNonNull(claimedTile, "claimedTile");
        validate(type, tiles, claimedFrom, claimedTile);
    }

    public boolean open() {
        return type.isOpen();
    }

    public List<Tile> logicalTiles() {
        return tiles.stream().map(TileInstance::tile).toList();
    }

    private static void validate(
            MeldType type,
            List<TileInstance> tiles,
            Optional<PlayerId> claimedFrom,
            Optional<TileId> claimedTile) {
        int expected = type.isKan() ? 4 : 3;
        if (tiles.size() != expected) {
            throw new IllegalArgumentException(type + " must contain " + expected + " tiles");
        }
        if (tiles.stream().map(TileInstance::id).distinct().count() != tiles.size()) {
            throw new IllegalArgumentException("meld contains duplicate physical tile ids");
        }
        if (type == MeldType.ANKAN) {
            if (claimedFrom.isPresent() || claimedTile.isPresent()) {
                throw new IllegalArgumentException("ankan cannot have a claimed tile");
            }
        } else if (claimedFrom.isEmpty() || claimedTile.isEmpty()
                || tiles.stream().noneMatch(tile -> tile.id().equals(claimedTile.orElseThrow()))) {
            throw new IllegalArgumentException("open meld must identify its claimed tile and source");
        }

        List<TileKind> kinds = new ArrayList<>(tiles.stream().map(t -> t.tile().kind()).toList());
        kinds.sort(Comparator.comparingInt(Enum::ordinal));
        if (type == MeldType.CHII) {
            boolean sequence = !kinds.getFirst().isHonor()
                    && kinds.getFirst().suit() == kinds.get(1).suit()
                    && kinds.get(1).suit() == kinds.getLast().suit()
                    && kinds.get(1).rank() == kinds.getFirst().rank() + 1
                    && kinds.getLast().rank() == kinds.getFirst().rank() + 2;
            if (!sequence) {
                throw new IllegalArgumentException("chii tiles are not a suited sequence");
            }
        } else if (kinds.stream().distinct().count() != 1) {
            throw new IllegalArgumentException(type + " tiles must have one logical kind");
        }
    }
}

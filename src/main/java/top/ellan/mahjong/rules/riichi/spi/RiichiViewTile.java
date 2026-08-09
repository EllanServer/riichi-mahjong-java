package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;

import java.util.Optional;

/** One public or authorized-private scene node with no physical tile identifier. */
public record RiichiViewTile(
        long projectionId,
        Optional<TileInstance> face,
        Optional<PlayerId> owner,
        RiichiViewZone zone,
        int index,
        boolean faceUp) {

    public RiichiViewTile {
        if (projectionId < 0 || projectionId >= RiichiProjectionIds.PHYSICAL_TILE_COUNT
                || face == null || owner == null || zone == null || index < 0) {
            throw new IllegalArgumentException("invalid Riichi projected tile");
        }
        if (faceUp != face.isPresent()) {
            throw new IllegalArgumentException("face presence must match visibility");
        }
        if (zone == RiichiViewZone.WALL) {
            if (owner.isPresent() || faceUp) {
                throw new IllegalArgumentException("wall node must be ownerless and hidden");
            }
        } else if (zone == RiichiViewZone.INDICATOR) {
            if (owner.isPresent() || !faceUp) {
                throw new IllegalArgumentException("indicator node must be ownerless and visible");
            }
        } else if (owner.isEmpty()) {
            throw new IllegalArgumentException("placed scene tile requires an owner");
        }
        if ((zone == RiichiViewZone.DISCARD || zone == RiichiViewZone.MELD) && !faceUp) {
            throw new IllegalArgumentException("discard and meld nodes must be visible");
        }
    }
}

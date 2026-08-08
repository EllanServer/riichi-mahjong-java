package top.ellan.mahjong.rules.riichi.model;

public enum Wind {
    EAST, SOUTH, WEST, NORTH;

    public Wind next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public TileKind tileKind() {
        return TileKind.values()[TileKind.EAST.ordinal() + ordinal()];
    }
}

package top.ellan.mahjong.rules.riichi.model;

import java.util.Objects;

/** Logical tile kind plus the physical red-five attribute. */
public record Tile(TileKind kind, boolean red) implements Comparable<Tile> {
    public Tile {
        Objects.requireNonNull(kind, "kind");
        if (red && (kind.isHonor() || kind.rank() != 5)) {
            throw new IllegalArgumentException("only suited fives can be red: " + kind);
        }
    }

    public static Tile of(TileKind kind) {
        return new Tile(kind, false);
    }

    public static Tile red(TileKind kind) {
        return new Tile(kind, true);
    }

    public static Tile parse(String notation) {
        TileKind kind = TileKind.parse(notation);
        boolean red = notation != null && notation.trim().length() == 2 && notation.trim().charAt(0) == '0';
        return new Tile(kind, red);
    }

    public String notation() {
        if (!red) {
            return kind.notation();
        }
        return "0" + kind.notation().charAt(1);
    }

    @Override
    public int compareTo(Tile other) {
        int byKind = Integer.compare(kind.ordinal(), other.kind.ordinal());
        return byKind != 0 ? byKind : Boolean.compare(other.red, red);
    }
}

package top.ellan.mahjong.rules.riichi.internal.scoring;

import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;

import java.util.List;
import java.util.Objects;

/** One structural interpretation of a complete hand and its winning-tile wait. */
record HandPattern(
        Shape shape,
        List<Group> groups,
        TileKind pair,
        Wait waitType,
        int winningGroup,
        int[] counts,
        boolean closed) {

    HandPattern {
        Objects.requireNonNull(shape, "shape");
        groups = List.copyOf(Objects.requireNonNull(groups, "groups"));
        Objects.requireNonNull(waitType, "waitType");
        counts = Objects.requireNonNull(counts, "counts").clone();
        if (counts.length != TileKind.values().length) {
            throw new IllegalArgumentException("a scoring pattern requires 34 tile counts");
        }
    }

    @Override
    public int[] counts() {
        return counts.clone();
    }

    int count(TileKind kind) {
        return counts[kind.ordinal()];
    }

    int tileCount() {
        int total = 0;
        for (int count : counts) total += count;
        return total;
    }

    boolean isConcealedTriplet(int index, ScoreRequest request) {
        Group group = groups.get(index);
        if (group.type() != GroupType.TRIPLET || group.open()) return false;
        return !(request.winMethod() == WinMethod.RON
                && index == winningGroup
                && waitType == Wait.SHANPON);
    }

    enum Shape {
        STANDARD,
        SEVEN_PAIRS,
        KOKUSHI
    }

    enum Wait {
        NONE,
        RYANMEN,
        KANCHAN,
        PENCHAN,
        SHANPON,
        TANKI
    }

    enum GroupType {
        SEQUENCE,
        TRIPLET
    }

    /** Sequence tile is its first tile; triplet tile is its repeated tile. */
    record Group(GroupType type, TileKind tile, boolean open, boolean kan) {
        Group {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(tile, "tile");
            if (type == GroupType.SEQUENCE && (tile.isHonor() || tile.rank() > 7 || kan)) {
                throw new IllegalArgumentException("invalid sequence group");
            }
        }

        boolean contains(TileKind kind) {
            if (type == GroupType.TRIPLET) return tile == kind;
            return tile.suit() == kind.suit()
                    && kind.rank() >= tile.rank()
                    && kind.rank() <= tile.rank() + 2;
        }

        boolean containsTerminalOrHonor() {
            return type == GroupType.TRIPLET
                    ? tile.isTerminalOrHonor()
                    : tile.rank() == 1 || tile.rank() == 7;
        }
    }
}

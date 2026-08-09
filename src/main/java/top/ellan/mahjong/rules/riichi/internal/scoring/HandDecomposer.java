package top.ellan.mahjong.rules.riichi.internal.scoring;

import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Group;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.GroupType;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Shape;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Wait;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;

import java.util.ArrayList;
import java.util.List;

/** Allocation-bounded complete-hand decomposer for standard, seven-pairs and kokushi shapes. */
final class HandDecomposer {
    private static final TileKind[] KINDS = TileKind.values();

    private HandDecomposer() {
    }

    static List<HandPattern> decompose(ScoreRequest request) {
        int[] concealed = new int[KINDS.length];
        for (Tile tile : request.fullConcealedTiles()) concealed[tile.kind().ordinal()]++;
        int[] all = concealed.clone();
        for (Meld meld : request.melds()) {
            for (Tile tile : meld.logicalTiles()) all[tile.kind().ordinal()]++;
        }

        ArrayList<HandPattern> result = new ArrayList<>(8);
        boolean closed = request.closedHand();
        if (request.melds().isEmpty()) {
            addSevenPairs(concealed, all, closed, result);
            addKokushi(concealed, all, request.winningTile().kind(), closed, result);
        }
        addStandard(concealed, all, request, closed, result);
        return List.copyOf(result);
    }

    private static void addSevenPairs(
            int[] concealed, int[] all, boolean closed, List<HandPattern> result) {
        int pairs = 0;
        for (int count : concealed) {
            if (count == 2) pairs++;
            else if (count != 0) return;
        }
        if (pairs == 7) {
            result.add(new HandPattern(
                    Shape.SEVEN_PAIRS, List.of(), null, Wait.TANKI, -1, all, closed));
        }
    }

    private static void addKokushi(
            int[] concealed,
            int[] all,
            TileKind winning,
            boolean closed,
            List<HandPattern> result) {
        int distinct = 0;
        int duplicate = -1;
        for (TileKind kind : KINDS) {
            int count = concealed[kind.ordinal()];
            if (kind.isTerminalOrHonor()) {
                if (count > 0) distinct++;
                if (count == 2) duplicate = kind.ordinal();
                else if (count != 1) return;
            } else if (count != 0) {
                return;
            }
        }
        if (distinct == 13 && duplicate >= 0) {
            Wait wait = duplicate == winning.ordinal() ? Wait.TANKI : Wait.NONE;
            result.add(new HandPattern(Shape.KOKUSHI, List.of(), null, wait, -1, all, closed));
        }
    }

    private static void addStandard(
            int[] concealed,
            int[] all,
            ScoreRequest request,
            boolean closed,
            List<HandPattern> result) {
        int concealedGroupTarget = 4 - request.melds().size();
        if (concealedGroupTarget < 0) return;
        ArrayList<Group> fixed = new ArrayList<>(request.melds().size());
        for (Meld meld : request.melds()) fixed.add(fromMeld(meld));

        for (TileKind pair : KINDS) {
            int pairOrdinal = pair.ordinal();
            if (concealed[pairOrdinal] < 2) continue;
            concealed[pairOrdinal] -= 2;
            ArrayList<List<Group>> decompositions = new ArrayList<>(4);
            collectGroups(concealed, concealedGroupTarget, new ArrayList<>(concealedGroupTarget), decompositions);
            concealed[pairOrdinal] += 2;
            for (List<Group> concealedGroups : decompositions) {
                ArrayList<Group> groups = new ArrayList<>(4);
                groups.addAll(concealedGroups);
                groups.addAll(fixed);
                addWinningInterpretations(groups, concealedGroups.size(), pair, all, request, closed, result);
            }
        }
    }

    private static void collectGroups(
            int[] counts,
            int target,
            ArrayList<Group> current,
            List<List<Group>> result) {
        int first = firstOccupied(counts);
        if (first < 0) {
            if (current.size() == target) result.add(List.copyOf(current));
            return;
        }
        if (current.size() >= target) return;

        TileKind kind = KINDS[first];
        if (counts[first] >= 3) {
            counts[first] -= 3;
            current.add(new Group(GroupType.TRIPLET, kind, false, false));
            collectGroups(counts, target, current, result);
            current.removeLast();
            counts[first] += 3;
        }
        if (!kind.isHonor() && kind.rank() <= 7
                && counts[first + 1] > 0 && counts[first + 2] > 0) {
            counts[first]--;
            counts[first + 1]--;
            counts[first + 2]--;
            current.add(new Group(GroupType.SEQUENCE, kind, false, false));
            collectGroups(counts, target, current, result);
            current.removeLast();
            counts[first]++;
            counts[first + 1]++;
            counts[first + 2]++;
        }
    }

    private static void addWinningInterpretations(
            List<Group> groups,
            int concealedGroupCount,
            TileKind pair,
            int[] all,
            ScoreRequest request,
            boolean closed,
            List<HandPattern> result) {
        TileKind winning = request.winningTile().kind();
        if (pair == winning) {
            result.add(new HandPattern(
                    Shape.STANDARD, groups, pair, Wait.TANKI, -1, all, closed));
        }
        for (int index = 0; index < concealedGroupCount; index++) {
            Group group = groups.get(index);
            if (!group.contains(winning)) continue;
            result.add(new HandPattern(
                    Shape.STANDARD,
                    groups,
                    pair,
                    wait(group, winning),
                    index,
                    all,
                    closed));
        }
    }

    private static Wait wait(Group group, TileKind winning) {
        if (group.type() == GroupType.TRIPLET) return Wait.SHANPON;
        int relative = winning.rank() - group.tile().rank();
        if (relative == 1) return Wait.KANCHAN;
        if ((group.tile().rank() == 1 && relative == 2)
                || (group.tile().rank() == 7 && relative == 0)) {
            return Wait.PENCHAN;
        }
        return Wait.RYANMEN;
    }

    private static Group fromMeld(Meld meld) {
        TileKind first = meld.tiles().getFirst().tile().kind();
        return new Group(
                meld.type() == MeldType.CHII ? GroupType.SEQUENCE : GroupType.TRIPLET,
                first,
                meld.open(),
                meld.type().isKan());
    }

    private static int firstOccupied(int[] counts) {
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] != 0) return index;
        }
        return -1;
    }
}

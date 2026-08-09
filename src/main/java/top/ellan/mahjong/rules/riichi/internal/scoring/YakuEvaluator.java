package top.ellan.mahjong.rules.riichi.internal.scoring;

import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Group;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.GroupType;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Shape;
import top.ellan.mahjong.rules.riichi.internal.scoring.HandPattern.Wait;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Native Java yaku recognizer for the supported four-player Mahjong Soul profile. */
final class YakuEvaluator {
    private static final TileKind[] KINDS = TileKind.values();

    private YakuEvaluator() {
    }

    static Evaluation evaluate(HandPattern pattern, ScoreRequest request) {
        ArrayList<YakuAward> yakuman = new ArrayList<>(4);
        addSituationalYakuman(yakuman, request);
        addStructuralYakuman(yakuman, pattern, request);
        if (!yakuman.isEmpty()) {
            List<YakuAward> effective = effectiveYakuman(yakuman, request);
            int multiplier = effective.stream().mapToInt(YakuAward::yakumanMultiplier).sum();
            return new Evaluation(effective, 0, multiplier);
        }

        ArrayList<YakuAward> yaku = new ArrayList<>(12);
        addSituationalYaku(yaku, request);
        addStructuralYaku(yaku, pattern, request);
        yaku.sort(Comparator.comparing(YakuAward::id));
        return new Evaluation(
                List.copyOf(yaku),
                yaku.stream().mapToInt(YakuAward::han).sum(),
                0);
    }

    static boolean isPinfu(HandPattern pattern, ScoreRequest request) {
        if (pattern.shape() != Shape.STANDARD || !pattern.closed() || pattern.wait() != Wait.RYANMEN) {
            return false;
        }
        if (pattern.groups().stream().anyMatch(group -> group.type() != GroupType.SEQUENCE)) {
            return false;
        }
        TileKind pair = pattern.pair();
        return !pair.isDragon()
                && pair != windTile(request.seatWind())
                && pair != windTile(request.roundWind());
    }

    private static void addSituationalYakuman(List<YakuAward> awards, ScoreRequest request) {
        if (!request.firstTurn()) return;
        addYakuman(awards, request.seatWind() == Wind.EAST ? "TENHOU" : "CHIIHOU", 1);
    }

    private static void addSituationalYaku(List<YakuAward> awards, ScoreRequest request) {
        if (request.doubleRiichi()) addHan(awards, "DOUBLE_REACH", 2);
        else if (request.riichi()) addHan(awards, "REACH", 1);
        if (request.ippatsu()) addHan(awards, "IPPATSU", 1);
        if (request.rinshan()) addHan(awards, "RINSHAN_KAIHOU", 1);
        if (request.chankan()) addHan(awards, "CHANKAN", 1);
        if (request.lastTile() && !request.rinshan()) {
            addHan(awards, request.winMethod() == WinMethod.TSUMO ? "HAITEI" : "HOUTEI", 1);
        }
    }

    private static void addStructuralYakuman(
            List<YakuAward> awards, HandPattern pattern, ScoreRequest request) {
        int doubleValue = request.rules().multipleYakuman() ? 2 : 1;
        if (pattern.shape() == Shape.KOKUSHI) {
            addYakuman(
                    awards,
                    pattern.wait() == Wait.TANKI
                            ? "KOKUSHIMUSO_JUUSANMENMACHI"
                            : "KOKUSHIMUSO",
                    pattern.wait() == Wait.TANKI ? doubleValue : 1);
            return;
        }
        if (pattern.shape() != Shape.STANDARD) {
            if (allHonors(pattern)) addYakuman(awards, "TSUUIISOU", 1);
            if (allTerminals(pattern)) addYakuman(awards, "CHINROUTOU", 1);
            if (allGreen(pattern)) addYakuman(awards, "RYUUIISOU", 1);
            return;
        }

        int concealedTriplets = concealedTriplets(pattern, request);
        if (concealedTriplets == 4) {
            boolean tanki = pattern.wait() == Wait.TANKI;
            addYakuman(awards, tanki ? "SUANKO_TANKI" : "SUANKO", tanki ? doubleValue : 1);
        }
        int dragonTriplets = tripletCount(pattern, TileKind::isDragon);
        if (dragonTriplets == 3) addYakuman(awards, "DAISANGEN", 1);
        if (allHonors(pattern)) addYakuman(awards, "TSUUIISOU", 1);

        int windTriplets = tripletCount(pattern, TileKind::isWind);
        if (windTriplets == 4) {
            addYakuman(awards, "DAISUUSHII", doubleValue);
        } else if (windTriplets == 3 && pattern.pair().isWind()) {
            addYakuman(awards, "SHOUSUUSHII", 1);
        }
        if (allGreen(pattern)) addYakuman(awards, "RYUUIISOU", 1);
        if (allTerminals(pattern)) addYakuman(awards, "CHINROUTOU", 1);
        if (pattern.groups().stream().filter(Group::kan).count() == 4) {
            addYakuman(awards, "SUUKANTSU", 1);
        }

        TileKind churenExtra = churenExtra(pattern);
        if (churenExtra != null) {
            boolean pure = churenExtra == request.winningTile().kind();
            addYakuman(
                    awards,
                    pure ? "JUNSEI_CHUURENPOUTOU" : "CHUURENPOUTOU",
                    pure ? doubleValue : 1);
        }
    }

    private static void addStructuralYaku(
            List<YakuAward> awards, HandPattern pattern, ScoreRequest request) {
        if (pattern.closed() && request.winMethod() == WinMethod.TSUMO) {
            addHan(awards, "TSUMO", 1);
        }
        if (isPinfu(pattern, request)) addHan(awards, "PINFU", 1);
        if (allSimples(pattern) && (pattern.closed() || request.rules().openTanyao())) {
            addHan(awards, "TANYAO", 1);
        }
        if (pattern.shape() == Shape.SEVEN_PAIRS) {
            addHan(awards, "CHITOITSU", 2);
        }
        if (pattern.shape() == Shape.STANDARD) {
            addStandardYaku(awards, pattern, request);
        }
        if (allTerminalOrHonor(pattern)) addHan(awards, "HONROUTOU", 2);
        addFlushYaku(awards, pattern);
    }

    private static void addStandardYaku(
            List<YakuAward> awards, HandPattern pattern, ScoreRequest request) {
        int identicalPairs = identicalSequencePairs(pattern);
        if (pattern.closed() && identicalPairs >= 2) addHan(awards, "RYANPEIKOU", 3);
        else if (pattern.closed() && identicalPairs == 1) addHan(awards, "IIPEIKOU", 1);

        addYakuhai(awards, pattern, request);
        if (hasSanshokuSequences(pattern)) addHan(awards, "SANSHOKU", pattern.closed() ? 2 : 1);
        if (hasIttsu(pattern)) addHan(awards, "ITTSU", pattern.closed() ? 2 : 1);

        TerminalShape terminalShape = terminalShape(pattern);
        if (terminalShape.allGroupsQualified() && terminalShape.hasSequence()) {
            if (terminalShape.hasHonor()) addHan(awards, "CHANTA", pattern.closed() ? 2 : 1);
            else addHan(awards, "JUNCHAN", pattern.closed() ? 3 : 2);
        }

        if (pattern.groups().stream().allMatch(group -> group.type() == GroupType.TRIPLET)) {
            addHan(awards, "TOITOI", 2);
        }
        if (concealedTriplets(pattern, request) == 3) addHan(awards, "SANANKOU", 2);
        if (hasSanshokuTriplets(pattern)) addHan(awards, "SANDOKOU", 2);
        if (pattern.groups().stream().filter(Group::kan).count() == 3) addHan(awards, "SANKANTSU", 2);
        if (tripletCount(pattern, TileKind::isDragon) == 2 && pattern.pair().isDragon()) {
            addHan(awards, "SHOUSANGEN", 2);
        }
    }

    private static void addYakuhai(
            List<YakuAward> awards, HandPattern pattern, ScoreRequest request) {
        for (Group group : pattern.groups()) {
            if (group.type() != GroupType.TRIPLET) continue;
            TileKind kind = group.tile();
            if (kind == windTile(request.seatWind())) addHan(awards, "SELF_WIND", 1);
            if (kind == windTile(request.roundWind())) addHan(awards, "ROUND_WIND", 1);
            if (kind == TileKind.WHITE_DRAGON) addHan(awards, "HAKU", 1);
            if (kind == TileKind.GREEN_DRAGON) addHan(awards, "HATSU", 1);
            if (kind == TileKind.RED_DRAGON) addHan(awards, "CHUN", 1);
        }
    }

    private static void addFlushYaku(List<YakuAward> awards, HandPattern pattern) {
        TileKind.Suit suit = null;
        boolean honor = false;
        for (TileKind kind : KINDS) {
            if (pattern.count(kind) == 0) continue;
            if (kind.isHonor()) {
                honor = true;
            } else if (suit == null) {
                suit = kind.suit();
            } else if (suit != kind.suit()) {
                return;
            }
        }
        if (suit == null) return;
        if (honor) addHan(awards, "HONITSU", pattern.closed() ? 3 : 2);
        else addHan(awards, "CHINITSU", pattern.closed() ? 6 : 5);
    }

    private static List<YakuAward> effectiveYakuman(
            List<YakuAward> awards, ScoreRequest request) {
        awards.sort(Comparator.comparing(YakuAward::id));
        if (request.rules().complexYakuman()) return List.copyOf(awards);
        return List.of(awards.stream()
                .max(Comparator.comparingInt(YakuAward::yakumanMultiplier)
                        .thenComparing(YakuAward::id))
                .orElseThrow());
    }

    private static int concealedTriplets(HandPattern pattern, ScoreRequest request) {
        int count = 0;
        for (int index = 0; index < pattern.groups().size(); index++) {
            if (pattern.isConcealedTriplet(index, request)) count++;
        }
        return count;
    }

    private static int tripletCount(
            HandPattern pattern, java.util.function.Predicate<TileKind> predicate) {
        int count = 0;
        for (Group group : pattern.groups()) {
            if (group.type() == GroupType.TRIPLET && predicate.test(group.tile())) count++;
        }
        return count;
    }

    private static int identicalSequencePairs(HandPattern pattern) {
        Map<TileKind, Integer> counts = new EnumMap<>(TileKind.class);
        for (Group group : pattern.groups()) {
            if (group.type() == GroupType.SEQUENCE) counts.merge(group.tile(), 1, Integer::sum);
        }
        int pairs = 0;
        for (int count : counts.values()) pairs += count / 2;
        return pairs;
    }

    private static boolean hasSanshokuSequences(HandPattern pattern) {
        for (int rank = 1; rank <= 7; rank++) {
            boolean man = false;
            boolean pin = false;
            boolean sou = false;
            for (Group group : pattern.groups()) {
                if (group.type() != GroupType.SEQUENCE || group.tile().rank() != rank) continue;
                switch (group.tile().suit()) {
                    case MAN -> man = true;
                    case PIN -> pin = true;
                    case SOU -> sou = true;
                    case HONOR -> throw new IllegalStateException("honor sequence");
                }
            }
            if (man && pin && sou) return true;
        }
        return false;
    }

    private static boolean hasIttsu(HandPattern pattern) {
        for (TileKind.Suit suit : List.of(TileKind.Suit.MAN, TileKind.Suit.PIN, TileKind.Suit.SOU)) {
            boolean one = false;
            boolean four = false;
            boolean seven = false;
            for (Group group : pattern.groups()) {
                if (group.type() != GroupType.SEQUENCE || group.tile().suit() != suit) continue;
                one |= group.tile().rank() == 1;
                four |= group.tile().rank() == 4;
                seven |= group.tile().rank() == 7;
            }
            if (one && four && seven) return true;
        }
        return false;
    }

    private static boolean hasSanshokuTriplets(HandPattern pattern) {
        for (int rank = 1; rank <= 9; rank++) {
            boolean man = false;
            boolean pin = false;
            boolean sou = false;
            for (Group group : pattern.groups()) {
                if (group.type() != GroupType.TRIPLET
                        || group.tile().isHonor()
                        || group.tile().rank() != rank) continue;
                switch (group.tile().suit()) {
                    case MAN -> man = true;
                    case PIN -> pin = true;
                    case SOU -> sou = true;
                    case HONOR -> throw new IllegalStateException("honor handled above");
                }
            }
            if (man && pin && sou) return true;
        }
        return false;
    }

    private static TerminalShape terminalShape(HandPattern pattern) {
        if (pattern.shape() != Shape.STANDARD) return new TerminalShape(false, false, false);
        boolean qualified = pattern.pair().isTerminalOrHonor();
        boolean sequence = false;
        boolean honor = pattern.pair().isHonor();
        for (Group group : pattern.groups()) {
            qualified &= group.containsTerminalOrHonor();
            sequence |= group.type() == GroupType.SEQUENCE;
            honor |= group.type() == GroupType.TRIPLET && group.tile().isHonor();
        }
        return new TerminalShape(qualified, sequence, honor);
    }

    private static boolean allSimples(HandPattern pattern) {
        for (TileKind kind : KINDS) {
            if (pattern.count(kind) > 0 && kind.isTerminalOrHonor()) return false;
        }
        return true;
    }

    private static boolean allTerminalOrHonor(HandPattern pattern) {
        for (TileKind kind : KINDS) {
            if (pattern.count(kind) > 0 && !kind.isTerminalOrHonor()) return false;
        }
        return true;
    }

    private static boolean allHonors(HandPattern pattern) {
        for (TileKind kind : KINDS) {
            if (pattern.count(kind) > 0 && !kind.isHonor()) return false;
        }
        return true;
    }

    private static boolean allTerminals(HandPattern pattern) {
        for (TileKind kind : KINDS) {
            if (pattern.count(kind) > 0 && !kind.isTerminal()) return false;
        }
        return true;
    }

    private static boolean allGreen(HandPattern pattern) {
        for (TileKind kind : KINDS) {
            if (pattern.count(kind) == 0) continue;
            if (kind != TileKind.S2 && kind != TileKind.S3 && kind != TileKind.S4
                    && kind != TileKind.S6 && kind != TileKind.S8
                    && kind != TileKind.GREEN_DRAGON) {
                return false;
            }
        }
        return true;
    }

    private static TileKind churenExtra(HandPattern pattern) {
        if (!pattern.closed() || pattern.tileCount() != 14) return null;
        TileKind.Suit suit = null;
        int[] ranks = new int[9];
        for (TileKind kind : KINDS) {
            int count = pattern.count(kind);
            if (count == 0) continue;
            if (kind.isHonor()) return null;
            if (suit == null) suit = kind.suit();
            else if (suit != kind.suit()) return null;
            ranks[kind.rank() - 1] = count;
        }
        if (suit == null || ranks[0] < 3 || ranks[8] < 3) return null;
        for (int rank = 1; rank <= 7; rank++) if (ranks[rank] < 1) return null;
        ranks[0] -= 3;
        ranks[8] -= 3;
        for (int rank = 1; rank <= 7; rank++) ranks[rank]--;
        int extra = -1;
        for (int rank = 0; rank < ranks.length; rank++) {
            if (ranks[rank] == 1 && extra < 0) extra = rank;
            else if (ranks[rank] != 0) return null;
        }
        return extra < 0 ? null : TileKind.of(suit, extra + 1);
    }

    private static TileKind windTile(Wind wind) {
        return switch (wind) {
            case EAST -> TileKind.EAST;
            case SOUTH -> TileKind.SOUTH;
            case WEST -> TileKind.WEST;
            case NORTH -> TileKind.NORTH;
        };
    }

    private static void addHan(List<YakuAward> awards, String id, int han) {
        awards.add(new YakuAward(id, han, 0, false));
    }

    private static void addYakuman(List<YakuAward> awards, String id, int multiplier) {
        awards.add(new YakuAward(id, 0, multiplier, false));
    }

    record Evaluation(List<YakuAward> awards, int yakuHan, int yakumanMultiplier) {
        Evaluation {
            awards = List.copyOf(awards);
        }
    }

    private record TerminalShape(boolean allGroupsQualified, boolean hasSequence, boolean hasHonor) {
    }
}

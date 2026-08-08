package top.ellan.mahjong.rules.riichi.internal;

import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.scoring.Limit;
import top.ellan.mahjong.rules.riichi.scoring.RonPayment;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class MahjongUtilsScoreCalculator implements ScoreCalculator {
    private static final Map<String, String> CANONICAL_NAMES = canonicalNames();
    private static final Set<String> DOUBLE_YAKUMAN = Set.of(
            "Daisushi", "SuankoTanki", "ChurenNineWaiting", "KokushiThirteenWaiting");

    private final MahjongUtilsBridge bridge;
    private final HandEvaluator evaluator;
    private final BoundedLruCache<ScoreRequest, ScoreResult> cache = new BoundedLruCache<>(2_048);

    MahjongUtilsScoreCalculator(MahjongUtilsBridge bridge, HandEvaluator evaluator) {
        this.bridge = bridge;
        this.evaluator = evaluator;
    }

    @Override
    public ScoreResult score(ScoreRequest request) {
        ScoreResult cached = cache.get(request);
        if (cached != null) return cached;
        ScoreResult result = calculate(request);
        cache.put(request, result);
        return result;
    }

    private ScoreResult calculate(ScoreRequest request) {
        boolean complete = evaluator.analyze(request.fullConcealedTiles(), request.melds()).complete();
        if (!complete) {
            return emptyResult(request, false);
        }

        int dora = countDora(request.fullConcealedTiles(), request.melds(), request.doraIndicators());
        int uraDora = request.riichi() || request.doubleRiichi()
                ? countDora(request.fullConcealedTiles(), request.melds(), request.uraDoraIndicators())
                : 0;
        int redDora = countRed(request);

        Object options = bridge.horaOptions(
                request.rules().openTanyao(),
                request.rules().kiriageMangan(),
                request.rules().kazoeYakuman(),
                request.rules().multipleYakuman(),
                request.rules().complexYakuman());
        Set<Object> extraYaku = bridge.extraYaku(options, extraYaku(request));
        Object hora = bridge.score(
                request.fullConcealedTiles(),
                request.melds(),
                request.winningTile(),
                request.winMethod() == WinMethod.TSUMO,
                dora + uraDora + redDora,
                request.seatWind(),
                request.roundWind(),
                extraYaku,
                options);

        boolean open = request.melds().stream().anyMatch(Meld::open);
        ArrayList<YakuAward> awards = new ArrayList<>();
        int yakuHan = 0;
        int yakumanMultiplier = 0;
        for (Object rawYaku : bridge.yaku(hora)) {
            String rawName = bridge.yakuName(rawYaku);
            boolean yakuman = bridge.isYakuman(rawYaku);
            int multiplier = yakuman ? (DOUBLE_YAKUMAN.contains(rawName) ? 2 : 1) : 0;
            int han = yakuman ? 0 : Math.max(0, bridge.yakuHan(rawYaku) - (open ? bridge.yakuOpenLoss(rawYaku) : 0));
            if (!yakuman && han == 0) {
                continue;
            }
            awards.add(new YakuAward(canonicalName(rawName), han, multiplier, false));
            yakuHan += han;
            yakumanMultiplier += multiplier;
        }
        addBonus(awards, "DORA", dora);
        addBonus(awards, "URADORA", uraDora);
        addBonus(awards, "RED_FIVE", redDora);
        awards.sort(Comparator.comparing(YakuAward::bonus).thenComparing(YakuAward::id));

        boolean hasYaku = yakuHan > 0 || yakumanMultiplier > 0;
        boolean eligible = hasYaku && (yakumanMultiplier > 0 || yakuHan >= request.rules().minimumYakuHan());
        int han = bridge.han(hora);
        int fu = hasYaku ? bridge.fu(hora) : 0;
        Optional<RonPayment> ron = Optional.empty();
        Optional<TsumoPayment> tsumo = Optional.empty();
        boolean dealer = request.seatWind() == top.ellan.mahjong.rules.riichi.model.Wind.EAST;
        if (request.winMethod() == WinMethod.RON) {
            ron = Optional.of(new RonPayment(dealer ? bridge.dealerRon(hora) : bridge.childRon(hora)));
        } else if (dealer) {
            tsumo = Optional.of(new TsumoPayment(0, bridge.dealerTsumoEach(hora)));
        } else {
            tsumo = Optional.of(new TsumoPayment(
                    bridge.childTsumoDealer(hora), bridge.childTsumoChild(hora)));
        }

        return new ScoreResult(
                true,
                hasYaku,
                eligible,
                awards,
                han,
                yakuHan,
                fu,
                dora,
                uraDora,
                redDora,
                yakumanMultiplier,
                limit(han, fu, yakumanMultiplier, request.rules()),
                ron,
                tsumo);
    }

    private static ScoreResult emptyResult(ScoreRequest request, boolean complete) {
        Optional<RonPayment> ron = request.winMethod() == WinMethod.RON
                ? Optional.of(new RonPayment(0)) : Optional.empty();
        Optional<TsumoPayment> tsumo = request.winMethod() == WinMethod.TSUMO
                ? Optional.of(new TsumoPayment(0, 0)) : Optional.empty();
        return new ScoreResult(
                complete, false, false, List.of(), 0, 0, 0, 0, 0, 0, 0,
                Limit.NONE, ron, tsumo);
    }

    private static int countRed(ScoreRequest request) {
        int count = 0;
        for (Tile tile : request.fullConcealedTiles()) {
            if (tile.red()) count++;
        }
        for (Meld meld : request.melds()) {
            for (Tile tile : meld.logicalTiles()) {
                if (tile.red()) count++;
            }
        }
        return count;
    }

    private static int countDora(List<Tile> concealed, List<Meld> melds, List<Tile> indicators) {
        if (indicators.isEmpty()) {
            return 0;
        }
        int[] multipliers = new int[top.ellan.mahjong.rules.riichi.model.TileKind.values().length];
        for (Tile indicator : indicators) {
            multipliers[indicator.kind().dora().ordinal()]++;
        }
        int total = 0;
        for (Tile tile : concealed) {
            total += multipliers[tile.kind().ordinal()];
        }
        for (Meld meld : melds) {
            for (Tile tile : meld.logicalTiles()) {
                total += multipliers[tile.kind().ordinal()];
            }
        }
        return total;
    }

    private static List<String> extraYaku(ScoreRequest request) {
        ArrayList<String> names = new ArrayList<>();
        if (request.doubleRiichi()) {
            names.add("WRichi");
        } else if (request.riichi()) {
            names.add("Richi");
        }
        if (request.ippatsu()) names.add("Ippatsu");
        if (request.rinshan()) names.add("Rinshan");
        if (request.chankan()) names.add("Chankan");
        if (request.lastTile() && !request.rinshan()) {
            names.add(request.winMethod() == WinMethod.TSUMO ? "Haitei" : "Houtei");
        }
        if (request.firstTurn() && request.winMethod() == WinMethod.TSUMO) {
            names.add(request.seatWind() == top.ellan.mahjong.rules.riichi.model.Wind.EAST ? "Tenhou" : "Chihou");
        }
        return List.copyOf(names);
    }

    private static void addBonus(List<YakuAward> awards, String id, int count) {
        if (count > 0) {
            awards.add(new YakuAward(id, count, 0, true));
        }
    }

    private static Limit limit(int han, int fu, int yakumanMultiplier,
                               top.ellan.mahjong.rules.riichi.model.RiichiRules rules) {
        if (yakumanMultiplier > 1) return Limit.MULTIPLE_YAKUMAN;
        if (yakumanMultiplier == 1) return Limit.YAKUMAN;
        if (han >= 13) return rules.kazoeYakuman() ? Limit.YAKUMAN : Limit.SANBAIMAN;
        if (han >= 11) return Limit.SANBAIMAN;
        if (han >= 8) return Limit.BAIMAN;
        if (han >= 6) return Limit.HANEMAN;
        if (han >= 5 || (han == 4 && fu >= 40) || (han == 3 && fu >= 70)
                || (rules.kiriageMangan() && ((han == 4 && fu == 30) || (han == 3 && fu == 60)))) {
            return Limit.MANGAN;
        }
        return Limit.NONE;
    }

    private static String canonicalName(String name) {
        return CANONICAL_NAMES.getOrDefault(name, name.toUpperCase(java.util.Locale.ROOT));
    }

    private static Map<String, String> canonicalNames() {
        return Map.ofEntries(
                Map.entry("Tsumo", "TSUMO"), Map.entry("Pinhu", "PINFU"),
                Map.entry("Tanyao", "TANYAO"), Map.entry("Ipe", "IIPEIKOU"),
                Map.entry("SelfWind", "SELF_WIND"), Map.entry("RoundWind", "ROUND_WIND"),
                Map.entry("Haku", "HAKU"), Map.entry("Hatsu", "HATSU"), Map.entry("Chun", "CHUN"),
                Map.entry("Sanshoku", "SANSHOKU"), Map.entry("Ittsu", "ITTSU"),
                Map.entry("Chanta", "CHANTA"), Map.entry("Chitoi", "CHITOITSU"),
                Map.entry("Toitoi", "TOITOI"), Map.entry("Sananko", "SANANKOU"),
                Map.entry("Honroto", "HONROUTOU"), Map.entry("Sandoko", "SANDOKOU"),
                Map.entry("Sankantsu", "SANKANTSU"), Map.entry("Shosangen", "SHOUSANGEN"),
                Map.entry("Honitsu", "HONITSU"), Map.entry("Junchan", "JUNCHAN"),
                Map.entry("Ryanpe", "RYANPEIKOU"), Map.entry("Chinitsu", "CHINITSU"),
                Map.entry("Kokushi", "KOKUSHIMUSO"), Map.entry("Suanko", "SUANKO"),
                Map.entry("Daisangen", "DAISANGEN"), Map.entry("Tsuiso", "TSUUIISOU"),
                Map.entry("Shousushi", "SHOUSUUSHII"), Map.entry("Lyuiso", "RYUUIISOU"),
                Map.entry("Chinroto", "CHINROUTOU"), Map.entry("Sukantsu", "SUUKANTSU"),
                Map.entry("Churen", "CHUURENPOUTOU"), Map.entry("Daisushi", "DAISUUSHII"),
                Map.entry("ChurenNineWaiting", "JUNSEI_CHUURENPOUTOU"),
                Map.entry("SuankoTanki", "SUANKO_TANKI"),
                Map.entry("KokushiThirteenWaiting", "KOKUSHIMUSO_JUUSANMENMACHI"),
                Map.entry("Richi", "REACH"), Map.entry("Ippatsu", "IPPATSU"),
                Map.entry("Rinshan", "RINSHAN_KAIHOU"), Map.entry("Chankan", "CHANKAN"),
                Map.entry("Haitei", "HAITEI"), Map.entry("Houtei", "HOUTEI"),
                Map.entry("WRichi", "DOUBLE_REACH"), Map.entry("Tenhou", "TENHOU"),
                Map.entry("Chihou", "CHIIHOU"));
    }
}

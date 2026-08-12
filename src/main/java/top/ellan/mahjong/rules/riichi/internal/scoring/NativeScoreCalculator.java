package top.ellan.mahjong.rules.riichi.internal.scoring;

import top.ellan.mahjong.rules.riichi.internal.scoring.BonusCounter.Bonuses;
import top.ellan.mahjong.rules.riichi.internal.scoring.PointCalculator.Payment;
import top.ellan.mahjong.rules.riichi.scoring.Limit;
import top.ellan.mahjong.rules.riichi.scoring.RonPayment;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Pure Java score calculator; contains no reflective or Kotlin runtime boundary. */
public final class NativeScoreCalculator implements ScoreCalculator {
    private static final int CACHE_SIZE = 2_048;
    private final ConcurrentMap<ScoreRequest, ScoreResult> cache =
            new ConcurrentHashMap<>(128);

    @Override
    public ScoreResult score(ScoreRequest request) {
        ScoreResult cached = cache.get(request);
        if (cached != null) return cached;

        ScoreResult calculated = calculate(request);
        ScoreResult concurrent = cache.putIfAbsent(request, calculated);
        if (concurrent == null && cache.size() > CACHE_SIZE) {
            cache.remove(request, calculated);
        }
        return concurrent == null ? calculated : concurrent;
    }

    private static ScoreResult calculate(ScoreRequest request) {
        List<HandPattern> patterns = HandDecomposer.decompose(request);
        if (patterns.isEmpty()) return empty(request, false);

        Bonuses bonuses = BonusCounter.count(request);
        Candidate best = null;
        for (HandPattern pattern : patterns) {
            Candidate candidate = scorePattern(pattern, request, bonuses);
            if (best == null || Candidate.ORDER.compare(candidate, best) > 0) best = candidate;
        }
        return best == null ? empty(request, false) : best.result();
    }

    private static Candidate scorePattern(
            HandPattern pattern, ScoreRequest request, Bonuses bonuses) {
        YakuEvaluator.Evaluation yaku = YakuEvaluator.evaluate(pattern, request);
        boolean hasYaku = yaku.yakuHan() > 0 || yaku.yakumanMultiplier() > 0;
        int fu = hasYaku ? FuCalculator.calculate(pattern, request) : 0;
        int han = yaku.yakumanMultiplier() > 0
                ? Math.multiplyExact(13, yaku.yakumanMultiplier())
                : yaku.yakuHan() > 0 ? yaku.yakuHan() + bonuses.total() : 0;

        ArrayList<YakuAward> awards = new ArrayList<>(yaku.awards());
        if (yaku.yakumanMultiplier() == 0) {
            addBonus(awards, "DORA", bonuses.dora());
            addBonus(awards, "URADORA", bonuses.uraDora());
            addBonus(awards, "RED_FIVE", bonuses.redDora());
        }
        awards.sort(Comparator.comparing(YakuAward::bonus).thenComparing(YakuAward::id));

        boolean eligible = hasYaku
                && (yaku.yakumanMultiplier() > 0
                    || yaku.yakuHan() >= request.rules().minimumYakuHan());
        Payment payment = hasYaku
                ? PointCalculator.calculate(request, han, fu, yaku.yakumanMultiplier())
                : PointCalculator.zero(request);
        ScoreResult result = new ScoreResult(
                true,
                hasYaku,
                eligible,
                awards,
                han,
                yaku.yakuHan(),
                fu,
                bonuses.dora(),
                bonuses.uraDora(),
                bonuses.redDora(),
                yaku.yakumanMultiplier(),
                payment.limit(),
                payment.ron(),
                payment.tsumo());
        return new Candidate(result);
    }

    private static ScoreResult empty(ScoreRequest request, boolean complete) {
        Optional<RonPayment> ron = request.winMethod() == top.ellan.mahjong.rules.riichi.scoring.WinMethod.RON
                ? Optional.of(new RonPayment(0))
                : Optional.empty();
        Optional<TsumoPayment> tsumo = request.winMethod() == top.ellan.mahjong.rules.riichi.scoring.WinMethod.TSUMO
                ? Optional.of(new TsumoPayment(0, 0))
                : Optional.empty();
        return new ScoreResult(
                complete,
                false,
                false,
                List.of(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Limit.NONE,
                ron,
                tsumo);
    }

    private static void addBonus(List<YakuAward> awards, String id, int count) {
        if (count > 0) awards.add(new YakuAward(id, count, 0, true));
    }

    private record Candidate(ScoreResult result) {
        private static final Comparator<Candidate> ORDER = Comparator
                .comparingInt((Candidate candidate) -> candidate.result.yakumanMultiplier())
                .thenComparingInt(candidate -> candidate.result.han())
                .thenComparingInt(candidate -> candidate.result.fu());
    }
}

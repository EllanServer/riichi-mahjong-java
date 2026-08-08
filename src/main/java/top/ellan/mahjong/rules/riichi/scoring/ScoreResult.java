package top.ellan.mahjong.rules.riichi.scoring;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ScoreResult(
        boolean completeHand,
        boolean hasYaku,
        boolean eligibleByMinimumHan,
        List<YakuAward> yaku,
        int han,
        int yakuHan,
        int fu,
        int dora,
        int uraDora,
        int redDora,
        int yakumanMultiplier,
        Limit limit,
        Optional<RonPayment> ronPayment,
        Optional<TsumoPayment> tsumoPayment) {

    public ScoreResult {
        yaku = List.copyOf(Objects.requireNonNull(yaku, "yaku"));
        Objects.requireNonNull(limit, "limit");
        ronPayment = Objects.requireNonNull(ronPayment, "ronPayment");
        tsumoPayment = Objects.requireNonNull(tsumoPayment, "tsumoPayment");
        if (han < 0 || yakuHan < 0 || fu < 0 || dora < 0 || uraDora < 0
                || redDora < 0 || yakumanMultiplier < 0) {
            throw new IllegalArgumentException("score values cannot be negative");
        }
        if (ronPayment.isPresent() == tsumoPayment.isPresent()) {
            throw new IllegalArgumentException("exactly one payment shape is required");
        }
        if (!completeHand && (hasYaku || eligibleByMinimumHan)) {
            throw new IllegalArgumentException("incomplete hand cannot have a legal score");
        }
        if (eligibleByMinimumHan && !hasYaku) {
            throw new IllegalArgumentException("minimum-han eligibility requires at least one yaku");
        }
        if (hasYaku != (yakuHan > 0 || yakumanMultiplier > 0)) {
            throw new IllegalArgumentException("hasYaku disagrees with yaku han and yakuman values");
        }
    }

    public int totalPoints(boolean winnerIsDealer) {
        return ronPayment.<Integer>map(RonPayment::discarderPays)
                .orElseGet(() -> tsumoPayment.orElseThrow().total(winnerIsDealer));
    }
}

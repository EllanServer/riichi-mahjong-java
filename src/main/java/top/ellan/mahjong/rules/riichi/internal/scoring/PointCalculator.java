package top.ellan.mahjong.rules.riichi.internal.scoring;

import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.Limit;
import top.ellan.mahjong.rules.riichi.scoring.RonPayment;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;

import java.util.Optional;

/** Integer-only Japanese point calculation with limit and kiriage handling. */
final class PointCalculator {
    private PointCalculator() {
    }

    static Payment calculate(
            ScoreRequest request, int han, int fu, int yakumanMultiplier) {
        boolean dealer = request.seatWind() == Wind.EAST;
        int base = basePoints(han, fu, yakumanMultiplier, request.rules());
        Optional<RonPayment> ron = Optional.empty();
        Optional<TsumoPayment> tsumo = Optional.empty();
        if (request.winMethod() == WinMethod.RON) {
            ron = Optional.of(new RonPayment(roundUpHundred(base * (dealer ? 6 : 4))));
        } else if (dealer) {
            tsumo = Optional.of(new TsumoPayment(0, roundUpHundred(base * 2)));
        } else {
            tsumo = Optional.of(new TsumoPayment(roundUpHundred(base * 2), roundUpHundred(base)));
        }
        return new Payment(limit(han, fu, yakumanMultiplier, request.rules()), ron, tsumo);
    }

    static Payment zero(ScoreRequest request) {
        if (request.winMethod() == WinMethod.RON) {
            return new Payment(Limit.NONE, Optional.of(new RonPayment(0)), Optional.empty());
        }
        return new Payment(Limit.NONE, Optional.empty(), Optional.of(new TsumoPayment(0, 0)));
    }

    private static int basePoints(
            int han, int fu, int yakumanMultiplier, RiichiRules rules) {
        if (yakumanMultiplier > 0) return Math.multiplyExact(8_000, yakumanMultiplier);
        if (han >= 13) return rules.kazoeYakuman() ? 8_000 : 6_000;
        if (han >= 11) return 6_000;
        if (han >= 8) return 4_000;
        if (han >= 6) return 3_000;
        if (isMangan(han, fu, rules)) return 2_000;
        if (han <= 0) return 0;
        return Math.min(2_000, fu * (1 << (han + 2)));
    }

    private static Limit limit(
            int han, int fu, int yakumanMultiplier, RiichiRules rules) {
        if (yakumanMultiplier > 1) return Limit.MULTIPLE_YAKUMAN;
        if (yakumanMultiplier == 1) return Limit.YAKUMAN;
        if (han >= 13) return rules.kazoeYakuman() ? Limit.YAKUMAN : Limit.SANBAIMAN;
        if (han >= 11) return Limit.SANBAIMAN;
        if (han >= 8) return Limit.BAIMAN;
        if (han >= 6) return Limit.HANEMAN;
        if (isMangan(han, fu, rules)) return Limit.MANGAN;
        return Limit.NONE;
    }

    private static boolean isMangan(int han, int fu, RiichiRules rules) {
        return han >= 5
                || (han == 4 && fu >= 40)
                || (han == 3 && fu >= 70)
                || (rules.kiriageMangan()
                    && ((han == 4 && fu == 30) || (han == 3 && fu == 60)));
    }

    private static int roundUpHundred(int value) {
        return (value + 99) / 100 * 100;
    }

    record Payment(
            Limit limit,
            Optional<RonPayment> ron,
            Optional<TsumoPayment> tsumo) {
    }
}

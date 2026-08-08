package top.ellan.mahjong.rules.riichi.settlement;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mahjong Soul Daisangen/Daisuushii liability payment splitter. */
public final class PaoPaymentCalculator {
    private PaoPaymentCalculator() {
    }

    public static List<PaymentTransfer> ron(
            PlayerId winner,
            PlayerId discarder,
            PlayerId liable,
            boolean winnerDealer,
            int totalYakumanMultiplier,
            int paoYakumanMultiplier,
            int honba) {
        validate(totalYakumanMultiplier, paoYakumanMultiplier, honba);
        Objects.requireNonNull(winner, "winner");
        Objects.requireNonNull(discarder, "discarder");
        Objects.requireNonNull(liable, "liable");
        if (winner.equals(discarder) || winner.equals(liable)) {
            throw new IllegalArgumentException("winner cannot pay their own pao settlement");
        }
        int unit = winnerDealer ? 48_000 : 32_000;
        int paoAmount = Math.multiplyExact(unit, paoYakumanMultiplier);
        int nonPaoAmount = Math.multiplyExact(unit, totalYakumanMultiplier - paoYakumanMultiplier);
        ArrayList<PaymentTransfer> result = new ArrayList<>();
        SettlementAggregator.add(result, discarder, winner, nonPaoAmount, PaymentReason.RON);
        if (discarder.equals(liable)) {
            SettlementAggregator.add(result, liable, winner, paoAmount, PaymentReason.PAO);
        } else {
            SettlementAggregator.add(result, liable, winner, paoAmount / 2, PaymentReason.PAO);
            SettlementAggregator.add(result, discarder, winner, paoAmount / 2, PaymentReason.PAO);
        }
        SettlementAggregator.add(result, liable, winner, Math.multiplyExact(honba, 300), PaymentReason.HONBA);
        return List.copyOf(result);
    }

    public static List<PaymentTransfer> tsumo(
            PlayerId winner,
            PlayerId liable,
            PlayerId dealer,
            List<PlayerId> opponents,
            boolean winnerDealer,
            int totalYakumanMultiplier,
            int paoYakumanMultiplier,
            int honba) {
        validate(totalYakumanMultiplier, paoYakumanMultiplier, honba);
        Objects.requireNonNull(dealer, "dealer");
        List<PlayerId> payers = List.copyOf(opponents);
        if (payers.size() != 3 || payers.stream().distinct().count() != 3
                || !payers.contains(liable) || payers.contains(winner)
                || (winnerDealer && !dealer.equals(winner))
                || (!winnerDealer && !payers.contains(dealer))) {
            throw new IllegalArgumentException("tsumo opponents must be the three non-winners including the liable player");
        }
        int unit = winnerDealer ? 48_000 : 32_000;
        int remainingMultiplier = totalYakumanMultiplier - paoYakumanMultiplier;
        int paoAmount = Math.multiplyExact(unit, paoYakumanMultiplier);
        ArrayList<PaymentTransfer> result = new ArrayList<>();
        SettlementAggregator.add(result, liable, winner, paoAmount, PaymentReason.PAO);
        if (remainingMultiplier > 0) {
            if (winnerDealer) {
                int each = Math.multiplyExact(16_000, remainingMultiplier);
                payers.forEach(payer -> SettlementAggregator.add(result, payer, winner, each, PaymentReason.TSUMO));
            } else {
                for (PlayerId payer : payers) {
                    int amount = Math.multiplyExact(
                            payer.equals(dealer) ? 16_000 : 8_000, remainingMultiplier);
                    SettlementAggregator.add(result, payer, winner, amount, PaymentReason.TSUMO);
                }
            }
        }
        SettlementAggregator.add(result, liable, winner, Math.multiplyExact(honba, 300), PaymentReason.HONBA);
        return List.copyOf(result);
    }

    private static void validate(int totalMultiplier, int paoMultiplier, int honba) {
        if (totalMultiplier < 1 || paoMultiplier < 1 || paoMultiplier > totalMultiplier
                || honba < 0 || honba > Integer.MAX_VALUE / 300) {
            throw new IllegalArgumentException("invalid pao multipliers or honba");
        }
    }
}

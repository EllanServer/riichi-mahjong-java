package top.ellan.mahjong.rules.riichi.settlement;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Aggregates every transfer before a score row is emitted; one account can never appear twice. */
public final class SettlementAggregator {
    private SettlementAggregator() {
    }

    public static AggregatedSettlement aggregate(Collection<PaymentTransfer> transfers) {
        List<PaymentTransfer> immutable = List.copyOf(Objects.requireNonNull(transfers, "transfers"));
        LinkedHashMap<LedgerAccount, Integer> deltas = new LinkedHashMap<>();
        for (PaymentTransfer transfer : immutable) {
            deltas.merge(transfer.payer(), -transfer.amount(), Math::addExact);
            deltas.merge(transfer.payee(), transfer.amount(), Math::addExact);
        }
        return new AggregatedSettlement(immutable, deltas);
    }

    public static List<PaymentTransfer> ron(
            PlayerId winner,
            PlayerId discarder,
            int ronPoints,
            int honba,
            boolean receivesHonba) {
        if (ronPoints < 0 || honba < 0) {
            throw new IllegalArgumentException("points and honba cannot be negative");
        }
        ArrayList<PaymentTransfer> result = new ArrayList<>();
        add(result, discarder, winner, ronPoints, PaymentReason.RON);
        if (receivesHonba) add(result, discarder, winner, Math.multiplyExact(honba, 300), PaymentReason.HONBA);
        return List.copyOf(result);
    }

    public static List<PaymentTransfer> tsumo(
            PlayerId winner,
            Map<PlayerId, Integer> payerAmounts,
            int honba) {
        if (honba < 0) throw new IllegalArgumentException("honba cannot be negative");
        ArrayList<PaymentTransfer> result = new ArrayList<>();
        payerAmounts.forEach((payer, amount) -> {
            add(result, payer, winner, amount, PaymentReason.TSUMO);
            add(result, payer, winner, Math.multiplyExact(honba, 100), PaymentReason.HONBA);
        });
        return List.copyOf(result);
    }

    public static List<PaymentTransfer> noten(
            Collection<PlayerId> tenpaiPlayers,
            Collection<PlayerId> notenPlayers) {
        List<PlayerId> tenpai = List.copyOf(Objects.requireNonNull(tenpaiPlayers, "tenpaiPlayers"));
        List<PlayerId> noten = List.copyOf(Objects.requireNonNull(notenPlayers, "notenPlayers"));
        if (tenpai.stream().distinct().count() != tenpai.size()
                || noten.stream().distinct().count() != noten.size()
                || tenpai.stream().anyMatch(noten::contains)
                || tenpai.size() + noten.size() != 4) {
            throw new IllegalArgumentException("noten settlement requires a four-player partition");
        }
        if (tenpai.isEmpty() || noten.isEmpty()) return List.of();

        int gainPerTenpai = 3_000 / tenpai.size();
        int lossPerNoten = 3_000 / noten.size();
        ArrayList<PaymentTransfer> result = new ArrayList<>();
        int tenpaiIndex = 0;
        int notenIndex = 0;
        int gainRemaining = gainPerTenpai;
        int lossRemaining = lossPerNoten;
        while (tenpaiIndex < tenpai.size() && notenIndex < noten.size()) {
            int amount = Math.min(gainRemaining, lossRemaining);
            add(result, noten.get(notenIndex), tenpai.get(tenpaiIndex), amount, PaymentReason.NOTEN);
            gainRemaining -= amount;
            lossRemaining -= amount;
            if (gainRemaining == 0) {
                tenpaiIndex++;
                gainRemaining = gainPerTenpai;
            }
            if (lossRemaining == 0) {
                notenIndex++;
                lossRemaining = lossPerNoten;
            }
        }
        return List.copyOf(result);
    }

    public static List<PaymentTransfer> nagashiMangan(
            PlayerId winner,
            Map<PlayerId, Integer> payerAmounts) {
        Objects.requireNonNull(winner, "winner");
        Map<PlayerId, Integer> payers = Map.copyOf(Objects.requireNonNull(payerAmounts, "payerAmounts"));
        if (payers.size() != 3 || payers.containsKey(winner)
                || payers.values().stream().anyMatch(amount -> amount == null || amount <= 0)) {
            throw new IllegalArgumentException("nagashi mangan requires three positive opponent payments");
        }
        ArrayList<PaymentTransfer> result = new ArrayList<>();
        payers.forEach((payer, amount) -> add(
                result, payer, winner, amount, PaymentReason.NAGASHI_MANGAN));
        return List.copyOf(result);
    }

    public static PaymentTransfer riichiPool(PlayerId winner, int sticks) {
        if (sticks <= 0) throw new IllegalArgumentException("riichi sticks must be positive");
        return new PaymentTransfer(
                LedgerAccount.table(), LedgerAccount.player(winner),
                Math.multiplyExact(sticks, 1_000), PaymentReason.RIICHI_POOL);
    }

    public static PaymentTransfer riichiRefund(PlayerId declarer) {
        return new PaymentTransfer(
                LedgerAccount.table(), LedgerAccount.player(declarer), 1_000, PaymentReason.RIICHI_REFUND);
    }

    static void add(
            List<PaymentTransfer> output,
            PlayerId payer,
            PlayerId payee,
            int amount,
            PaymentReason reason) {
        if (amount > 0) {
            output.add(new PaymentTransfer(
                    LedgerAccount.player(payer), LedgerAccount.player(payee), amount, reason));
        }
    }
}

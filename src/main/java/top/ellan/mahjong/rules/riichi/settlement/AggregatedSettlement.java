package top.ellan.mahjong.rules.riichi.settlement;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record AggregatedSettlement(
        List<PaymentTransfer> transfers,
        Map<LedgerAccount, Integer> accountDeltas) {
    public AggregatedSettlement {
        transfers = List.copyOf(Objects.requireNonNull(transfers, "transfers"));
        accountDeltas = Map.copyOf(Objects.requireNonNull(accountDeltas, "accountDeltas"));
        LinkedHashMap<LedgerAccount, Integer> expected = new LinkedHashMap<>();
        for (PaymentTransfer transfer : transfers) {
            expected.merge(transfer.payer(), -transfer.amount(), Math::addExact);
            expected.merge(transfer.payee(), transfer.amount(), Math::addExact);
        }
        if (!expected.equals(accountDeltas)) {
            throw new IllegalArgumentException("account deltas do not match payment transfers");
        }
        long sum = accountDeltas.values().stream().mapToLong(Integer::longValue).sum();
        if (sum != 0) {
            throw new IllegalArgumentException("settlement is not zero-sum: " + sum);
        }
    }

    public Map<PlayerId, Integer> playerDeltas() {
        LinkedHashMap<PlayerId, Integer> players = new LinkedHashMap<>();
        accountDeltas.forEach((account, delta) -> account.playerId().ifPresent(player -> players.merge(player, delta, Integer::sum)));
        return Map.copyOf(players);
    }
}

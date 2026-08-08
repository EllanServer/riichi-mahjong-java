package top.ellan.mahjong.rules.riichi.settlement;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementAggregationTest {
    private static final PlayerId A = new PlayerId("a");
    private static final PlayerId B = new PlayerId("b");
    private static final PlayerId C = new PlayerId("c");
    private static final PlayerId D = new PlayerId("d");

    @Test
    void multiRonAndCrossLiabilityProduceOneDeltaPerPlayer() {
        ArrayList<PaymentTransfer> transfers = new ArrayList<>();
        transfers.addAll(SettlementAggregator.ron(A, D, 8_000, 1, true));
        // A wins, but is also the liable player for B's yakuman in the same multi-ron.
        transfers.addAll(PaoPaymentCalculator.ron(B, D, A, false, 1, 1, 1));
        AggregatedSettlement settlement = SettlementAggregator.aggregate(transfers);
        assertEquals(3, settlement.playerDeltas().size());
        assertEquals(Integer.valueOf(8_300 - 16_300), settlement.playerDeltas().get(A));
        assertEquals(Integer.valueOf(32_300), settlement.playerDeltas().get(B));
        assertEquals(Integer.valueOf(-24_300), settlement.playerDeltas().get(D));
        assertEquals(0, settlement.accountDeltas().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void riichiPoolIsRepresentedAsATableAccount() {
        AggregatedSettlement settlement = SettlementAggregator.aggregate(List.of(
                SettlementAggregator.riichiPool(C, 2)));
        assertEquals(Integer.valueOf(2_000), settlement.playerDeltas().get(C));
        assertEquals(Integer.valueOf(-2_000), settlement.accountDeltas().get(LedgerAccount.table()));
    }

    @Test
    void paoTsumoChargesLiablePlayerForFullPaoYakumanAndHonba() {
        List<PaymentTransfer> transfers = PaoPaymentCalculator.tsumo(
                A, B, D, List.of(B, C, D), false, 1, 1, 2);
        AggregatedSettlement settlement = SettlementAggregator.aggregate(transfers);
        assertEquals(Integer.valueOf(-32_600), settlement.playerDeltas().get(B));
        assertEquals(Integer.valueOf(32_600), settlement.playerDeltas().get(A));
    }

    @Test
    void paoTsumoRejectsDuplicateOrIncorrectOpponentSets() {
        assertThrows(IllegalArgumentException.class, () -> PaoPaymentCalculator.tsumo(
                A, B, D, List.of(B, B, D), false, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> PaoPaymentCalculator.tsumo(
                A, B, C, List.of(B, C, D), true, 1, 1, 0));
    }

    @Test
    void riichiDeclarationRefundIsAnExplicitTableTransfer() {
        AggregatedSettlement settlement = SettlementAggregator.aggregate(List.of(
                SettlementAggregator.riichiRefund(D)));
        assertEquals(Integer.valueOf(1_000), settlement.playerDeltas().get(D));
        assertEquals(Integer.valueOf(-1_000), settlement.accountDeltas().get(LedgerAccount.table()));
        assertEquals(PaymentReason.RIICHI_REFUND, settlement.transfers().getFirst().reason());
    }
}

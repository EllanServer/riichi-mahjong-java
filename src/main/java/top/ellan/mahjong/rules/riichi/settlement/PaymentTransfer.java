package top.ellan.mahjong.rules.riichi.settlement;

import java.util.Objects;

public record PaymentTransfer(
        LedgerAccount payer,
        LedgerAccount payee,
        int amount,
        PaymentReason reason) {
    public PaymentTransfer {
        Objects.requireNonNull(payer, "payer");
        Objects.requireNonNull(payee, "payee");
        Objects.requireNonNull(reason, "reason");
        if (payer.equals(payee)) {
            throw new IllegalArgumentException("payer and payee must differ");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("transfer amount must be positive");
        }
    }
}

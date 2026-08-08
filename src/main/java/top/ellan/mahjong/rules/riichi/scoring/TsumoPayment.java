package top.ellan.mahjong.rules.riichi.scoring;

/** For a dealer win dealerPays is zero and each opponent pays nonDealerPaysEach. */
public record TsumoPayment(int dealerPays, int nonDealerPaysEach) {
    public TsumoPayment {
        if (dealerPays < 0 || nonDealerPaysEach < 0) {
            throw new IllegalArgumentException("payment cannot be negative");
        }
    }

    public int total(boolean winnerIsDealer) {
        return winnerIsDealer
                ? Math.multiplyExact(nonDealerPaysEach, 3)
                : Math.addExact(dealerPays, Math.multiplyExact(nonDealerPaysEach, 2));
    }
}

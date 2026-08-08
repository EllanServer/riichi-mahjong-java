package top.ellan.mahjong.rules.riichi.scoring;

public record RonPayment(int discarderPays) {
    public RonPayment {
        if (discarderPays < 0) {
            throw new IllegalArgumentException("payment cannot be negative");
        }
    }
}

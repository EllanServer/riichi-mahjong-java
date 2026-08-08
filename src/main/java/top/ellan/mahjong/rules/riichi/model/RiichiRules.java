package top.ellan.mahjong.rules.riichi.model;

import java.util.Objects;

public record RiichiRules(
        Profile profile,
        RonMode ronMode,
        int startingPoints,
        int targetPoints,
        int minimumYakuHan,
        RedFiveConfiguration redFives,
        boolean openTanyao,
        boolean kiriageMangan,
        boolean kazoeYakuman,
        boolean multipleYakuman,
        boolean complexYakuman) {

    public enum Profile { MAJSOUL, EARLY_KAN_DORA }
    public enum RonMode { MULTI_RON, HEAD_BUMP }

    public RiichiRules {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(ronMode, "ronMode");
        Objects.requireNonNull(redFives, "redFives");
        if (startingPoints <= 0 || targetPoints <= 0) {
            throw new IllegalArgumentException("points must be positive");
        }
        if (minimumYakuHan != 1 && minimumYakuHan != 2
                && minimumYakuHan != 4 && minimumYakuHan != 13) {
            throw new IllegalArgumentException("minimum yaku han must be 1, 2, 4, or 13");
        }
    }

    public static RiichiRules mahjongSoul() {
        return new RiichiRules(
                Profile.MAJSOUL,
                RonMode.MULTI_RON,
                25_000,
                30_000,
                1,
                RedFiveConfiguration.standardThree(),
                true,
                false,
                true,
                true,
                true);
    }
}

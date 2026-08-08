package top.ellan.mahjong.rules.riichi.scoring;

/** One scored yaku or bonus. Yakuman use multiplier rather than han. */
public record YakuAward(String id, int han, int yakumanMultiplier, boolean bonus) {
    public YakuAward {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("yaku id is required");
        }
        if (han < 0 || yakumanMultiplier < 0 || (han > 0 && yakumanMultiplier > 0)) {
            throw new IllegalArgumentException("invalid yaku value");
        }
    }
}

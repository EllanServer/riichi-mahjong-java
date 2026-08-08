package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Wind;

import java.util.Objects;

/** Match-length policy layered over the hand-level rules. */
public record RiichiMatchRules(
        RiichiRules roundRules,
        Wind scheduledLastWind,
        Wind maximumWind,
        boolean bustEndsMatch) {
    public RiichiMatchRules {
        Objects.requireNonNull(roundRules, "roundRules");
        Objects.requireNonNull(scheduledLastWind, "scheduledLastWind");
        Objects.requireNonNull(maximumWind, "maximumWind");
        if (scheduledLastWind.ordinal() > maximumWind.ordinal()) {
            throw new IllegalArgumentException("maximum wind precedes the scheduled last wind");
        }
    }

    public static RiichiMatchRules mahjongSoulHanchan() {
        return new RiichiMatchRules(RiichiRules.mahjongSoul(), Wind.SOUTH, Wind.WEST, true);
    }
}

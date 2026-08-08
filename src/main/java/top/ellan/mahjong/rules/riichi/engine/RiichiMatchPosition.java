package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.Wind;

import java.util.Objects;

/** Position and carried counters for the active or next hand. */
public record RiichiMatchPosition(
        Wind roundWind,
        int handNumber,
        int dealerIndex,
        int honba,
        int riichiSticks) {
    public RiichiMatchPosition {
        Objects.requireNonNull(roundWind, "roundWind");
        if (handNumber < 1 || handNumber > 4) {
            throw new IllegalArgumentException("hand number must be in [1, 4]");
        }
        if (dealerIndex < 0 || dealerIndex > 3) {
            throw new IllegalArgumentException("dealer index must be in [0, 3]");
        }
        if (honba < 0 || riichiSticks < 0) {
            throw new IllegalArgumentException("match counters cannot be negative");
        }
    }

    public static RiichiMatchPosition eastOne() {
        return new RiichiMatchPosition(Wind.EAST, 1, 0, 0, 0);
    }
}

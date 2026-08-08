package top.ellan.mahjong.rules.riichi.evaluation;

import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.util.Objects;
import java.util.Set;

public record HandAnalysis(int shanten, Set<TileKind> waits) {
    public HandAnalysis {
        if (shanten < -1 || shanten > 8) {
            throw new IllegalArgumentException("invalid shanten: " + shanten);
        }
        waits = Set.copyOf(Objects.requireNonNull(waits, "waits"));
        if (shanten != 0 && !waits.isEmpty()) {
            throw new IllegalArgumentException("waits are valid only for a zero-shanten hand");
        }
    }

    public boolean complete() {
        return shanten == -1;
    }

    public boolean tenpai() {
        return shanten == 0 && !waits.isEmpty();
    }
}

package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Enforces the four-kan physical limit at both action-discovery and submission time. */
public final class KanTracker {
    public enum Registration { CONTINUE, ABORT_AFTER_DISCARD }

    private final Map<PlayerId, Integer> byPlayer = new LinkedHashMap<>();
    private int total;

    public KanTracker() {
    }

    public KanTracker(Map<PlayerId, Integer> existingKans) {
        Objects.requireNonNull(existingKans, "existingKans").forEach((player, count) -> {
            if (player == null || count == null || count < 0 || count > 4) {
                throw new IllegalArgumentException("invalid existing kan count");
            }
            byPlayer.put(player, count);
            total += count;
        });
        if (total > 4) {
            throw new IllegalArgumentException("a Riichi hand cannot contain more than four kans");
        }
    }

    public boolean canDeclareSelfKan() {
        return total < 4;
    }

    public boolean canOfferDiscardKan() {
        return total < 4;
    }

    public Registration registerSelfKan(PlayerId player) {
        return register(player);
    }

    public Registration registerDiscardKan(PlayerId player) {
        return register(player);
    }

    public int total() {
        return total;
    }

    public Map<PlayerId, Integer> byPlayer() {
        return Map.copyOf(byPlayer);
    }

    private Registration register(PlayerId player) {
        Objects.requireNonNull(player, "player");
        if (total >= 4) {
            throw new RuleViolationException(RuleViolation.FIFTH_KAN_FORBIDDEN, "a fifth kan is forbidden");
        }
        byPlayer.merge(player, 1, Integer::sum);
        total++;
        return total == 4 && byPlayer.size() > 1
                ? Registration.ABORT_AFTER_DISCARD
                : Registration.CONTINUE;
    }
}

package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic decision made at an ended-hand boundary. */
public record RiichiMatchAdvance(
        boolean matchEnded,
        boolean dealerContinues,
        RiichiMatchPosition position,
        Map<PlayerId, Integer> scores,
        List<PlayerId> ranking,
        Optional<String> endReason) {
    public RiichiMatchAdvance {
        Objects.requireNonNull(position, "position");
        scores = Map.copyOf(Objects.requireNonNull(scores, "scores"));
        ranking = List.copyOf(Objects.requireNonNull(ranking, "ranking"));
        endReason = Objects.requireNonNull(endReason, "endReason");
        if (matchEnded != endReason.isPresent()) {
            throw new IllegalArgumentException("match end flag and reason disagree");
        }
        if (ranking.size() != scores.size() || !scores.keySet().equals(Set.copyOf(ranking))) {
            throw new IllegalArgumentException("ranking must contain every scored player exactly once");
        }
    }
}

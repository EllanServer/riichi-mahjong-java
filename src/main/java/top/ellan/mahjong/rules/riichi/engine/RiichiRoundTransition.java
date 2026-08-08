package top.ellan.mahjong.rules.riichi.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of applying one command to an immutable round revision. */
public record RiichiRoundTransition(
        boolean accepted,
        RiichiRoundState state,
        List<RoundEvent> events,
        Optional<RuleViolation> violation,
        String message) {
    public RiichiRoundTransition {
        Objects.requireNonNull(state, "state");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        violation = Objects.requireNonNull(violation, "violation");
        message = Objects.requireNonNull(message, "message");
        if (accepted == violation.isPresent()) {
            throw new IllegalArgumentException("accepted result and violation disagree");
        }
        if (!accepted && !events.isEmpty()) {
            throw new IllegalArgumentException("rejected transition cannot publish events");
        }
    }
}

package top.ellan.mahjong.rules.riichi.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of a round command or explicit between-round advance. */
public record RiichiMatchTransition(
        boolean accepted,
        RiichiMatchState state,
        List<RoundEvent> roundEvents,
        List<RiichiMatchEvent> matchEvents,
        Optional<RuleViolation> violation,
        String message) {
    public RiichiMatchTransition {
        Objects.requireNonNull(state, "state");
        roundEvents = List.copyOf(Objects.requireNonNull(roundEvents, "roundEvents"));
        matchEvents = List.copyOf(Objects.requireNonNull(matchEvents, "matchEvents"));
        violation = Objects.requireNonNull(violation, "violation");
        message = Objects.requireNonNull(message, "message");
        if (accepted == violation.isPresent()) {
            throw new IllegalArgumentException("accepted match transition and violation disagree");
        }
        if (!accepted && (!roundEvents.isEmpty() || !matchEvents.isEmpty())) {
            throw new IllegalArgumentException("rejected match transition cannot publish events");
        }
    }
}

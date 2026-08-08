package top.ellan.mahjong.rules.riichi.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CommandResult(
        boolean accepted,
        Optional<RuleViolation> violation,
        String message,
        List<RoundEvent> events,
        RoundSnapshot snapshot) {
    public CommandResult {
        violation = Objects.requireNonNull(violation, "violation");
        message = Objects.requireNonNull(message, "message");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
        Objects.requireNonNull(snapshot, "snapshot");
        if (accepted == violation.isPresent()) {
            throw new IllegalArgumentException("accepted result and violation disagree");
        }
    }
}

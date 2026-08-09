package top.ellan.mahjong.rules.riichi.engine;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Copy-on-write transition boundary suitable for actor and async execution. */
public final class RiichiRoundEngine {
    public RiichiRoundTransition apply(RiichiRoundState state, RoundCommand command) {
        Objects.requireNonNull(state, "state");
        if (command == null) {
            return new RiichiRoundTransition(
                    false,
                    state,
                    List.of(),
                    Optional.of(RuleViolation.ILLEGAL_PHASE),
                    "command must not be null");
        }

        RiichiRound fork = state.forkImage();
        CommandResult result = fork.apply(command);
        if (!result.accepted()) {
            return new RiichiRoundTransition(
                    false,
                    state,
                    List.of(),
                    result.violation(),
                    result.message());
        }

        return new RiichiRoundTransition(
                true,
                state.advanced(fork, command),
                result.events(),
                Optional.empty(),
                result.message());
    }
}

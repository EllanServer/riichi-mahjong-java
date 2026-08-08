package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ReactionResolution(
        List<PlayerId> ronWinners,
        Optional<PlayerId> caller,
        Optional<Reaction> call) {
    public ReactionResolution {
        ronWinners = List.copyOf(Objects.requireNonNull(ronWinners, "ronWinners"));
        caller = Objects.requireNonNull(caller, "caller");
        call = Objects.requireNonNull(call, "call");
        if (caller.isPresent() != call.isPresent()) {
            throw new IllegalArgumentException("caller and call must be present together");
        }
        if (!ronWinners.isEmpty() && caller.isPresent()) {
            throw new IllegalArgumentException("ron and a meld cannot resolve together");
        }
    }

    public static ReactionResolution noClaim() {
        return new ReactionResolution(List.of(), Optional.empty(), Optional.empty());
    }
}

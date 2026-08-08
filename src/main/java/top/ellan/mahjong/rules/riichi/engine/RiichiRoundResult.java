package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Match-facing summary of an ended hand. */
public record RiichiRoundResult(
        Map<PlayerId, Integer> scores,
        Set<PlayerId> winners,
        Set<PlayerId> tenpaiPlayers,
        Set<PlayerId> nagashiWinners,
        Optional<AbortiveDraw> abortiveDraw,
        String endReason,
        int riichiSticks) {
    public RiichiRoundResult {
        scores = Map.copyOf(Objects.requireNonNull(scores, "scores"));
        winners = Set.copyOf(Objects.requireNonNull(winners, "winners"));
        tenpaiPlayers = Set.copyOf(Objects.requireNonNull(tenpaiPlayers, "tenpaiPlayers"));
        nagashiWinners = Set.copyOf(Objects.requireNonNull(nagashiWinners, "nagashiWinners"));
        abortiveDraw = Objects.requireNonNull(abortiveDraw, "abortiveDraw");
        if (endReason == null || endReason.isBlank()) {
            throw new IllegalArgumentException("end reason is required");
        }
        if (riichiSticks < 0) throw new IllegalArgumentException("riichi sticks cannot be negative");
        if (!scores.keySet().containsAll(winners)
                || !scores.keySet().containsAll(tenpaiPlayers)
                || !scores.keySet().containsAll(nagashiWinners)) {
            throw new IllegalArgumentException("round result references an unknown player");
        }
    }

    public static RiichiRoundResult from(RoundSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (snapshot.phase() != RoundPhase.ENDED) {
            throw new IllegalArgumentException("round snapshot has not ended");
        }
        return new RiichiRoundResult(
                snapshot.scores(),
                snapshot.winners(),
                snapshot.tenpaiPlayers(),
                snapshot.nagashiWinners(),
                snapshot.abortiveDraw(),
                snapshot.endReason().orElseThrow(),
                snapshot.riichiSticks());
    }
}

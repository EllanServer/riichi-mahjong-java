package top.ellan.mahjong.rules.riichi.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable complete-match command boundary. */
public final class RiichiMatchEngine {
    private final RiichiRoundEngine roundEngine = new RiichiRoundEngine();

    public RiichiMatchTransition applyRoundCommand(
            RiichiMatchState state,
            RoundCommand command) {
        Objects.requireNonNull(state, "state");
        if (state.phase() != RiichiMatchPhase.ACTIVE_ROUND) {
            return rejected(state, "match is not accepting round commands");
        }

        RiichiRoundTransition round = roundEngine.apply(state.roundState(), command);
        if (!round.accepted()) {
            return new RiichiMatchTransition(
                    false,
                    state,
                    List.of(),
                    List.of(),
                    round.violation(),
                    round.message());
        }
        RoundSnapshot snapshot = round.state().publicSnapshot();
        if (snapshot.phase() != RoundPhase.ENDED) {
            return new RiichiMatchTransition(
                    true,
                    state.withRound(round.state()),
                    round.events(),
                    List.of(),
                    Optional.empty(),
                    round.message());
        }

        RiichiMatchAdvance advance = RiichiMatchProgression.advance(
                state.rules(),
                state.seats(),
                state.position(),
                RiichiRoundResult.from(snapshot));
        RiichiMatchState updated = state.atBoundary(round.state(), advance);
        ArrayList<RiichiMatchEvent> events = new ArrayList<>();
        events.add(new RiichiMatchEvent(
                RiichiMatchEvent.Type.ROUND_ENDED,
                snapshot.endReason().orElseThrow()));
        if (advance.matchEnded()) {
            events.add(new RiichiMatchEvent(
                    RiichiMatchEvent.Type.MATCH_ENDED,
                    advance.endReason().orElseThrow()));
        }
        return new RiichiMatchTransition(
                true,
                updated,
                round.events(),
                events,
                Optional.empty(),
                round.message());
    }

    public RiichiMatchTransition startNextRound(RiichiMatchState state) {
        Objects.requireNonNull(state, "state");
        if (state.phase() != RiichiMatchPhase.BETWEEN_ROUNDS) {
            return rejected(state, "match is not between rounds");
        }
        RiichiMatchState next = state.startNextRound();
        RiichiMatchPosition position = next.position();
        String detail = position.roundWind().name() + '-' + position.handNumber()
                + " honba=" + position.honba();
        return new RiichiMatchTransition(
                true,
                next,
                List.of(),
                List.of(new RiichiMatchEvent(RiichiMatchEvent.Type.ROUND_STARTED, detail)),
                Optional.empty(),
                "next round started");
    }

    private static RiichiMatchTransition rejected(RiichiMatchState state, String message) {
        return new RiichiMatchTransition(
                false,
                state,
                List.of(),
                List.of(),
                Optional.of(RuleViolation.ILLEGAL_PHASE),
                message);
    }
}

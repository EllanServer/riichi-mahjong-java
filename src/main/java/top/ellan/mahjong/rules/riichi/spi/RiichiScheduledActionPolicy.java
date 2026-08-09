package top.ellan.mahjong.rules.riichi.spi;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.rules.riichi.engine.ReactionType;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPhase;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RoundCommand;
import top.ellan.mahjong.rules.riichi.engine.RoundPhase;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.spi.RuleAction;
import top.ellan.mahjong.spi.ScheduledRuleAction;

/** Deterministic one-shot policy for automatic table actions and reaction expiry. */
final class RiichiScheduledActionPolicy {
    static final String SKIP_REACTIONS_TYPE = "system.skip_reactions";
    static final Duration DRAW_DELAY = Duration.ofMillis(700);
    static final Duration REACTION_DELAY = Duration.ofSeconds(5);
    static final Duration NEXT_ROUND_DELAY = Duration.ofSeconds(3);

    private static final RuleAction DRAW = new RuleAction("draw", new byte[0]);
    private static final RuleAction SKIP_REACTIONS =
            new RuleAction(SKIP_REACTIONS_TYPE, new byte[0]);
    private static final RuleAction START_NEXT_HAND =
            new RuleAction("start_next_hand", new byte[0]);

    Optional<ScheduledRuleAction> next(RiichiProviderState state) {
        RiichiMatchState match = state.match();
        if (match.phase() == RiichiMatchPhase.BETWEEN_ROUNDS) {
            return Optional.of(new ScheduledRuleAction(
                    nextDealer(state),
                    START_NEXT_HAND,
                    NEXT_ROUND_DELAY,
                    "riichi.next_round"));
        }
        if (match.phase() != RiichiMatchPhase.ACTIVE_ROUND) {
            return Optional.empty();
        }

        var snapshot = match.roundState().publicSnapshot();
        if (snapshot.phase() == RoundPhase.AWAITING_DRAW) {
            return Optional.of(new ScheduledRuleAction(
                    spiPlayer(state, snapshot.currentPlayer()),
                    DRAW,
                    DRAW_DELAY,
                    "riichi.automatic_draw"));
        }
        if (snapshot.phase() == RoundPhase.AWAITING_REACTIONS
                && !skipCommands(match).isEmpty()) {
            return Optional.of(new ScheduledRuleAction(
                    spiPlayer(state, snapshot.currentPlayer()),
                    SKIP_REACTIONS,
                    REACTION_DELAY,
                    "riichi.reaction_timeout"));
        }
        return Optional.empty();
    }

    boolean isActorOwned(RuleAction action) {
        return SKIP_REACTIONS_TYPE.equals(action.type());
    }

    Optional<List<RoundCommand>> decodeActorOwned(
            RiichiProviderState state,
            top.ellan.mahjong.spi.PlayerId actor,
            RuleAction action) {
        if (!isActorOwned(action)
                || action.payload().length != 0
                || state.match().phase() != RiichiMatchPhase.ACTIVE_ROUND) {
            return Optional.empty();
        }
        var snapshot = state.match().roundState().publicSnapshot();
        if (snapshot.phase() != RoundPhase.AWAITING_REACTIONS
                || !spiPlayer(state, snapshot.currentPlayer()).equals(actor)) {
            return Optional.empty();
        }
        List<RoundCommand> commands = skipCommands(state.match());
        return commands.isEmpty() ? Optional.empty() : Optional.of(commands);
    }

    boolean mayStartNextRound(
            RiichiProviderState state, top.ellan.mahjong.spi.PlayerId actor) {
        return state.match().phase() == RiichiMatchPhase.BETWEEN_ROUNDS
                && nextDealer(state).equals(actor);
    }

    private static List<RoundCommand> skipCommands(RiichiMatchState match) {
        ArrayList<RoundCommand> result = new ArrayList<>(3);
        for (PlayerId player : match.seats()) {
            for (RoundCommand command : match.roundState().legalCommands(player)) {
                if (command instanceof RoundCommand.Respond response
                        && response.reaction().type() == ReactionType.SKIP) {
                    result.add(command);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    private static top.ellan.mahjong.spi.PlayerId nextDealer(RiichiProviderState state) {
        return state.player(state.match().position().dealerIndex());
    }

    private static top.ellan.mahjong.spi.PlayerId spiPlayer(
            RiichiProviderState state, PlayerId domainPlayer) {
        int initialSeat = state.match().seats().indexOf(domainPlayer);
        if (initialSeat < 0) {
            throw new IllegalStateException("Riichi round references an unseated player");
        }
        return state.player(initialSeat);
    }
}

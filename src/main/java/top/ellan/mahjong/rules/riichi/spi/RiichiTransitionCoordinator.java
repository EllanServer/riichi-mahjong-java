package top.ellan.mahjong.rules.riichi.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchEngine;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchEvent;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchTransition;
import top.ellan.mahjong.rules.riichi.engine.RoundCommand;
import top.ellan.mahjong.rules.riichi.engine.RoundEvent;

/** Composes generated commands while keeping their intermediate revisions private to the SPI. */
final class RiichiTransitionCoordinator {
    private final RiichiMatchEngine engine = new RiichiMatchEngine();

    RiichiMatchTransition apply(RiichiMatchState state, RoundCommand command) {
        return engine.applyRoundCommand(state, command);
    }

    RiichiMatchTransition startNextRound(RiichiMatchState state) {
        return engine.startNextRound(state);
    }

    RiichiMatchTransition applyActorOwned(
            RiichiMatchState state, List<RoundCommand> commands) {
        RiichiMatchState next = state;
        ArrayList<RoundEvent> roundEvents = new ArrayList<>(commands.size());
        ArrayList<RiichiMatchEvent> matchEvents = new ArrayList<>(1);
        for (RoundCommand command : commands) {
            RiichiMatchTransition transition = engine.applyRoundCommand(next, command);
            if (!transition.accepted()) {
                throw new IllegalStateException(
                        "generated Riichi actor action was rejected: " + transition.message());
            }
            next = transition.state();
            roundEvents.addAll(transition.roundEvents());
            matchEvents.addAll(transition.matchEvents());
        }
        return new RiichiMatchTransition(
                true,
                next,
                roundEvents,
                matchEvents,
                Optional.empty(),
                "reaction deadline resolved");
    }
}

package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.RiichiMatchEvent;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPhase;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchRules;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchTransition;
import top.ellan.mahjong.rules.riichi.engine.RoundCommand;
import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Official Riichi rule pack exposing the full match through the rule SDK. */
public final class RiichiRulePackProvider implements top.ellan.mahjong.spi.RulePackProvider {
    public static final top.ellan.mahjong.spi.RuleId RULE_ID =
            new top.ellan.mahjong.spi.RuleId("riichi");
    public static final top.ellan.mahjong.spi.ProfileId PROFILE_ID =
            new top.ellan.mahjong.spi.ProfileId("mahjong-soul");
    public static final String PACK_VERSION = "2.0.2";

    private final top.ellan.mahjong.spi.RulePackDescriptor descriptor = descriptorValue();
    private final RiichiTransitionCoordinator transitions = new RiichiTransitionCoordinator();
    private final RiichiScheduledActionPolicy scheduledActions =
            new RiichiScheduledActionPolicy();
    private final RiichiAutomationPolicy automation = new RiichiAutomationPolicy();
    private final RiichiProviderSnapshotCodec snapshots = new RiichiProviderSnapshotCodec();
    private final RiichiViewProjector projector = new RiichiViewProjector();

    @Override
    public top.ellan.mahjong.spi.RulePackDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public top.ellan.mahjong.spi.RuleState createMatch(top.ellan.mahjong.spi.MatchSetup setup) {
        if (setup == null) {
            throw new NullPointerException("setup");
        }
        if (!PROFILE_ID.equals(setup.profileId())) {
            throw new IllegalArgumentException("unsupported Riichi profile: " + setup.profileId());
        }
        if (!setup.configuration().isEmpty()) {
            throw new IllegalArgumentException("the mahjong-soul profile has no house-rule options");
        }
        if (setup.players().size() != 4) {
            throw new IllegalArgumentException("the mahjong-soul profile requires four players");
        }
        top.ellan.mahjong.spi.PlayerId[] players = new top.ellan.mahjong.spi.PlayerId[4];
        for (top.ellan.mahjong.spi.MatchPlayer player : setup.players()) {
            int seat = player.seatId().value();
            if (seat >= players.length || players[seat] != null) {
                throw new IllegalArgumentException(
                        "initial seats 0 through 3 must each be assigned once");
            }
            players[seat] = player.playerId();
        }
        for (top.ellan.mahjong.spi.PlayerId player : players) {
            if (player == null) {
                throw new IllegalArgumentException(
                        "initial seats 0 through 3 must each be assigned once");
            }
        }
        ArrayList<PlayerId> seats = new ArrayList<>(4);
        for (top.ellan.mahjong.spi.PlayerId player : players) {
            seats.add(RiichiProviderState.toDomain(player));
        }
        RiichiMatchState match = RiichiMatchState.start(
                RiichiMatchRules.mahjongSoulHanchan(), seats, deriveMatchSeed(setup.seed()));
        return new RiichiProviderState(match, List.of(players));
    }

    @Override
    public top.ellan.mahjong.spi.RuleTransition transition(
            top.ellan.mahjong.spi.RuleState state,
            top.ellan.mahjong.spi.PlayerId actor,
            top.ellan.mahjong.spi.RuleAction action) {
        RiichiProviderState current = requireState(state);
        if (actor == null || action == null) {
            return top.ellan.mahjong.spi.RuleTransition.rejected(current, "null_input");
        }
        int initialSeat = current.initialSeat(actor);
        if (initialSeat < 0) {
            return top.ellan.mahjong.spi.RuleTransition.rejected(current, "actor_not_seated");
        }
        PlayerId domainActor = current.match().seats().get(initialSeat);
        if (scheduledActions.isActorOwned(action)) {
            var commands = scheduledActions.decodeActorOwned(current, actor, action);
            if (commands.isEmpty()) {
                return top.ellan.mahjong.spi.RuleTransition.rejected(
                        current, "actor_action_not_authorized");
            }
            return accepted(current, transitions.applyActorOwned(
                    current.match(), commands.orElseThrow()));
        }
        if (current.match().phase() == RiichiMatchPhase.BETWEEN_ROUNDS) {
            if (!action.type().equals("start_next_hand")
                    || action.payload().length != 0) {
                return top.ellan.mahjong.spi.RuleTransition.rejected(current, "invalid_action_payload");
            }
            if (!scheduledActions.mayStartNextRound(current, actor)) {
                return top.ellan.mahjong.spi.RuleTransition.rejected(current, "next_dealer_required");
            }
            RiichiMatchTransition domain = transitions.startNextRound(current.match());
            return accepted(current, domain);
        }
        if (current.match().phase() == RiichiMatchPhase.ENDED) {
            return top.ellan.mahjong.spi.RuleTransition.rejected(current, "match_ended");
        }
        RoundCommand command;
        try {
            command = RiichiSpiActions.decode(
                    current.match(), current.projectionIds(), domainActor, action);
        } catch (IllegalArgumentException invalid) {
            return top.ellan.mahjong.spi.RuleTransition.rejected(current, "invalid_action_payload");
        }
        RiichiMatchTransition domain = transitions.apply(current.match(), command);
        if (!domain.accepted()) {
            return top.ellan.mahjong.spi.RuleTransition.rejected(current, rejectionReason(domain));
        }
        return accepted(current, domain);
    }

    @Override
    public List<top.ellan.mahjong.spi.LegalAction> legalActions(
            top.ellan.mahjong.spi.RuleState state,
            top.ellan.mahjong.spi.PlayerId actor) {
        RiichiProviderState current = requireState(state);
        if (actor == null) {
            return List.of();
        }
        int initialSeat = current.initialSeat(actor);
        if (initialSeat < 0 || current.match().phase() == RiichiMatchPhase.ENDED) {
            return List.of();
        }
        PlayerId player = current.match().seats().get(initialSeat);
        if (current.match().phase() == RiichiMatchPhase.BETWEEN_ROUNDS) {
            return scheduledActions.mayStartNextRound(current, actor)
                    ? List.of(new top.ellan.mahjong.spi.LegalAction(
                            "start_next_hand",
                            new top.ellan.mahjong.spi.RuleAction("start_next_hand", new byte[0]),
                            top.ellan.mahjong.spi.ActionPresentation.actionRow(
                                    "action.start_next_hand")))
                    : List.of();
        }
        List<RoundCommand> commands = current.match().roundState().legalCommands(player);
        ArrayList<top.ellan.mahjong.spi.LegalAction> result = new ArrayList<>(commands.size());
        for (RoundCommand command : commands) {
            result.add(
                    RiichiSpiActions.encode(
                            current.match(), current.projectionIds(), player, command));
        }
        return List.copyOf(result);
    }

    @Override
    public java.util.Optional<top.ellan.mahjong.spi.ScheduledRuleAction> scheduledAction(
            top.ellan.mahjong.spi.RuleState state) {
        return scheduledActions.next(requireState(state));
    }

    @Override
    public java.util.Optional<top.ellan.mahjong.spi.ScheduledRuleAction> automatedAction(
            top.ellan.mahjong.spi.RuleState state,
            List<top.ellan.mahjong.spi.AutomatedPlayerActions> candidates) {
        return automation.next(requireState(state), List.copyOf(candidates));
    }

    @Override
    public java.util.Optional<top.ellan.mahjong.spi.RuleMatchResult> matchResult(
            top.ellan.mahjong.spi.RuleState state) {
        return RiichiMatchResults.from(requireState(state));
    }

    @Override
    public top.ellan.mahjong.spi.PublicRuleView publicView(
            top.ellan.mahjong.spi.RuleState state, long revision) {
        return projector.publicView(requireState(state), revision);
    }

    @Override
    public top.ellan.mahjong.spi.PrivateRuleView privateView(
            top.ellan.mahjong.spi.RuleState state,
            top.ellan.mahjong.spi.PlayerId viewer,
            long revision) {
        return projector.privateView(requireState(state), revision, viewer);
    }

    @Override
    public String stateHash(top.ellan.mahjong.spi.RuleState state) {
        return snapshots.snapshot(requireState(state), 0).sha256();
    }

    @Override
    public top.ellan.mahjong.spi.RuleStateSnapshot snapshot(
            top.ellan.mahjong.spi.RuleState state, long sequence) {
        return snapshots.snapshot(requireState(state), sequence);
    }

    @Override
    public top.ellan.mahjong.spi.RuleState restore(
            top.ellan.mahjong.spi.RuleStateSnapshot snapshot) {
        return snapshots.restore(snapshot);
    }

    private static top.ellan.mahjong.spi.RulePackDescriptor descriptorValue() {
        String schema = "{\"type\":\"object\",\"properties\":{},"
                + "\"additionalProperties\":false}";
        return new top.ellan.mahjong.spi.RulePackDescriptor(
                RULE_ID,
                PACK_VERSION,
                top.ellan.mahjong.spi.SpiVersion.CURRENT,
                ">=2.0.0",
                1,
                List.of(new top.ellan.mahjong.spi.RuleProfileDescriptor(
                        PROFILE_ID,
                        "Mahjong Soul Hanchan (four player)",
                        schema)),
                Set.of());
    }

    private static long deriveMatchSeed(top.ellan.mahjong.spi.MatchSeed seed) {
        long value = seed.high() ^ Long.rotateLeft(seed.low(), 31) ^ 0x5249_4348_5f4d_4154L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static top.ellan.mahjong.spi.RuleTransition accepted(
            RiichiProviderState current, RiichiMatchTransition domain) {
        List<top.ellan.mahjong.spi.RuleEvent> events =
                RiichiSpiEvents.encode(domain, current.match());
        if (events.isEmpty()) {
            throw new IllegalStateException("accepted Riichi transition emitted no canonical event");
        }
        RiichiProviderState next = current.withMatch(domain.state());
        return new top.ellan.mahjong.spi.RuleTransition(
                next,
                disposition(domain),
                events,
                RiichiPresentationCues.from(next, domain),
                "accepted");
    }

    private static top.ellan.mahjong.spi.TransitionDisposition disposition(
            RiichiMatchTransition transition) {
        for (RiichiMatchEvent event : transition.matchEvents()) {
            if (event.type() == RiichiMatchEvent.Type.MATCH_ENDED) {
                return top.ellan.mahjong.spi.TransitionDisposition.MATCH_ENDED;
            }
        }
        for (RiichiMatchEvent event : transition.matchEvents()) {
            if (event.type() == RiichiMatchEvent.Type.ROUND_ENDED) {
                return top.ellan.mahjong.spi.TransitionDisposition.ROUND_ENDED;
            }
        }
        return top.ellan.mahjong.spi.TransitionDisposition.ACCEPTED;
    }

    private static String rejectionReason(RiichiMatchTransition transition) {
        return transition.violation()
                .map(violation -> "rule." + violation.name().toLowerCase())
                .orElse("rule.rejected");
    }

    private static RiichiProviderState requireState(top.ellan.mahjong.spi.RuleState state) {
        if (!(state instanceof RiichiProviderState riichi)) {
            throw new IllegalArgumentException("state does not belong to the Riichi rule pack");
        }
        return riichi;
    }
}

package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.RiichiServices;
import top.ellan.mahjong.rules.riichi.evaluation.EvaluationException;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.TsumoPayment;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;
import top.ellan.mahjong.rules.riichi.scoring.YakuAward;
import top.ellan.mahjong.rules.riichi.settlement.AggregatedSettlement;
import top.ellan.mahjong.rules.riichi.settlement.PaoPaymentCalculator;
import top.ellan.mahjong.rules.riichi.settlement.PaymentTransfer;
import top.ellan.mahjong.rules.riichi.settlement.SettlementAggregator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Single-thread-confined, command-oriented round core. Unsupported ambiguous
 * flows return a typed failure and never mutate state.
 */
public final class RiichiRound {
    private final RiichiRules rules;
    private final List<PlayerId> seats;
    private final int dealerIndex;
    private final Wind roundWind;
    private final HandEvaluator handEvaluator;
    private final ScoreCalculator scoreCalculator;
    private final Map<PlayerId, PlayerState> players = new LinkedHashMap<>();
    private final Deque<TileInstance> liveWall;
    private final Deque<TileInstance> rinshan;
    private final List<TileInstance> doraIndicatorSequence;
    private final List<TileInstance> uraDoraIndicatorSequence;
    private final KanTracker kanTracker;
    private final Set<PlayerId> riichiPlayers = new LinkedHashSet<>();
    private final List<TileKind> firstDiscards = new ArrayList<>();
    private final Set<PlayerId> winners = new LinkedHashSet<>();
    private final Set<PlayerId> tenpaiPlayers = new LinkedHashSet<>();
    private final Set<PlayerId> nagashiWinners = new LinkedHashSet<>();

    private int currentPlayerIndex;
    private int honba;
    private int riichiSticks;
    private RoundPhase phase;
    private PendingReaction pendingReaction;
    private boolean firstTurnUninterrupted = true;
    private boolean anyCallMade;
    private boolean pendingFourKanAbort;
    private boolean pendingOpenKanDora;
    private int revealedDoraCount;
    private AbortiveDraw abortiveDraw;
    private String endReason;
    private AggregatedSettlement settlement;

    private RiichiRound(Scenario scenario, HandEvaluator evaluator, ScoreCalculator calculator) {
        rules = scenario.rules();
        seats = scenario.seatOrder();
        dealerIndex = scenario.dealerIndex();
        roundWind = scenario.roundWind();
        currentPlayerIndex = scenario.currentPlayerIndex();
        honba = scenario.honba();
        riichiSticks = scenario.riichiSticks();
        phase = scenario.phase();
        handEvaluator = Objects.requireNonNull(evaluator, "evaluator");
        scoreCalculator = Objects.requireNonNull(calculator, "calculator");
        liveWall = new ArrayDeque<>(scenario.liveWall());
        rinshan = new ArrayDeque<>(scenario.rinshan());
        doraIndicatorSequence = scenario.doraIndicatorSequence();
        uraDoraIndicatorSequence = scenario.uraDoraIndicatorSequence();
        revealedDoraCount = doraIndicatorSequence.isEmpty() ? 0 : 1;
        kanTracker = new KanTracker(scenario.kanCounts());
        for (PlayerId player : seats) {
            players.put(player, new PlayerState(
                    player,
                    scenario.scores().get(player),
                    scenario.hands().get(player),
                    scenario.melds().get(player)));
        }
        if (phase == RoundPhase.AWAITING_DISCARD) {
            PlayerState current = players.get(seats.get(currentPlayerIndex));
            if (!current.hand.isEmpty()) current.lastDrawn = current.hand.getLast().id();
        }
    }

    private RiichiRound(RiichiRound source) {
        rules = source.rules;
        seats = source.seats;
        dealerIndex = source.dealerIndex;
        roundWind = source.roundWind;
        currentPlayerIndex = source.currentPlayerIndex;
        honba = source.honba;
        riichiSticks = source.riichiSticks;
        phase = source.phase;
        handEvaluator = source.handEvaluator;
        scoreCalculator = source.scoreCalculator;
        for (Map.Entry<PlayerId, PlayerState> entry : source.players.entrySet()) {
            players.put(entry.getKey(), new PlayerState(entry.getValue()));
        }
        liveWall = new ArrayDeque<>(source.liveWall);
        rinshan = new ArrayDeque<>(source.rinshan);
        doraIndicatorSequence = source.doraIndicatorSequence;
        uraDoraIndicatorSequence = source.uraDoraIndicatorSequence;
        kanTracker = new KanTracker(source.kanTracker.byPlayer());
        riichiPlayers.addAll(source.riichiPlayers);
        firstDiscards.addAll(source.firstDiscards);
        winners.addAll(source.winners);
        tenpaiPlayers.addAll(source.tenpaiPlayers);
        nagashiWinners.addAll(source.nagashiWinners);
        pendingReaction = source.pendingReaction == null ? null : new PendingReaction(
                source.pendingReaction.source,
                source.pendingReaction.tile,
                source.pendingReaction.window.copy(),
                source.pendingReaction.ronScores,
                source.pendingReaction.riichiDeclaration,
                source.pendingReaction.pendingKan);
        firstTurnUninterrupted = source.firstTurnUninterrupted;
        anyCallMade = source.anyCallMade;
        pendingFourKanAbort = source.pendingFourKanAbort;
        pendingOpenKanDora = source.pendingOpenKanDora;
        revealedDoraCount = source.revealedDoraCount;
        abortiveDraw = source.abortiveDraw;
        endReason = source.endReason;
        settlement = source.settlement;
    }

    public static RiichiRound fromScenario(Scenario scenario) {
        return new RiichiRound(scenario, RiichiServices.handEvaluator(), RiichiServices.scoreCalculator());
    }

    public static RiichiRound fromScenario(
            Scenario scenario,
            HandEvaluator evaluator,
            ScoreCalculator calculator) {
        return new RiichiRound(scenario, evaluator, calculator);
    }

    RiichiRound copy() {
        return new RiichiRound(this);
    }

    public CommandResult apply(RoundCommand command) {
        Objects.requireNonNull(command, "command");
        if (phase == RoundPhase.ENDED) {
            return rejected(RuleViolation.ILLEGAL_PHASE, "round has ended");
        }
        ArrayList<RoundEvent> events = new ArrayList<>();
        try {
            if (command instanceof RoundCommand.Draw draw) {
                draw(draw.player(), events);
            } else if (command instanceof RoundCommand.Discard discard) {
                discard(discard.player(), discard.tile(), discard.declareRiichi(), events);
            } else if (command instanceof RoundCommand.Respond respond) {
                respond(respond.player(), respond.reaction(), events);
            } else if (command instanceof RoundCommand.DeclareSelfKan kan) {
                declareSelfKan(kan.player(), kan.kind(), events);
            } else if (command instanceof RoundCommand.DeclareNineTerminals abort) {
                declareNineTerminals(abort.player(), events);
            } else if (command instanceof RoundCommand.DeclareTsumo tsumo) {
                declareTsumo(tsumo.player(), events);
            } else {
                throw new RuleViolationException(RuleViolation.UNSUPPORTED_RULE_FLOW, "unknown command type");
            }
            return new CommandResult(true, Optional.empty(), "accepted", events, snapshot());
        } catch (EvaluationException error) {
            return rejected(RuleViolation.EVALUATION_FAILED, error.getMessage());
        } catch (RuleViolationException error) {
            return rejected(error.violation(), error.getMessage());
        }
    }

    public RoundSnapshot snapshot() {
        LinkedHashMap<PlayerId, Integer> scores = new LinkedHashMap<>();
        LinkedHashMap<PlayerId, Integer> handSizes = new LinkedHashMap<>();
        LinkedHashMap<PlayerId, List<TileInstance>> discards = new LinkedHashMap<>();
        LinkedHashMap<PlayerId, List<Meld>> melds = new LinkedHashMap<>();
        LinkedHashMap<PlayerId, Boolean> riichi = new LinkedHashMap<>();
        players.forEach((player, state) -> {
            scores.put(player, state.score);
            handSizes.put(player, state.hand.size());
            discards.put(player, List.copyOf(state.discards));
            melds.put(player, List.copyOf(state.melds));
            riichi.put(player, state.riichi || state.doubleRiichi);
        });
        return new RoundSnapshot(
                phase,
                currentPlayer(),
                scores,
                handSizes,
                discards,
                melds,
                riichi,
                liveWall.size(),
                rinshan.size(),
                kanTracker.total(),
                revealedDoraCount,
                honba,
                riichiSticks,
                winners,
                tenpaiPlayers,
                nagashiWinners,
                Optional.ofNullable(abortiveDraw),
                Optional.ofNullable(endReason),
                Optional.ofNullable(settlement));
    }

    /** Private player view; the integration layer is responsible for authorization. */
    public List<TileInstance> concealedHand(PlayerId player) {
        return List.copyOf(player(player).hand);
    }

    public Optional<ReactionOptions> availableReactions(PlayerId player) {
        return pendingReaction == null
                ? Optional.empty()
                : Optional.ofNullable(pendingReaction.window.options().get(player));
    }

    private void draw(PlayerId player, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_DRAW);
        requireCurrent(player);
        if (liveWall.isEmpty()) {
            settleExhaustiveDraw(events);
            return;
        }
        PlayerState state = player(player);
        TileInstance tile = liveWall.removeFirst();
        state.hand.add(tile);
        state.lastDrawn = tile.id();
        state.lastDrawWasRinshan = false;
        state.furiten.onOwnDraw();
        phase = RoundPhase.AWAITING_DISCARD;
        events.add(RoundEvent.of(RoundEvent.Type.DRAWN, player, tile, "live-wall draw"));
    }

    private void discard(PlayerId playerId, TileId tileId, boolean declareRiichi, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_DISCARD);
        requireCurrent(playerId);
        PlayerState discarder = player(playerId);
        TileInstance discarded = discarder.find(tileId).orElseThrow(() ->
                new RuleViolationException(RuleViolation.TILE_NOT_IN_HAND, "tile is not in hand"));
        if ((discarder.riichi || discarder.doubleRiichi)
                && discarder.lastDrawn != null
                && !discarder.lastDrawn.equals(tileId)) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "riichi player must discard the drawn tile");
        }
        if (discarder.kuikaeForbidden.contains(discarded.tile().kind())) {
            throw new RuleViolationException(
                    RuleViolation.KUIKAE_FORBIDDEN, "tile is forbidden by the immediately preceding call");
        }

        List<TileInstance> handAfterDiscard = new ArrayList<>(discarder.hand);
        handAfterDiscard.remove(discarded);
        boolean doubleRiichi = false;
        if (declareRiichi) {
            validateRiichi(discarder, handAfterDiscard);
            doubleRiichi = firstTurnUninterrupted && discarder.discards.isEmpty();
        }

        ReactionPlan plan = reactionPlan(playerId, discarded);

        if (pendingOpenKanDora) {
            revealNextDora();
            pendingOpenKanDora = false;
        }

        discarder.hand.remove(discarded);
        discarder.discards.add(discarded);
        discarder.furiten.recordOwnDiscard(discarded.tile().kind());
        discarder.lastDrawn = null;
        discarder.lastDrawWasRinshan = false;
        discarder.kuikaeForbidden = Set.of();
        if (declareRiichi) {
            discarder.score -= 1_000;
            riichiSticks++;
            discarder.doubleRiichi = doubleRiichi;
            discarder.riichi = !doubleRiichi;
            discarder.ippatsu = true;
            riichiPlayers.add(playerId);
            events.add(RoundEvent.of(
                    RoundEvent.Type.RIICHI_DECLARED, playerId, discarded,
                    doubleRiichi ? "double riichi" : "riichi"));
        } else if (discarder.ippatsu) {
            discarder.ippatsu = false;
        }
        if (discarder.discards.size() == 1) firstDiscards.add(discarded.tile().kind());
        events.add(RoundEvent.of(RoundEvent.Type.DISCARDED, playerId, discarded, "discard"));

        if (plan.options.isEmpty()) {
            afterUnclaimedDiscard(playerId, events);
            return;
        }
        ReactionWindow window = new ReactionWindow(
                priorityAfter(playerId), plan.options, rules.ronMode(), kanTracker);
        pendingReaction = new PendingReaction(
                playerId, discarded, window, plan.ronScores, declareRiichi, Optional.empty());
        phase = RoundPhase.AWAITING_REACTIONS;
        events.add(RoundEvent.of(RoundEvent.Type.REACTION_OPENED, playerId, discarded, "discard reactions"));
    }

    private void respond(PlayerId playerId, Reaction reaction, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_REACTIONS);
        PendingReaction pending = Objects.requireNonNull(pendingReaction, "pendingReaction");
        validateReactionTiles(playerId, reaction, pending.tile);
        ReactionOptions options = pending.window.options().get(playerId);
        PlayerState responder = player(playerId);
        ReactionWindow windowCheckpoint = pending.window.copy();
        FuritenState.Snapshot furitenCheckpoint = responder.furiten.snapshot();
        try {
            ReactionWindow.SubmitStatus status = pending.window.submit(playerId, reaction);
            if (reaction.type() != ReactionType.RON && options != null && options.ron()) {
                responder.furiten.missLegalRon(responder.riichi || responder.doubleRiichi);
            }
            events.add(RoundEvent.of(
                    RoundEvent.Type.REACTION_SUBMITTED, playerId, pending.tile, reaction.type().name()));
            if (status == ReactionWindow.SubmitStatus.RESOLVED) {
                resolveReactions(pending, pending.window.resolution().orElseThrow(), events);
            }
        } catch (EvaluationException | RuleViolationException error) {
            responder.furiten.restore(furitenCheckpoint);
            pendingReaction = new PendingReaction(
                    pending.source,
                    pending.tile,
                    windowCheckpoint,
                    pending.ronScores,
                    pending.riichiDeclaration,
                    pending.pendingKan);
            throw error;
        }
    }

    private void resolveReactions(
            PendingReaction pending,
            ReactionResolution resolution,
            List<RoundEvent> events) {
        if (!resolution.ronWinners().isEmpty()) {
            if (resolution.ronWinners().size() == 3) {
                pendingReaction = null;
                end("TRIPLE_RON", AbortiveDraw.TRIPLE_RON, null, events);
                return;
            }
            resolveRon(pending, resolution.ronWinners(), events);
            return;
        }
        if (resolution.caller().isEmpty()) {
            pendingReaction = null;
            if (pending.pendingKan.isPresent()) {
                completeSelfKan(pending.source, pending.pendingKan.orElseThrow(), events);
            } else {
                afterUnclaimedDiscard(pending.source, events);
            }
            return;
        }
        PlayerId callerId = resolution.caller().orElseThrow();
        Reaction reaction = resolution.call().orElseThrow();
        PlayerState caller = player(callerId);
        List<TileInstance> consumed = select(caller, reaction.consumedTiles());
        ArrayList<TileInstance> meldTiles = new ArrayList<>(consumed);
        meldTiles.add(pending.tile);
        MeldType type = switch (reaction.type()) {
            case CHII -> MeldType.CHII;
            case PON -> MeldType.PON;
            case MINKAN -> MeldType.MINKAN;
            case RON, SKIP -> throw new IllegalStateException("not a meld reaction");
        };
        Meld meld = new Meld(
                type,
                meldTiles,
                Optional.of(pending.source),
                Optional.of(pending.tile.id()));
        caller.hand.removeAll(consumed);
        caller.melds.add(meld);
        player(pending.source).calledDiscards.add(pending.tile.id());
        if (type == MeldType.PON || type == MeldType.MINKAN) {
            registerPaoLiability(caller, pending.source);
        }
        caller.kuikaeForbidden = switch (type) {
            case PON -> Set.of(pending.tile.tile().kind());
            case CHII -> kuikaeForbiddenAfterChii(pending.tile.tile().kind(), consumed);
            case MINKAN -> Set.of();
            case ANKAN, KAKAN -> throw new IllegalStateException("not a discard reaction");
        };
        pendingReaction = null;
        anyCallMade = true;
        firstTurnUninterrupted = false;
        cancelIppatsu();
        currentPlayerIndex = seats.indexOf(callerId);
        events.add(RoundEvent.of(RoundEvent.Type.MELD_DECLARED, callerId, pending.tile, type.name()));
        if (type == MeldType.MINKAN) {
            KanTracker.Registration registration = kanTracker.registerDiscardKan(callerId);
            pendingFourKanAbort |= registration == KanTracker.Registration.ABORT_AFTER_DISCARD;
            if (KanDoraPolicy.revealBeforeRinshan(rules.profile(), type)) {
                revealNextDora();
            } else {
                pendingOpenKanDora = true;
            }
            events.add(RoundEvent.of(RoundEvent.Type.KAN_REGISTERED, callerId, pending.tile, doraTiming(type)));
            drawRinshan(callerId, events);
        } else {
            phase = RoundPhase.AWAITING_DISCARD;
        }
    }

    private void declareSelfKan(PlayerId playerId, TileKind kind, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_DISCARD);
        requireCurrent(playerId);
        if (!kanTracker.canDeclareSelfKan()) {
            throw new RuleViolationException(RuleViolation.FIFTH_KAN_FORBIDDEN, "a fifth kan is forbidden");
        }
        if (!player(playerId).kuikaeForbidden.isEmpty()) {
            throw new RuleViolationException(
                    RuleViolation.KUIKAE_FORBIDDEN, "a self-kan cannot replace the mandatory post-call discard");
        }
        if (rinshan.isEmpty()) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "no rinshan tile remains");
        }
        if (liveWall.isEmpty()) {
            throw new RuleViolationException(
                    RuleViolation.ILLEGAL_PHASE, "a kan is forbidden after the last live-wall draw");
        }
        PlayerState state = player(playerId);
        List<TileInstance> matching = state.hand.stream()
                .filter(tile -> tile.tile().kind() == kind)
                .toList();

        PendingKan pendingKan;
        TileInstance robbableTile;
        boolean kokushiOnly;
        if (matching.size() == 4) {
            Meld ankan = new Meld(MeldType.ANKAN, matching, Optional.empty(), Optional.empty());
            if (state.riichi || state.doubleRiichi) {
                validateRiichiAnkan(state, kind, matching, ankan);
            }
            robbableTile = matching.stream()
                    .filter(tile -> tile.id().equals(state.lastDrawn))
                    .findFirst()
                    .orElse(matching.getFirst());
            pendingKan = new PendingKan(MeldType.ANKAN, ankan, -1, matching);
            kokushiOnly = true;
        } else {
            if (state.riichi || state.doubleRiichi) {
                throw new RuleViolationException(
                        RuleViolation.ILLEGAL_PHASE, "a riichi player cannot declare kakan");
            }
            int ponIndex = findPonIndex(state, kind);
            if (matching.size() != 1 || ponIndex < 0) {
                throw new RuleViolationException(
                        RuleViolation.ILLEGAL_PHASE, "self-kan requires four concealed tiles or a pon plus its fourth tile");
            }
            Meld pon = state.melds.get(ponIndex);
            robbableTile = matching.getFirst();
            ArrayList<TileInstance> completedTiles = new ArrayList<>(pon.tiles());
            completedTiles.add(robbableTile);
            Meld kakan = new Meld(
                    MeldType.KAKAN,
                    completedTiles,
                    pon.claimedFrom(),
                    pon.claimedTile());
            pendingKan = new PendingKan(MeldType.KAKAN, kakan, ponIndex, List.of(robbableTile));
            kokushiOnly = false;
        }

        events.add(RoundEvent.of(
                RoundEvent.Type.KAN_DECLARED, playerId, robbableTile, pendingKan.type.name()));
        ReactionPlan robberyPlan = kanRobberyPlan(playerId, robbableTile, kokushiOnly);
        if (robberyPlan.options.isEmpty()) {
            completeSelfKan(playerId, pendingKan, events);
            return;
        }

        ReactionWindow window = new ReactionWindow(
                priorityAfter(playerId), robberyPlan.options, rules.ronMode(), kanTracker);
        pendingReaction = new PendingReaction(
                playerId,
                robbableTile,
                window,
                robberyPlan.ronScores,
                false,
                Optional.of(pendingKan));
        phase = RoundPhase.AWAITING_REACTIONS;
        events.add(RoundEvent.of(
                RoundEvent.Type.REACTION_OPENED, playerId, robbableTile, "kan robbery reactions"));
    }

    private void validateRiichiAnkan(
            PlayerState state,
            TileKind kind,
            List<TileInstance> matching,
            Meld ankan) {
        TileInstance drawn = state.lastDrawn == null ? null : state.find(state.lastDrawn).orElse(null);
        if (drawn == null || drawn.tile().kind() != kind) {
            throw new RuleViolationException(
                    RuleViolation.ILLEGAL_PHASE,
                    "riichi ankan must use the tile drawn on this turn");
        }

        ArrayList<TileInstance> beforeKan = new ArrayList<>(state.hand);
        beforeKan.remove(drawn);
        HandAnalysis before = handEvaluator.analyze(logical(beforeKan), state.melds);

        ArrayList<TileInstance> afterKan = new ArrayList<>(state.hand);
        afterKan.removeAll(matching);
        ArrayList<Meld> meldsAfter = new ArrayList<>(state.melds);
        meldsAfter.add(ankan);
        HandAnalysis after = handEvaluator.analyze(logical(afterKan), meldsAfter);
        if (!before.tenpai() || !after.tenpai() || !before.waits().equals(after.waits())) {
            throw new RuleViolationException(
                    RuleViolation.RIICHI_KAN_CHANGES_WAIT,
                    "riichi ankan must preserve the exact winning-tile set");
        }
    }

    private int findPonIndex(PlayerState state, TileKind kind) {
        for (int index = 0; index < state.melds.size(); index++) {
            Meld meld = state.melds.get(index);
            if (meld.type() == MeldType.PON && meld.tiles().getFirst().tile().kind() == kind) {
                return index;
            }
        }
        return -1;
    }

    private void completeSelfKan(PlayerId playerId, PendingKan pendingKan, List<RoundEvent> events) {
        PlayerState state = player(playerId);
        if (!state.hand.containsAll(pendingKan.handTiles)) {
            throw new RuleViolationException(
                    RuleViolation.TILE_NOT_IN_HAND, "self-kan tile is no longer in hand");
        }
        if (pendingKan.type == MeldType.KAKAN) {
            if (pendingKan.replacedMeldIndex >= state.melds.size()
                    || state.melds.get(pendingKan.replacedMeldIndex).type() != MeldType.PON) {
                throw new RuleViolationException(
                        RuleViolation.ILLEGAL_PHASE, "the pon upgraded by kakan is no longer present");
            }
        }

        KanTracker.Registration registration = kanTracker.registerSelfKan(playerId);
        pendingFourKanAbort |= registration == KanTracker.Registration.ABORT_AFTER_DISCARD;
        state.hand.removeAll(pendingKan.handTiles);
        if (pendingKan.type == MeldType.KAKAN) {
            state.melds.set(pendingKan.replacedMeldIndex, pendingKan.completedMeld);
        } else {
            state.melds.add(pendingKan.completedMeld);
        }
        if (KanDoraPolicy.revealBeforeRinshan(rules.profile(), pendingKan.type)) {
            revealNextDora();
        } else {
            pendingOpenKanDora = true;
        }
        firstTurnUninterrupted = false;
        anyCallMade = true;
        cancelIppatsu();
        events.add(RoundEvent.of(
                RoundEvent.Type.KAN_REGISTERED,
                playerId,
                pendingKan.handTiles.getFirst(),
                doraTiming(pendingKan.type)));
        drawRinshan(playerId, events);
    }

    private void declareNineTerminals(PlayerId playerId, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_DISCARD);
        requireCurrent(playerId);
        PlayerState state = player(playerId);
        if (!state.discards.isEmpty()
                || !AbortiveDrawRules.canDeclareNineTerminals(firstTurnUninterrupted, state.hand)) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "nine-terminals abort is not available");
        }
        end("NINE_TERMINALS", AbortiveDraw.NINE_TERMINALS, null, events);
    }

    private void declareTsumo(PlayerId playerId, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_DISCARD);
        requireCurrent(playerId);
        PlayerState winner = player(playerId);
        if (winner.lastDrawn == null) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "tsumo requires a drawn tile");
        }
        TileInstance winning = winner.find(winner.lastDrawn).orElseThrow();
        ScoreResult score = score(winner, winning.tile(), true, WinMethod.TSUMO, false);
        requireLegalWin(score);
        TsumoPayment payment = score.tsumoPayment().orElseThrow();
        boolean winnerDealer = seatWind(playerId) == Wind.EAST;
        List<PlayerId> opponents = seats.stream()
                .filter(opponent -> !opponent.equals(playerId))
                .toList();
        ArrayList<PaymentTransfer> transfers;
        Optional<PaoAward> pao = paoAward(winner, score);
        if (pao.isPresent()) {
            PaoAward award = pao.orElseThrow();
            transfers = new ArrayList<>(PaoPaymentCalculator.tsumo(
                    playerId,
                    award.liable,
                    seats.get(dealerIndex),
                    opponents,
                    winnerDealer,
                    score.yakumanMultiplier(),
                    award.multiplier,
                    honba));
        } else {
            LinkedHashMap<PlayerId, Integer> payers = new LinkedHashMap<>();
            for (PlayerId opponent : opponents) {
                int amount = winnerDealer || seatWind(opponent) != Wind.EAST
                        ? payment.nonDealerPaysEach()
                        : payment.dealerPays();
                payers.put(opponent, amount);
            }
            transfers = new ArrayList<>(SettlementAggregator.tsumo(playerId, payers, honba));
        }
        if (riichiSticks > 0) transfers.add(SettlementAggregator.riichiPool(playerId, riichiSticks));
        finishSettlement("TSUMO", transfers, events);
        winners.add(playerId);
    }

    private void resolveRon(PendingReaction pending, List<PlayerId> winners, List<RoundEvent> events) {
        boolean refundDeclaration = pending.riichiDeclaration;
        int payableRiichiSticks = refundDeclaration ? riichiSticks - 1 : riichiSticks;
        if (payableRiichiSticks < 0) {
            throw new RuleViolationException(
                    RuleViolation.UNSUPPORTED_RULE_FLOW, "riichi declaration refund has no matching stick");
        }
        ArrayList<PaymentTransfer> transfers = new ArrayList<>();
        if (refundDeclaration) {
            transfers.add(SettlementAggregator.riichiRefund(pending.source));
        }
        for (int index = 0; index < winners.size(); index++) {
            PlayerId winner = winners.get(index);
            ScoreResult score = pending.ronScores.get(winner);
            requireLegalWin(score);
            Optional<PaoAward> pao = paoAward(player(winner), score);
            if (pao.isPresent()) {
                PaoAward award = pao.orElseThrow();
                transfers.addAll(PaoPaymentCalculator.ron(
                        winner,
                        pending.source,
                        award.liable,
                        seatWind(winner) == Wind.EAST,
                        score.yakumanMultiplier(),
                        award.multiplier,
                        index == 0 ? honba : 0));
            } else {
                transfers.addAll(SettlementAggregator.ron(
                        winner,
                        pending.source,
                        score.ronPayment().orElseThrow().discarderPays(),
                        honba,
                        index == 0));
            }
        }
        if (payableRiichiSticks > 0) {
            transfers.add(SettlementAggregator.riichiPool(winners.getFirst(), payableRiichiSticks));
        }
        finishSettlement(pending.pendingKan.isPresent() ? "CHANKAN" : "RON", transfers, events);
        this.winners.addAll(winners);
        if (pending.pendingKan.isPresent()) {
            player(pending.source).hand.remove(pending.tile);
        }
        pendingReaction = null;
        if (refundDeclaration) {
            PlayerState declarer = player(pending.source);
            declarer.riichi = false;
            declarer.doubleRiichi = false;
            declarer.ippatsu = false;
            riichiPlayers.remove(pending.source);
        }
    }

    private ReactionPlan reactionPlan(PlayerId discarder, TileInstance tile) {
        LinkedHashMap<PlayerId, ReactionOptions> options = new LinkedHashMap<>();
        LinkedHashMap<PlayerId, ScoreResult> ronScores = new LinkedHashMap<>();
        List<PlayerId> priority = priorityAfter(discarder);
        PlayerId leftPlayer = priority.getFirst();
        for (PlayerId candidateId : priority) {
            PlayerState candidate = player(candidateId);
            HandAnalysis analysis = handEvaluator.analyze(logical(candidate.hand), candidate.melds);
            boolean furiten = candidate.furiten.isFuriten(analysis.waits());
            boolean canRon = false;
            if (analysis.waits().contains(tile.tile().kind()) && !furiten) {
                ScoreResult score = score(candidate, tile.tile(), false, WinMethod.RON, false);
                canRon = score.completeHand() && score.hasYaku() && score.eligibleByMinimumHan();
                if (canRon) ronScores.put(candidateId, score);
            }
            boolean locked = candidate.riichi || candidate.doubleRiichi;
            int copies = count(candidate.hand, tile.tile().kind());
            boolean callsAllowed = !liveWall.isEmpty();
            boolean canPon = callsAllowed && !locked && copies >= 2;
            boolean canMinkan = callsAllowed && !locked && copies >= 3
                    && !rinshan.isEmpty() && kanTracker.canOfferDiscardKan();
            List<List<TileId>> chii = callsAllowed && !locked && candidateId.equals(leftPlayer)
                    ? chiiChoices(candidate.hand, tile.tile().kind()) : List.of();
            ReactionOptions candidateOptions = new ReactionOptions(canRon, canPon, canMinkan, chii);
            if (!candidateOptions.empty()) options.put(candidateId, candidateOptions);
        }
        return new ReactionPlan(Map.copyOf(options), Map.copyOf(ronScores));
    }

    private ScoreResult score(
            PlayerState player,
            Tile winningTile,
            boolean winningInHand,
            WinMethod method,
            boolean chankan) {
        return scoreCalculator.score(new ScoreRequest(
                logical(player.hand),
                player.melds,
                winningTile,
                winningInHand,
                method,
                seatWind(playerId(player)),
                roundWind,
                player.riichi,
                player.doubleRiichi,
                player.ippatsu,
                chankan,
                method == WinMethod.TSUMO && player.lastDrawWasRinshan,
                liveWall.isEmpty(),
                firstTurnUninterrupted && player.discards.isEmpty() && method == WinMethod.TSUMO,
                visibleDoraIndicators(),
                visibleUraDoraIndicators(),
                rules));
    }

    private void registerPaoLiability(PlayerState caller, PlayerId feeder) {
        Set<TileKind> openDragons = new HashSet<>();
        Set<TileKind> openWinds = new HashSet<>();
        for (Meld meld : caller.melds) {
            if (!meld.open() || meld.type() == MeldType.CHII) continue;
            TileKind kind = meld.tiles().getFirst().tile().kind();
            if (kind.isDragon()) openDragons.add(kind);
            if (kind.isWind()) openWinds.add(kind);
        }
        if (openDragons.size() == 3) {
            caller.paoLiabilities.putIfAbsent(PaoKind.DAISANGEN, feeder);
        }
        if (openWinds.size() == 4) {
            caller.paoLiabilities.putIfAbsent(PaoKind.DAISUUSHII, feeder);
        }
    }

    private Optional<PaoAward> paoAward(PlayerState winner, ScoreResult score) {
        for (YakuAward yaku : score.yaku()) {
            PaoKind kind = switch (yaku.id()) {
                case "DAISANGEN" -> PaoKind.DAISANGEN;
                case "DAISUUSHII" -> PaoKind.DAISUUSHII;
                default -> null;
            };
            if (kind == null || yaku.yakumanMultiplier() == 0) continue;
            PlayerId liable = winner.paoLiabilities.get(kind);
            if (liable != null) {
                return Optional.of(new PaoAward(liable, yaku.yakumanMultiplier()));
            }
        }
        return Optional.empty();
    }

    private ReactionPlan kanRobberyPlan(
            PlayerId declarer,
            TileInstance tile,
            boolean kokushiOnly) {
        LinkedHashMap<PlayerId, ReactionOptions> options = new LinkedHashMap<>();
        LinkedHashMap<PlayerId, ScoreResult> ronScores = new LinkedHashMap<>();
        for (PlayerId candidateId : priorityAfter(declarer)) {
            PlayerState candidate = player(candidateId);
            HandAnalysis analysis = handEvaluator.analyze(logical(candidate.hand), candidate.melds);
            if (!analysis.waits().contains(tile.tile().kind())
                    || candidate.furiten.isFuriten(analysis.waits())) {
                continue;
            }
            ScoreResult result = score(candidate, tile.tile(), false, WinMethod.RON, true);
            boolean legal = result.completeHand() && result.hasYaku() && result.eligibleByMinimumHan();
            boolean kokushi = result.yaku().stream()
                    .anyMatch(yaku -> yaku.id().startsWith("KOKUSHIMUSO"));
            if (!legal || (kokushiOnly && !kokushi)) continue;
            options.put(candidateId, new ReactionOptions(true, false, false, List.of()));
            ronScores.put(candidateId, result);
        }
        return new ReactionPlan(Map.copyOf(options), Map.copyOf(ronScores));
    }

    private void validateRiichi(PlayerState player, List<TileInstance> handAfterDiscard) {
        if (player.riichi || player.doubleRiichi) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "player is already in riichi");
        }
        if (player.melds.stream().anyMatch(Meld::open)) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "open hand cannot declare riichi");
        }
        if (player.score < 1_000) {
            throw new RuleViolationException(RuleViolation.INSUFFICIENT_POINTS, "riichi requires 1000 points");
        }
        if (liveWall.size() < 4) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "riichi is unavailable with fewer than four draws");
        }
        if (!handEvaluator.analyze(logical(handAfterDiscard), player.melds).tenpai()) {
            throw new RuleViolationException(RuleViolation.NOT_TENPAI, "discard does not leave a tenpai hand");
        }
    }

    private void validateReactionTiles(PlayerId playerId, Reaction reaction, TileInstance claimed) {
        if (reaction.type() == ReactionType.RON || reaction.type() == ReactionType.SKIP) return;
        PlayerState player = player(playerId);
        List<TileInstance> selected = reaction.consumedTiles().stream()
                .map(id -> player.find(id).orElseThrow(() ->
                        new RuleViolationException(RuleViolation.INVALID_REACTION_PAYLOAD, "reaction tile is not in hand")))
                .toList();
        if (reaction.type() == ReactionType.PON || reaction.type() == ReactionType.MINKAN) {
            if (selected.stream().anyMatch(tile -> tile.tile().kind() != claimed.tile().kind())) {
                throw new RuleViolationException(RuleViolation.INVALID_REACTION_PAYLOAD, "pon/kan tile kind mismatch");
            }
        }
    }

    private void settleExhaustiveDraw(List<RoundEvent> events) {
        tenpaiPlayers.clear();
        nagashiWinners.clear();
        for (PlayerId playerId : seats) {
            PlayerState state = player(playerId);
            if (handEvaluator.analyze(logical(state.hand), state.melds).tenpai()) {
                tenpaiPlayers.add(playerId);
            }
            boolean nagashi = !state.discards.isEmpty()
                    && state.calledDiscards.isEmpty()
                    && state.discards.stream()
                            .allMatch(tile -> tile.tile().kind().isTerminalOrHonor());
            if (nagashi) nagashiWinners.add(playerId);
        }

        ArrayList<PaymentTransfer> transfers = new ArrayList<>();
        String reason;
        if (nagashiWinners.isEmpty()) {
            List<PlayerId> notenPlayers = seats.stream()
                    .filter(player -> !tenpaiPlayers.contains(player))
                    .toList();
            transfers.addAll(SettlementAggregator.noten(tenpaiPlayers, notenPlayers));
            reason = "EXHAUSTIVE_DRAW";
        } else {
            for (PlayerId winner : nagashiWinners) {
                boolean dealer = seatWind(winner) == Wind.EAST;
                LinkedHashMap<PlayerId, Integer> payerAmounts = new LinkedHashMap<>();
                for (PlayerId opponent : seats) {
                    if (opponent.equals(winner)) continue;
                    int amount = dealer || seatWind(opponent) == Wind.EAST ? 4_000 : 2_000;
                    payerAmounts.put(opponent, amount);
                }
                transfers.addAll(SettlementAggregator.nagashiMangan(winner, payerAmounts));
            }
            reason = "NAGASHI_MANGAN";
        }
        finishSettlement(reason, transfers, false, events);
    }

    private void afterUnclaimedDiscard(PlayerId discarder, List<RoundEvent> events) {
        if (pendingFourKanAbort) {
            end("FOUR_KANS", AbortiveDraw.FOUR_KANS, null, events);
            return;
        }
        if (AbortiveDrawRules.isFourWinds(firstDiscards, anyCallMade)) {
            end("FOUR_WINDS", AbortiveDraw.FOUR_WINDS, null, events);
            return;
        }
        if (AbortiveDrawRules.isFourRiichi(riichiPlayers)) {
            end("FOUR_RIICHI", AbortiveDraw.FOUR_RIICHI, null, events);
            return;
        }
        if (liveWall.isEmpty()) {
            settleExhaustiveDraw(events);
            return;
        }
        currentPlayerIndex = (seats.indexOf(discarder) + 1) % seats.size();
        phase = RoundPhase.AWAITING_DRAW;
    }

    private void drawRinshan(PlayerId playerId, List<RoundEvent> events) {
        if (rinshan.isEmpty()) {
            throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "no rinshan tile remains");
        }
        PlayerState player = player(playerId);
        TileInstance tile = rinshan.removeFirst();
        player.hand.add(tile);
        player.lastDrawn = tile.id();
        player.lastDrawWasRinshan = true;
        player.furiten.onOwnDraw();
        phase = RoundPhase.AWAITING_DISCARD;
        events.add(RoundEvent.of(RoundEvent.Type.RINSHAN_DRAWN, playerId, tile, "rinshan draw"));
    }

    private void finishSettlement(String reason, List<PaymentTransfer> transfers, List<RoundEvent> events) {
        finishSettlement(reason, transfers, true, events);
    }

    private void finishSettlement(
            String reason,
            List<PaymentTransfer> transfers,
            boolean consumeRiichiPool,
            List<RoundEvent> events) {
        AggregatedSettlement prepared;
        LinkedHashMap<PlayerId, Integer> resultingScores = new LinkedHashMap<>();
        try {
            prepared = SettlementAggregator.aggregate(transfers);
            for (Map.Entry<PlayerId, Integer> delta : prepared.playerDeltas().entrySet()) {
                PlayerState state = players.get(delta.getKey());
                if (state == null) {
                    throw new ArithmeticException("settlement references a player outside this round");
                }
                resultingScores.put(delta.getKey(), Math.addExact(state.score, delta.getValue()));
            }
        } catch (ArithmeticException | IllegalArgumentException error) {
            throw new RuleViolationException(
                    RuleViolation.UNSUPPORTED_RULE_FLOW, "settlement arithmetic failed: " + error.getMessage());
        }
        resultingScores.forEach((player, score) -> players.get(player).score = score);
        settlement = prepared;
        if (consumeRiichiPool) riichiSticks = 0;
        end(reason, null, settlement, events);
    }

    private void end(
            String reason,
            AbortiveDraw draw,
            AggregatedSettlement finalSettlement,
            List<RoundEvent> events) {
        phase = RoundPhase.ENDED;
        endReason = reason;
        abortiveDraw = draw;
        if (finalSettlement != null) settlement = finalSettlement;
        events.add(RoundEvent.of(RoundEvent.Type.ROUND_ENDED, null, null, reason));
    }

    private void requireLegalWin(ScoreResult score) {
        if (!score.completeHand()) throw new RuleViolationException(RuleViolation.ILLEGAL_PHASE, "hand is not complete");
        if (!score.hasYaku()) throw new RuleViolationException(RuleViolation.NO_YAKU, "hand has no yaku");
        if (!score.eligibleByMinimumHan()) {
            throw new RuleViolationException(RuleViolation.MINIMUM_HAN_NOT_MET, "minimum yaku han is not met");
        }
    }

    private void requirePhase(RoundPhase expected) {
        if (phase != expected) {
            throw new RuleViolationException(
                    RuleViolation.ILLEGAL_PHASE, "expected " + expected + " but was " + phase);
        }
    }

    private void requireCurrent(PlayerId player) {
        if (!players.containsKey(player)) {
            throw new RuleViolationException(RuleViolation.UNKNOWN_PLAYER, "unknown player: " + player);
        }
        if (!currentPlayer().equals(player)) {
            throw new RuleViolationException(RuleViolation.NOT_YOUR_TURN, "not the current player");
        }
    }

    private PlayerId currentPlayer() {
        return seats.get(currentPlayerIndex);
    }

    private PlayerState player(PlayerId id) {
        PlayerState state = players.get(id);
        if (state == null) {
            throw new RuleViolationException(RuleViolation.UNKNOWN_PLAYER, "unknown player: " + id);
        }
        return state;
    }

    private PlayerId playerId(PlayerState target) {
        return target.id;
    }

    private Wind seatWind(PlayerId player) {
        int relative = (seats.indexOf(player) - dealerIndex + seats.size()) % seats.size();
        return Wind.values()[relative];
    }

    private List<PlayerId> priorityAfter(PlayerId discarder) {
        int start = seats.indexOf(discarder);
        ArrayList<PlayerId> priority = new ArrayList<>(3);
        for (int offset = 1; offset < seats.size(); offset++) {
            priority.add(seats.get((start + offset) % seats.size()));
        }
        return List.copyOf(priority);
    }

    private static int count(List<TileInstance> hand, TileKind kind) {
        return Math.toIntExact(hand.stream().filter(tile -> tile.tile().kind() == kind).count());
    }

    private static List<Tile> logical(List<TileInstance> tiles) {
        return tiles.stream().map(TileInstance::tile).toList();
    }

    private static List<TileInstance> select(PlayerState player, List<TileId> ids) {
        return ids.stream().map(id -> player.find(id).orElseThrow()).toList();
    }

    private static List<List<TileId>> chiiChoices(List<TileInstance> hand, TileKind claimed) {
        if (claimed.isHonor()) return List.of();
        EnumMap<TileKind, List<TileInstance>> byKind = new EnumMap<>(TileKind.class);
        for (TileInstance tile : hand) {
            byKind.computeIfAbsent(tile.tile().kind(), ignored -> new ArrayList<>()).add(tile);
        }
        ArrayList<List<TileId>> choices = new ArrayList<>();
        for (int start = claimed.rank() - 2; start <= claimed.rank(); start++) {
            if (start < 1 || start > 7) continue;
            ArrayList<List<TileInstance>> candidates = new ArrayList<>(2);
            for (int rank = start; rank <= start + 2; rank++) {
                if (rank == claimed.rank()) continue;
                TileKind needed = TileKind.of(claimed.suit(), rank);
                List<TileInstance> matches = byKind.getOrDefault(needed, List.of());
                if (matches.isEmpty()) {
                    candidates.clear();
                    break;
                }
                candidates.add(matches);
            }
            if (candidates.size() == 2) {
                for (TileInstance first : candidates.getFirst()) {
                    for (TileInstance second : candidates.getLast()) {
                        choices.add(List.of(first.id(), second.id()));
                    }
                }
            }
        }
        return List.copyOf(choices);
    }

    private static Set<TileKind> kuikaeForbiddenAfterChii(
            TileKind claimed,
            List<TileInstance> consumed) {
        LinkedHashSet<TileKind> forbidden = new LinkedHashSet<>();
        forbidden.add(claimed);
        boolean below = consumed.stream().allMatch(tile -> tile.tile().kind().rank() < claimed.rank());
        boolean above = consumed.stream().allMatch(tile -> tile.tile().kind().rank() > claimed.rank());
        int alternateRank = below ? claimed.rank() - 3 : above ? claimed.rank() + 3 : -1;
        if (alternateRank >= 1 && alternateRank <= 9) {
            forbidden.add(TileKind.of(claimed.suit(), alternateRank));
        }
        return Set.copyOf(forbidden);
    }

    private void cancelIppatsu() {
        players.values().forEach(player -> player.ippatsu = false);
    }

    private String doraTiming(MeldType type) {
        return KanDoraPolicy.revealBeforeRinshan(rules.profile(), type)
                ? "dora-before-rinshan"
                : "dora-before-next-discard";
    }

    private void revealNextDora() {
        if (revealedDoraCount < doraIndicatorSequence.size() && revealedDoraCount < 5) {
            revealedDoraCount++;
        }
    }

    private List<Tile> visibleDoraIndicators() {
        return doraIndicatorSequence
                .subList(0, Math.min(revealedDoraCount, doraIndicatorSequence.size()))
                .stream()
                .map(TileInstance::tile)
                .toList();
    }

    private List<Tile> visibleUraDoraIndicators() {
        return uraDoraIndicatorSequence
                .subList(0, Math.min(revealedDoraCount, uraDoraIndicatorSequence.size()))
                .stream()
                .map(TileInstance::tile)
                .toList();
    }

    private CommandResult rejected(RuleViolation violation, String message) {
        return new CommandResult(false, Optional.of(violation), message == null ? violation.name() : message, List.of(), snapshot());
    }

    private record ReactionPlan(
            Map<PlayerId, ReactionOptions> options,
            Map<PlayerId, ScoreResult> ronScores) {
    }

    private enum PaoKind {
        DAISANGEN,
        DAISUUSHII
    }

    private record PaoAward(PlayerId liable, int multiplier) {
        private PaoAward {
            Objects.requireNonNull(liable, "liable");
            if (multiplier < 1) throw new IllegalArgumentException("pao multiplier must be positive");
        }
    }

    private record PendingReaction(
            PlayerId source,
            TileInstance tile,
            ReactionWindow window,
            Map<PlayerId, ScoreResult> ronScores,
            boolean riichiDeclaration,
            Optional<PendingKan> pendingKan) {
    }

    private record PendingKan(
            MeldType type,
            Meld completedMeld,
            int replacedMeldIndex,
            List<TileInstance> handTiles) {
        private PendingKan {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(completedMeld, "completedMeld");
            handTiles = List.copyOf(Objects.requireNonNull(handTiles, "handTiles"));
            if (type != MeldType.ANKAN && type != MeldType.KAKAN) {
                throw new IllegalArgumentException("pending self-kan must be ankan or kakan");
            }
            if (completedMeld.type() != type) {
                throw new IllegalArgumentException("pending kan meld type mismatch");
            }
            if ((type == MeldType.ANKAN && replacedMeldIndex != -1)
                    || (type == MeldType.KAKAN && replacedMeldIndex < 0)) {
                throw new IllegalArgumentException("pending kan replacement index mismatch");
            }
            int expectedHandTiles = type == MeldType.ANKAN ? 4 : 1;
            if (handTiles.size() != expectedHandTiles) {
                throw new IllegalArgumentException("pending kan has invalid hand tile count");
            }
        }
    }

    private static final class PlayerState {
        private final PlayerId id;
        private int score;
        private final List<TileInstance> hand;
        private final List<Meld> melds;
        private final List<TileInstance> discards = new ArrayList<>();
        private final Set<TileId> calledDiscards = new LinkedHashSet<>();
        private final Map<PaoKind, PlayerId> paoLiabilities = new EnumMap<>(PaoKind.class);
        private final FuritenState furiten = new FuritenState();
        private boolean riichi;
        private boolean doubleRiichi;
        private boolean ippatsu;
        private TileId lastDrawn;
        private boolean lastDrawWasRinshan;
        private Set<TileKind> kuikaeForbidden = Set.of();

        private PlayerState(PlayerId id, int score, List<TileInstance> hand, List<Meld> melds) {
            this.id = id;
            this.score = score;
            this.hand = new ArrayList<>(hand);
            this.melds = new ArrayList<>(melds);
        }

        private PlayerState(PlayerState source) {
            id = source.id;
            score = source.score;
            hand = new ArrayList<>(source.hand);
            melds = new ArrayList<>(source.melds);
            discards.addAll(source.discards);
            calledDiscards.addAll(source.calledDiscards);
            paoLiabilities.putAll(source.paoLiabilities);
            furiten.restore(source.furiten.snapshot());
            riichi = source.riichi;
            doubleRiichi = source.doubleRiichi;
            ippatsu = source.ippatsu;
            lastDrawn = source.lastDrawn;
            lastDrawWasRinshan = source.lastDrawWasRinshan;
            kuikaeForbidden = source.kuikaeForbidden;
        }

        private Optional<TileInstance> find(TileId id) {
            return hand.stream().filter(tile -> tile.id().equals(id)).findFirst();
        }
    }
}

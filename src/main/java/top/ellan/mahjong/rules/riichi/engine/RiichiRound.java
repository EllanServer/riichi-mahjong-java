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
import top.ellan.mahjong.rules.riichi.settlement.AggregatedSettlement;
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
    private final List<Tile> doraIndicatorSequence;
    private final List<Tile> uraDoraIndicatorSequence;
    private final KanTracker kanTracker;
    private final Set<PlayerId> riichiPlayers = new LinkedHashSet<>();
    private final List<TileKind> firstDiscards = new ArrayList<>();

    private int currentPlayerIndex;
    private int honba;
    private int riichiSticks;
    private RoundPhase phase;
    private PendingDiscard pendingDiscard;
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
        pendingDiscard = source.pendingDiscard == null ? null : new PendingDiscard(
                source.pendingDiscard.discarder,
                source.pendingDiscard.tile,
                source.pendingDiscard.window.copy(),
                source.pendingDiscard.ronScores,
                source.pendingDiscard.riichiDeclaration);
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
                Optional.ofNullable(abortiveDraw),
                Optional.ofNullable(endReason),
                Optional.ofNullable(settlement));
    }

    /** Private player view; the integration layer is responsible for authorization. */
    public List<TileInstance> concealedHand(PlayerId player) {
        return List.copyOf(player(player).hand);
    }

    public Optional<ReactionOptions> availableReactions(PlayerId player) {
        return pendingDiscard == null ? Optional.empty() : Optional.ofNullable(pendingDiscard.window.options().get(player));
    }

    private void draw(PlayerId player, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_DRAW);
        requireCurrent(player);
        if (liveWall.isEmpty()) {
            end("EXHAUSTIVE_DRAW", null, null, events);
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
        pendingDiscard = new PendingDiscard(playerId, discarded, window, plan.ronScores, declareRiichi);
        phase = RoundPhase.AWAITING_REACTIONS;
        events.add(RoundEvent.of(RoundEvent.Type.REACTION_OPENED, playerId, discarded, "discard reactions"));
    }

    private void respond(PlayerId playerId, Reaction reaction, List<RoundEvent> events) {
        requirePhase(RoundPhase.AWAITING_REACTIONS);
        PendingDiscard pending = Objects.requireNonNull(pendingDiscard, "pendingDiscard");
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
            pendingDiscard = new PendingDiscard(
                    pending.discarder, pending.tile, windowCheckpoint, pending.ronScores, pending.riichiDeclaration);
            throw error;
        }
    }

    private void resolveReactions(
            PendingDiscard pending,
            ReactionResolution resolution,
            List<RoundEvent> events) {
        if (!resolution.ronWinners().isEmpty()) {
            resolveRon(pending, resolution.ronWinners(), events);
            return;
        }
        if (resolution.caller().isEmpty()) {
            pendingDiscard = null;
            afterUnclaimedDiscard(pending.discarder, events);
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
                Optional.of(pending.discarder),
                Optional.of(pending.tile.id()));
        caller.hand.removeAll(consumed);
        caller.melds.add(meld);
        caller.kuikaeForbidden = switch (type) {
            case PON -> Set.of(pending.tile.tile().kind());
            case CHII -> kuikaeForbiddenAfterChii(pending.tile.tile().kind(), consumed);
            case MINKAN -> Set.of();
            case ANKAN, KAKAN -> throw new IllegalStateException("not a discard reaction");
        };
        pendingDiscard = null;
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
        if (state.riichi || state.doubleRiichi) {
            throw new RuleViolationException(
                    RuleViolation.UNSUPPORTED_RULE_FLOW,
                    "phase 1 fails closed for riichi ankan wait-preservation validation");
        }
        List<TileInstance> matching = state.hand.stream()
                .filter(tile -> tile.tile().kind() == kind)
                .toList();
        if (matching.size() != 4) {
            throw new RuleViolationException(
                    RuleViolation.UNSUPPORTED_RULE_FLOW,
                    "phase 1 self-kan requires an ankan; kakan/chankan is fail-closed");
        }
        if (ankanKokushiChankanPossible(playerId, matching.getFirst().tile())) {
            throw new RuleViolationException(
                    RuleViolation.UNSUPPORTED_RULE_FLOW,
                    "Kokushi ankan robbery requires an explicit chankan window");
        }
        Meld ankan = new Meld(MeldType.ANKAN, matching, Optional.empty(), Optional.empty());
        KanTracker.Registration registration = kanTracker.registerSelfKan(playerId);
        pendingFourKanAbort |= registration == KanTracker.Registration.ABORT_AFTER_DISCARD;
        revealNextDora();
        state.hand.removeAll(matching);
        state.melds.add(ankan);
        firstTurnUninterrupted = false;
        anyCallMade = true;
        cancelIppatsu();
        events.add(RoundEvent.of(RoundEvent.Type.KAN_REGISTERED, playerId, matching.getFirst(), doraTiming(MeldType.ANKAN)));
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
        LinkedHashMap<PlayerId, Integer> payers = new LinkedHashMap<>();
        boolean winnerDealer = seatWind(playerId) == Wind.EAST;
        for (PlayerId opponent : seats) {
            if (opponent.equals(playerId)) continue;
            int amount = winnerDealer || seatWind(opponent) != Wind.EAST
                    ? payment.nonDealerPaysEach()
                    : payment.dealerPays();
            payers.put(opponent, amount);
        }
        ArrayList<PaymentTransfer> transfers = new ArrayList<>(
                SettlementAggregator.tsumo(playerId, payers, honba));
        if (riichiSticks > 0) transfers.add(SettlementAggregator.riichiPool(playerId, riichiSticks));
        finishSettlement("TSUMO", transfers, events);
    }

    private void resolveRon(PendingDiscard pending, List<PlayerId> winners, List<RoundEvent> events) {
        boolean refundDeclaration = pending.riichiDeclaration;
        int payableRiichiSticks = refundDeclaration ? riichiSticks - 1 : riichiSticks;
        if (payableRiichiSticks < 0) {
            throw new RuleViolationException(
                    RuleViolation.UNSUPPORTED_RULE_FLOW, "riichi declaration refund has no matching stick");
        }
        ArrayList<PaymentTransfer> transfers = new ArrayList<>();
        if (refundDeclaration) {
            transfers.add(SettlementAggregator.riichiRefund(pending.discarder));
        }
        for (int index = 0; index < winners.size(); index++) {
            PlayerId winner = winners.get(index);
            ScoreResult score = pending.ronScores.get(winner);
            requireLegalWin(score);
            transfers.addAll(SettlementAggregator.ron(
                    winner,
                    pending.discarder,
                    score.ronPayment().orElseThrow().discarderPays(),
                    honba,
                    index == 0));
        }
        if (payableRiichiSticks > 0) {
            transfers.add(SettlementAggregator.riichiPool(winners.getFirst(), payableRiichiSticks));
        }
        finishSettlement("RON", transfers, events);
        pendingDiscard = null;
        if (refundDeclaration) {
            PlayerState declarer = player(pending.discarder);
            declarer.riichi = false;
            declarer.doubleRiichi = false;
            declarer.ippatsu = false;
            riichiPlayers.remove(pending.discarder);
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

    private boolean ankanKokushiChankanPossible(PlayerId declarer, Tile tile) {
        for (PlayerId candidateId : priorityAfter(declarer)) {
            PlayerState candidate = player(candidateId);
            HandAnalysis analysis = handEvaluator.analyze(logical(candidate.hand), candidate.melds);
            if (!analysis.waits().contains(tile.kind()) || candidate.furiten.isFuriten(analysis.waits())) continue;
            ScoreResult result = score(candidate, tile, false, WinMethod.RON, true);
            if (result.eligibleByMinimumHan()
                    && result.yaku().stream().anyMatch(yaku -> yaku.id().startsWith("KOKUSHIMUSO"))) {
                return true;
            }
        }
        return false;
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
            end("EXHAUSTIVE_DRAW", null, null, events);
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
        riichiSticks = 0;
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
        return doraIndicatorSequence.subList(0, Math.min(revealedDoraCount, doraIndicatorSequence.size()));
    }

    private List<Tile> visibleUraDoraIndicators() {
        return uraDoraIndicatorSequence.subList(0, Math.min(revealedDoraCount, uraDoraIndicatorSequence.size()));
    }

    private CommandResult rejected(RuleViolation violation, String message) {
        return new CommandResult(false, Optional.of(violation), message == null ? violation.name() : message, List.of(), snapshot());
    }

    private record ReactionPlan(
            Map<PlayerId, ReactionOptions> options,
            Map<PlayerId, ScoreResult> ronScores) {
    }

    private record PendingDiscard(
            PlayerId discarder,
            TileInstance tile,
            ReactionWindow window,
            Map<PlayerId, ScoreResult> ronScores,
            boolean riichiDeclaration) {
    }

    private static final class PlayerState {
        private final PlayerId id;
        private int score;
        private final List<TileInstance> hand;
        private final List<Meld> melds;
        private final List<TileInstance> discards = new ArrayList<>();
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

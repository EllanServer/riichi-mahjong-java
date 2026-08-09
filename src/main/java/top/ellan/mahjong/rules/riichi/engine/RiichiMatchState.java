package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.RiichiServices;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable complete-match owner used by actor and asynchronous adapters. */
public final class RiichiMatchState {
    private final long revision;
    private final RiichiMatchRules rules;
    private final List<PlayerId> seats;
    private final long rootSeed;
    private final long handSerial;
    private final RiichiMatchPosition position;
    private final Map<PlayerId, Integer> scores;
    private final RiichiMatchPhase phase;
    private final RiichiRoundState roundState;
    private final List<PlayerId> ranking;
    private final Optional<String> endReason;
    private final HandEvaluator evaluator;
    private final ScoreCalculator calculator;

    private RiichiMatchState(
            long revision,
            RiichiMatchRules rules,
            List<PlayerId> seats,
            long rootSeed,
            long handSerial,
            RiichiMatchPosition position,
            Map<PlayerId, Integer> scores,
            RiichiMatchPhase phase,
            RiichiRoundState roundState,
            List<PlayerId> ranking,
            Optional<String> endReason,
            HandEvaluator evaluator,
            ScoreCalculator calculator) {
        if (revision < 0 || handSerial < 0) {
            throw new IllegalArgumentException("match revisions cannot be negative");
        }
        this.revision = revision;
        this.rules = Objects.requireNonNull(rules, "rules");
        this.seats = List.copyOf(Objects.requireNonNull(seats, "seats"));
        this.rootSeed = rootSeed;
        this.handSerial = handSerial;
        this.position = Objects.requireNonNull(position, "position");
        this.scores = Map.copyOf(Objects.requireNonNull(scores, "scores"));
        this.phase = Objects.requireNonNull(phase, "phase");
        this.roundState = Objects.requireNonNull(roundState, "roundState");
        this.ranking = List.copyOf(Objects.requireNonNull(ranking, "ranking"));
        this.endReason = Objects.requireNonNull(endReason, "endReason");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        if (this.seats.size() != 4 || this.seats.stream().distinct().count() != 4
                || this.ranking.size() != 4
                || !this.scores.keySet().equals(java.util.Set.copyOf(this.seats))
                || !this.scores.keySet().equals(java.util.Set.copyOf(this.ranking))) {
            throw new IllegalArgumentException("match state requires four unique ranked seats");
        }
        if ((phase == RiichiMatchPhase.ENDED) != endReason.isPresent()) {
            throw new IllegalArgumentException("ended phase and end reason disagree");
        }
    }

    public static RiichiMatchState start(
            RiichiMatchRules rules,
            List<PlayerId> seats,
            long seed) {
        return start(
                rules,
                seats,
                seed,
                RiichiServices.handEvaluator(),
                RiichiServices.scoreCalculator());
    }

    public static RiichiMatchState start(
            RiichiMatchRules rules,
            List<PlayerId> rawSeats,
            long seed,
            HandEvaluator evaluator,
            ScoreCalculator calculator) {
        Objects.requireNonNull(rules, "rules");
        List<PlayerId> seats = List.copyOf(rawSeats);
        if (seats.size() != 4 || seats.stream().distinct().count() != 4) {
            throw new IllegalArgumentException("Riichi match requires four unique players");
        }
        LinkedHashMap<PlayerId, Integer> scores = new LinkedHashMap<>();
        seats.forEach(player -> scores.put(player, rules.roundRules().startingPoints()));
        RiichiMatchPosition position = RiichiMatchPosition.eastOne();
        RiichiRoundState round = createRound(
                rules,
                seats,
                seed,
                0,
                position,
                scores,
                evaluator,
                calculator);
        return new RiichiMatchState(
                0,
                rules,
                seats,
                seed,
                0,
                position,
                scores,
                RiichiMatchPhase.ACTIVE_ROUND,
                round,
                RiichiMatchProgression.ranking(seats, scores),
                Optional.empty(),
                evaluator,
                calculator);
    }

    public long revision() {
        return revision;
    }

    public RiichiMatchRules rules() {
        return rules;
    }

    public List<PlayerId> seats() {
        return seats;
    }

    public long handSerial() {
        return handSerial;
    }

    /** The root seed supplied when the match started. */
    public long rootSeed() {
        return rootSeed;
    }

    /** Deterministic per-hand seed derived from the root seed and the current hand serial. */
    public long currentHandSeed() {
        return mixSeed(rootSeed, handSerial);
    }

    public RiichiMatchPosition position() {
        return position;
    }

    public Map<PlayerId, Integer> scores() {
        return scores;
    }

    public RiichiMatchPhase phase() {
        return phase;
    }

    public RiichiRoundState roundState() {
        return roundState;
    }

    public List<PlayerId> ranking() {
        return ranking;
    }

    public Optional<String> endReason() {
        return endReason;
    }

    RiichiMatchState withRound(RiichiRoundState updatedRound) {
        RoundSnapshot snapshot = updatedRound.publicSnapshot();
        RiichiMatchPosition updatedPosition = new RiichiMatchPosition(
                position.roundWind(),
                position.handNumber(),
                position.dealerIndex(),
                snapshot.honba(),
                snapshot.riichiSticks());
        return new RiichiMatchState(
                revision + 1,
                rules,
                seats,
                rootSeed,
                handSerial,
                updatedPosition,
                snapshot.scores(),
                RiichiMatchPhase.ACTIVE_ROUND,
                updatedRound,
                RiichiMatchProgression.ranking(seats, snapshot.scores()),
                Optional.empty(),
                evaluator,
                calculator);
    }

    RiichiMatchState atBoundary(RiichiRoundState endedRound, RiichiMatchAdvance advance) {
        return new RiichiMatchState(
                revision + 1,
                rules,
                seats,
                rootSeed,
                handSerial,
                advance.position(),
                advance.scores(),
                advance.matchEnded() ? RiichiMatchPhase.ENDED : RiichiMatchPhase.BETWEEN_ROUNDS,
                endedRound,
                advance.ranking(),
                advance.endReason(),
                evaluator,
                calculator);
    }

    RiichiMatchState startNextRound() {
        long nextSerial = Math.addExact(handSerial, 1);
        RiichiRoundState nextRound = createRound(
                rules,
                seats,
                rootSeed,
                nextSerial,
                position,
                scores,
                evaluator,
                calculator);
        return new RiichiMatchState(
                revision + 1,
                rules,
                seats,
                rootSeed,
                nextSerial,
                position,
                scores,
                RiichiMatchPhase.ACTIVE_ROUND,
                nextRound,
                RiichiMatchProgression.ranking(seats, scores),
                Optional.empty(),
                evaluator,
                calculator);
    }

    /**
     * Rebuilds a previously snapshot match. Ranking is recomputed deterministically
     * from the recorded scores, and the round state must have been restored first.
     */
    public static RiichiMatchState restored(
            long revision,
            RiichiMatchRules rules,
            List<PlayerId> seats,
            long rootSeed,
            long handSerial,
            RiichiMatchPosition position,
            Map<PlayerId, Integer> scores,
            RiichiMatchPhase phase,
            RiichiRoundState roundState,
            Optional<String> endReason,
            HandEvaluator evaluator,
            ScoreCalculator calculator) {
        return new RiichiMatchState(
                revision,
                rules,
                seats,
                rootSeed,
                handSerial,
                position,
                scores,
                phase,
                roundState,
                RiichiMatchProgression.ranking(seats, scores),
                endReason,
                evaluator,
                calculator);
    }

    private static RiichiRoundState createRound(
            RiichiMatchRules rules,
            List<PlayerId> seats,
            long rootSeed,
            long handSerial,
            RiichiMatchPosition position,
            Map<PlayerId, Integer> scores,
            HandEvaluator evaluator,
            ScoreCalculator calculator) {
        long handSeed = mixSeed(rootSeed, handSerial);
        Scenario scenario = Scenario.standard(
                rules.roundRules(),
                seats,
                handSeed,
                position.dealerIndex(),
                position.roundWind(),
                position.honba(),
                position.riichiSticks(),
                scores);
        return RiichiRoundState.start(scenario, evaluator, calculator);
    }

    private static long mixSeed(long rootSeed, long handSerial) {
        long value = rootSeed + 0x9E3779B97F4A7C15L * (handSerial + 1);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}

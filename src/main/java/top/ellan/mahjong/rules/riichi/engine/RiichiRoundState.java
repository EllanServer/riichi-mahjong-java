package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable owner of one complete mutable-core image. Transitions always fork
 * the hidden image before applying a command, so rejected and competing work
 * cannot mutate this revision.
 */
public final class RiichiRoundState {
    private final long revision;
    private final RiichiRound image;
    private final List<RoundCommand> history;

    RiichiRoundState(long revision, RiichiRound image) {
        this(revision, image, List.of());
    }

    private RiichiRoundState(long revision, RiichiRound image, List<RoundCommand> history) {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        this.revision = revision;
        this.image = Objects.requireNonNull(image, "image");
        this.history = List.copyOf(history);
    }

    public static RiichiRoundState start(Scenario scenario) {
        return new RiichiRoundState(0, RiichiRound.fromScenario(scenario), List.of());
    }

    public static RiichiRoundState start(
            Scenario scenario,
            HandEvaluator evaluator,
            ScoreCalculator calculator) {
        return new RiichiRoundState(0, RiichiRound.fromScenario(scenario, evaluator, calculator), List.of());
    }

    public long revision() {
        return revision;
    }

    /** Commands applied since the round started, in order; used for deterministic restores. */
    public List<RoundCommand> history() {
        return history;
    }

    /** The scenario that started this round. */
    public Scenario scenario() {
        return image.scenario();
    }

    public RoundSnapshot publicSnapshot() {
        return image.snapshot();
    }

    public List<TileInstance> concealedHand(PlayerId player) {
        return image.concealedHand(player);
    }

    public Optional<ReactionOptions> availableReactions(PlayerId player) {
        return image.availableReactions(player);
    }

    /** Commands the given player may legally submit right now. */
    public List<RoundCommand> legalCommands(PlayerId player) {
        return image.legalCommands(player);
    }

    /** Remaining live wall tiles in physical draw order. */
    public List<TileInstance> liveWall() {
        return image.liveWallCopy();
    }

    /** Remaining rinshan tiles in draw order. */
    public List<TileInstance> rinshan() {
        return image.rinshanCopy();
    }

    /** Dora indicators flipped so far, in physical order. */
    public List<TileInstance> revealedDoraIndicators() {
        return image.revealedDoraIndicators();
    }

    /** All ura-dora indicators; presentation decides exposure. */
    public List<TileInstance> uraDoraIndicators() {
        return image.uraDoraIndicators();
    }

    RiichiRound forkImage() {
        return image.copy();
    }

    RiichiRoundState advanced(RiichiRound nextImage, RoundCommand command) {
        ArrayList<RoundCommand> nextHistory = new ArrayList<>(history.size() + 1);
        nextHistory.addAll(history);
        nextHistory.add(command);
        return new RiichiRoundState(revision + 1, nextImage, nextHistory);
    }
}

package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

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

    RiichiRoundState(long revision, RiichiRound image) {
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        this.revision = revision;
        this.image = Objects.requireNonNull(image, "image");
    }

    public static RiichiRoundState start(Scenario scenario) {
        return new RiichiRoundState(0, RiichiRound.fromScenario(scenario));
    }

    public static RiichiRoundState start(
            Scenario scenario,
            HandEvaluator evaluator,
            ScoreCalculator calculator) {
        return new RiichiRoundState(0, RiichiRound.fromScenario(scenario, evaluator, calculator));
    }

    public long revision() {
        return revision;
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

    RiichiRound forkImage() {
        return image.copy();
    }
}

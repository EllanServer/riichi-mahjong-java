package top.ellan.mahjong.rules.riichi.internal;

import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.internal.scoring.NativeScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

/** Owns the dependency-free Java implementations used by the rule pack. */
public final class NativeRiichiServiceFactory {
    private final HandEvaluator handEvaluator = new NativeHandEvaluator();
    private final ScoreCalculator scoreCalculator = new NativeScoreCalculator();

    public NativeRiichiServiceFactory() {
    }

    public HandEvaluator handEvaluator() {
        return handEvaluator;
    }

    public ScoreCalculator scoreCalculator() {
        return scoreCalculator;
    }
}

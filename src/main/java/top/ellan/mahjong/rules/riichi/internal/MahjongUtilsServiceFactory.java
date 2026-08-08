package top.ellan.mahjong.rules.riichi.internal;

import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

public final class MahjongUtilsServiceFactory {
    private final HandEvaluator handEvaluator = new NativeHandEvaluator();
    private volatile ScoreCalculator scoreCalculator;

    public MahjongUtilsServiceFactory() {
    }

    public HandEvaluator handEvaluator() {
        return handEvaluator;
    }

    public ScoreCalculator scoreCalculator() {
        ScoreCalculator current = scoreCalculator;
        if (current != null) return current;
        synchronized (this) {
            current = scoreCalculator;
            if (current == null) {
                current = new MahjongUtilsScoreCalculator(new MahjongUtilsBridge(), handEvaluator);
                scoreCalculator = current;
            }
            return current;
        }
    }
}

package top.ellan.mahjong.rules.riichi;

import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.internal.NativeRiichiServiceFactory;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

/** Stable Java-only entrypoints for the native evaluator and score calculator. */
public final class RiichiServices {
    private RiichiServices() {
    }

    public static HandEvaluator handEvaluator() {
        return Holder.FACTORY.handEvaluator();
    }

    public static ScoreCalculator scoreCalculator() {
        return Holder.FACTORY.scoreCalculator();
    }

    private static final class Holder {
        private static final NativeRiichiServiceFactory FACTORY = new NativeRiichiServiceFactory();
    }
}

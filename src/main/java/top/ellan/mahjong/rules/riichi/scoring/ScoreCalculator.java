package top.ellan.mahjong.rules.riichi.scoring;

import top.ellan.mahjong.rules.riichi.evaluation.EvaluationException;

public interface ScoreCalculator {
    ScoreResult score(ScoreRequest request) throws EvaluationException;
}

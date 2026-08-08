package top.ellan.mahjong.rules.riichi.evaluation;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationFailureTest {
    @Test
    void scoreCalculatorContractDoesNotConvertInfrastructureFailureToNoYaku() {
        ScoreCalculator failing = request -> {
            throw new EvaluationException("backend unavailable");
        };
        EvaluationException error = assertThrows(EvaluationException.class, () -> failing.score(null));
        assertEquals("backend unavailable", error.getMessage());
    }
}

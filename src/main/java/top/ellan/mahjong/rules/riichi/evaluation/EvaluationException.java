package top.ellan.mahjong.rules.riichi.evaluation;

/** Infrastructure/evaluator failure. This is intentionally distinct from a legal no-yaku result. */
public final class EvaluationException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EvaluationException(String message) {
        super(message);
    }

    public EvaluationException(String message, Throwable cause) {
        super(message, cause);
    }
}

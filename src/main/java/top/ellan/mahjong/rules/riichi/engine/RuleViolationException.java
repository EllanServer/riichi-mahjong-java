package top.ellan.mahjong.rules.riichi.engine;

public final class RuleViolationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final RuleViolation violation;

    public RuleViolationException(RuleViolation violation, String message) {
        super(message);
        this.violation = java.util.Objects.requireNonNull(violation, "violation");
    }

    public RuleViolation violation() {
        return violation;
    }
}

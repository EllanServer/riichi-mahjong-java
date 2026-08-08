package top.ellan.mahjong.rules.riichi.engine;

/** Match-boundary event; hand events remain typed as {@link RoundEvent}. */
public record RiichiMatchEvent(Type type, String detail) {
    public enum Type {
        ROUND_ENDED,
        ROUND_STARTED,
        MATCH_ENDED
    }

    public RiichiMatchEvent {
        if (type == null) throw new IllegalArgumentException("match event type is required");
        if (detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("match event detail is required");
        }
    }
}

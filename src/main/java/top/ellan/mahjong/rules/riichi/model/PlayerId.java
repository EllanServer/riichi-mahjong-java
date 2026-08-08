package top.ellan.mahjong.rules.riichi.model;

public record PlayerId(String value) implements Comparable<PlayerId> {
    public PlayerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("player id is required");
        }
    }

    @Override
    public int compareTo(PlayerId other) {
        return value.compareTo(other.value);
    }
}

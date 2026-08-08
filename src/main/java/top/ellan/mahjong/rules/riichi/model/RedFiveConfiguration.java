package top.ellan.mahjong.rules.riichi.model;

import java.util.EnumMap;
import java.util.Map;

/**
 * Physical red-five supply, expressed per suit instead of as an ambiguous
 * table-wide count.
 */
public record RedFiveConfiguration(int man, int pin, int sou) {
    public RedFiveConfiguration {
        if (man < 0 || man > 4 || pin < 0 || pin > 4 || sou < 0 || sou > 4) {
            throw new IllegalArgumentException("each suit can contain between zero and four red fives");
        }
    }

    public static RedFiveConfiguration none() {
        return new RedFiveConfiguration(0, 0, 0);
    }

    public static RedFiveConfiguration standardThree() {
        return new RedFiveConfiguration(1, 1, 1);
    }

    public int total() {
        return Math.addExact(Math.addExact(man, pin), sou);
    }

    public int copiesOf(TileKind kind) {
        if (kind == null || kind.isHonor() || kind.rank() != 5) {
            return 0;
        }
        return switch (kind.suit()) {
            case MAN -> man;
            case PIN -> pin;
            case SOU -> sou;
            case HONOR -> 0;
        };
    }

    public Map<TileKind, Integer> asMap() {
        EnumMap<TileKind, Integer> result = new EnumMap<>(TileKind.class);
        if (man > 0) result.put(TileKind.M5, man);
        if (pin > 0) result.put(TileKind.P5, pin);
        if (sou > 0) result.put(TileKind.S5, sou);
        return Map.copyOf(result);
    }
}

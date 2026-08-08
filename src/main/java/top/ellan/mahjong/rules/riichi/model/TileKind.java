package top.ellan.mahjong.rules.riichi.model;

import java.util.Locale;

/** One of the 34 logical Riichi tile kinds. Red fives are represented by {@link Tile}. */
public enum TileKind {
    M1(Suit.MAN, 1), M2(Suit.MAN, 2), M3(Suit.MAN, 3), M4(Suit.MAN, 4), M5(Suit.MAN, 5),
    M6(Suit.MAN, 6), M7(Suit.MAN, 7), M8(Suit.MAN, 8), M9(Suit.MAN, 9),
    P1(Suit.PIN, 1), P2(Suit.PIN, 2), P3(Suit.PIN, 3), P4(Suit.PIN, 4), P5(Suit.PIN, 5),
    P6(Suit.PIN, 6), P7(Suit.PIN, 7), P8(Suit.PIN, 8), P9(Suit.PIN, 9),
    S1(Suit.SOU, 1), S2(Suit.SOU, 2), S3(Suit.SOU, 3), S4(Suit.SOU, 4), S5(Suit.SOU, 5),
    S6(Suit.SOU, 6), S7(Suit.SOU, 7), S8(Suit.SOU, 8), S9(Suit.SOU, 9),
    EAST(Suit.HONOR, 1), SOUTH(Suit.HONOR, 2), WEST(Suit.HONOR, 3), NORTH(Suit.HONOR, 4),
    WHITE_DRAGON(Suit.HONOR, 5), GREEN_DRAGON(Suit.HONOR, 6), RED_DRAGON(Suit.HONOR, 7);

    public enum Suit { MAN, PIN, SOU, HONOR }

    private final Suit suit;
    private final int rank;

    TileKind(Suit suit, int rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public Suit suit() {
        return suit;
    }

    public int rank() {
        return rank;
    }

    public boolean isHonor() {
        return suit == Suit.HONOR;
    }

    public boolean isWind() {
        return isHonor() && rank <= 4;
    }

    public boolean isDragon() {
        return isHonor() && rank >= 5;
    }

    public boolean isTerminal() {
        return !isHonor() && (rank == 1 || rank == 9);
    }

    public boolean isTerminalOrHonor() {
        return isHonor() || isTerminal();
    }

    public TileKind dora() {
        if (suit != Suit.HONOR) {
            return of(suit, rank == 9 ? 1 : rank + 1);
        }
        if (rank <= 4) {
            return of(Suit.HONOR, rank == 4 ? 1 : rank + 1);
        }
        return of(Suit.HONOR, rank == 7 ? 5 : rank + 1);
    }

    public String notation() {
        if (isHonor()) {
            return rank + "z";
        }
        char suffix = switch (suit) {
            case MAN -> 'm';
            case PIN -> 'p';
            case SOU -> 's';
            case HONOR -> throw new IllegalStateException("honor handled above");
        };
        return Integer.toString(rank) + suffix;
    }

    public static TileKind of(Suit suit, int rank) {
        if (suit == null) {
            throw new IllegalArgumentException("suit is required");
        }
        int max = suit == Suit.HONOR ? 7 : 9;
        if (rank < 1 || rank > max) {
            throw new IllegalArgumentException("invalid " + suit + " rank: " + rank);
        }
        int ordinal = switch (suit) {
            case MAN -> rank - 1;
            case PIN -> 9 + rank - 1;
            case SOU -> 18 + rank - 1;
            case HONOR -> 27 + rank - 1;
        };
        return values()[ordinal];
    }

    public static TileKind parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("tile kind is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() == 2 && Character.isDigit(normalized.charAt(0))) {
            int rank = normalized.charAt(0) - '0';
            Suit suit = switch (normalized.charAt(1)) {
                case 'm' -> Suit.MAN;
                case 'p' -> Suit.PIN;
                case 's' -> Suit.SOU;
                case 'z' -> Suit.HONOR;
                default -> throw new IllegalArgumentException("unknown tile notation: " + value);
            };
            if (rank == 0 && suit != Suit.HONOR) {
                rank = 5;
            }
            return of(suit, rank);
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

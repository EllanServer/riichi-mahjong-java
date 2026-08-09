package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** SPI identity envelope around a platform-independent Riichi match. */
public final class RiichiProviderState implements top.ellan.mahjong.spi.RuleState {
    private final RiichiMatchState match;
    private final top.ellan.mahjong.spi.PlayerId[] playersByInitialSeat;

    RiichiProviderState(
            RiichiMatchState match,
            List<top.ellan.mahjong.spi.PlayerId> playersByInitialSeat) {
        this.match = Objects.requireNonNull(match, "match");
        Objects.requireNonNull(playersByInitialSeat, "playersByInitialSeat");
        if (playersByInitialSeat.size() != 4
                || playersByInitialSeat.stream().distinct().count() != 4) {
            throw new IllegalArgumentException("exactly four distinct Riichi players are required");
        }
        this.playersByInitialSeat = playersByInitialSeat.toArray(top.ellan.mahjong.spi.PlayerId[]::new);
        for (top.ellan.mahjong.spi.PlayerId player : this.playersByInitialSeat) {
            Objects.requireNonNull(player, "seated player");
        }
    }

    public RiichiMatchState match() {
        return match;
    }

    public top.ellan.mahjong.spi.PlayerId player(int initialSeat) {
        if (initialSeat < 0 || initialSeat >= playersByInitialSeat.length) {
            throw new IllegalArgumentException("invalid initial seat: " + initialSeat);
        }
        return playersByInitialSeat[initialSeat];
    }

    public int initialSeat(top.ellan.mahjong.spi.PlayerId player) {
        Objects.requireNonNull(player, "player");
        for (int seat = 0; seat < playersByInitialSeat.length; seat++) {
            if (playersByInitialSeat[seat].equals(player)) {
                return seat;
            }
        }
        return -1;
    }

    List<top.ellan.mahjong.spi.PlayerId> playersByInitialSeat() {
        return List.copyOf(Arrays.asList(playersByInitialSeat.clone()));
    }

    RiichiProviderState withMatch(RiichiMatchState nextMatch) {
        return new RiichiProviderState(nextMatch, playersByInitialSeat());
    }

    static top.ellan.mahjong.rules.riichi.model.PlayerId toDomain(
            top.ellan.mahjong.spi.PlayerId player) {
        Objects.requireNonNull(player, "player");
        return new top.ellan.mahjong.rules.riichi.model.PlayerId(player.value().toString());
    }
}

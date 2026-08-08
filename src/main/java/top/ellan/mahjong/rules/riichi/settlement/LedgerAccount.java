package top.ellan.mahjong.rules.riichi.settlement;

import top.ellan.mahjong.rules.riichi.model.PlayerId;

import java.util.Objects;
import java.util.Optional;

public record LedgerAccount(Kind kind, String id) implements Comparable<LedgerAccount> {
    public enum Kind { PLAYER, TABLE }

    public LedgerAccount {
        Objects.requireNonNull(kind, "kind");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("account id is required");
        }
    }

    public static LedgerAccount player(PlayerId player) {
        return new LedgerAccount(Kind.PLAYER, player.value());
    }

    public static LedgerAccount table() {
        return new LedgerAccount(Kind.TABLE, "RIICHI_POOL");
    }

    public Optional<PlayerId> playerId() {
        return kind == Kind.PLAYER ? Optional.of(new PlayerId(id)) : Optional.empty();
    }

    @Override
    public int compareTo(LedgerAccount other) {
        int byKind = kind.compareTo(other.kind);
        return byKind == 0 ? id.compareTo(other.id) : byKind;
    }
}

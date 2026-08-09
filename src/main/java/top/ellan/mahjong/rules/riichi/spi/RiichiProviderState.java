package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.model.TileId;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** SPI identity envelope around a platform-independent Riichi match. */
public final class RiichiProviderState implements top.ellan.mahjong.spi.RuleState {
    private final RiichiMatchState match;
    private final top.ellan.mahjong.spi.PlayerId[] playersByInitialSeat;
    private final RiichiProjectionIds projectionIds;
    private final short[] wallSlotsByProjection;
    private final short[] projectionsByWallSlot;

    RiichiProviderState(
            RiichiMatchState match,
            List<top.ellan.mahjong.spi.PlayerId> playersByInitialSeat) {
        this(
                match,
                validatedPlayers(playersByInitialSeat),
                RiichiProjectionIds.forHand(
                        Objects.requireNonNull(match, "match").currentHandSeed()),
                null,
                null);
    }

    private RiichiProviderState(
            RiichiMatchState match,
            top.ellan.mahjong.spi.PlayerId[] playersByInitialSeat,
            RiichiProjectionIds projectionIds,
            short[] wallSlotsByProjection,
            short[] projectionsByWallSlot) {
        this.match = Objects.requireNonNull(match, "match");
        this.playersByInitialSeat = playersByInitialSeat;
        this.projectionIds = Objects.requireNonNull(projectionIds, "projectionIds");
        if (wallSlotsByProjection == null || projectionsByWallSlot == null) {
            WallSlots slots = createWallSlots(match.currentHandSeed(), projectionIds);
            this.wallSlotsByProjection = slots.byProjection();
            this.projectionsByWallSlot = slots.byWallSlot();
        } else {
            this.wallSlotsByProjection = wallSlotsByProjection;
            this.projectionsByWallSlot = projectionsByWallSlot;
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

    RiichiProjectionIds projectionIds() {
        return projectionIds;
    }

    int wallSlot(long projectionId) {
        if (projectionId < 0 || projectionId >= wallSlotsByProjection.length) {
            throw new IllegalArgumentException("invalid projected Riichi tile");
        }
        return Short.toUnsignedInt(wallSlotsByProjection[(int) projectionId]);
    }

    long projectionAtWallSlot(int wallSlot) {
        if (wallSlot < 0 || wallSlot >= projectionsByWallSlot.length) {
            throw new IllegalArgumentException("invalid Riichi wall slot");
        }
        return Short.toUnsignedInt(projectionsByWallSlot[wallSlot]);
    }

    RiichiProviderState withMatch(RiichiMatchState nextMatch) {
        if (match.currentHandSeed() == nextMatch.currentHandSeed()) {
            return new RiichiProviderState(
                    nextMatch,
                    playersByInitialSeat,
                    projectionIds,
                    wallSlotsByProjection,
                    projectionsByWallSlot);
        }
        RiichiProjectionIds nextProjectionIds =
                RiichiProjectionIds.forHand(nextMatch.currentHandSeed());
        return new RiichiProviderState(
                nextMatch, playersByInitialSeat, nextProjectionIds, null, null);
    }

    static top.ellan.mahjong.rules.riichi.model.PlayerId toDomain(
            top.ellan.mahjong.spi.PlayerId player) {
        Objects.requireNonNull(player, "player");
        return new top.ellan.mahjong.rules.riichi.model.PlayerId(player.value().toString());
    }

    private static top.ellan.mahjong.spi.PlayerId[] validatedPlayers(
            List<top.ellan.mahjong.spi.PlayerId> players) {
        Objects.requireNonNull(players, "playersByInitialSeat");
        if (players.size() != 4 || players.stream().distinct().count() != 4) {
            throw new IllegalArgumentException("exactly four distinct Riichi players are required");
        }
        top.ellan.mahjong.spi.PlayerId[] result =
                players.toArray(top.ellan.mahjong.spi.PlayerId[]::new);
        for (top.ellan.mahjong.spi.PlayerId player : result) {
            Objects.requireNonNull(player, "seated player");
        }
        return result;
    }

    private static WallSlots createWallSlots(long handSeed, RiichiProjectionIds projectionIds) {
        int[] physicalIds = new int[RiichiProjectionIds.PHYSICAL_TILE_COUNT];
        for (int id = 0; id < RiichiProjectionIds.PHYSICAL_TILE_COUNT; id++) {
            physicalIds[id] = id;
        }
        Random random = new Random(handSeed);
        // Exactly mirrors Collections.shuffle(Random) without boxing 136 integers per hand.
        for (int remaining = physicalIds.length; remaining > 1; remaining--) {
            int other = random.nextInt(remaining);
            int last = physicalIds[remaining - 1];
            physicalIds[remaining - 1] = physicalIds[other];
            physicalIds[other] = last;
        }
        short[] byProjection = new short[RiichiProjectionIds.PHYSICAL_TILE_COUNT];
        short[] byWallSlot = new short[RiichiProjectionIds.PHYSICAL_TILE_COUNT];
        for (int slot = 0; slot < physicalIds.length; slot++) {
            int projection = Math.toIntExact(
                    projectionIds.project(new TileId(physicalIds[slot])));
            byProjection[projection] = (short) slot;
            byWallSlot[slot] = (short) projection;
        }
        return new WallSlots(byProjection, byWallSlot);
    }

    private record WallSlots(short[] byProjection, short[] byWallSlot) {}
}

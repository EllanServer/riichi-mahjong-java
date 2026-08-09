package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPhase;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RiichiRoundState;
import top.ellan.mahjong.rules.riichi.engine.RoundPhase;
import top.ellan.mahjong.rules.riichi.engine.RoundSnapshot;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Projects domain state into public and authorized-private scene views. */
public final class RiichiViewProjector {
    private static final int[] POINT_STICK_DENOMINATIONS = {10_000, 5_000, 1_000, 100};
    private static final int POINT_STICK_ID_BASE = RiichiProjectionIds.PHYSICAL_TILE_COUNT;
    private static final int POINT_STICK_IDS_PER_SEAT = 512;
    private static final int DEAD_WALL_START = RiichiProjectionIds.PHYSICAL_TILE_COUNT - 14;
    private static final int RINSHAN_START = RiichiProjectionIds.PHYSICAL_TILE_COUNT - 4;
    private static final top.ellan.mahjong.spi.RuleWallPresentation PHYSICAL_WALL =
            new top.ellan.mahjong.spi.RuleWallPresentation(
                    List.of(17, 17, 17, 17),
                    0,
                    top.ellan.mahjong.spi.RuleWallDirection.CLOCKWISE);
    private static final top.ellan.mahjong.spi.TileVisualId BACK =
            new top.ellan.mahjong.spi.TileVisualId("riichi:tile/back");
    private static final top.ellan.mahjong.spi.TileVisualId[] FACE_VISUALS =
            createFaceVisuals(false);
    private static final top.ellan.mahjong.spi.TileVisualId[] RED_FACE_VISUALS =
            createFaceVisuals(true);

    public top.ellan.mahjong.spi.PublicRuleView publicView(
            RiichiProviderState provider, long revision) {
        RiichiMatchState match = provider.match();
        RiichiRoundState round = match.roundState();
        RoundSnapshot snapshot = round.publicSnapshot();
        RiichiProjectionIds ids = provider.projectionIds();
        ArrayList<top.ellan.mahjong.spi.RuleViewTile> tiles = new ArrayList<>();
        Set<PlayerId> winners = snapshot.winners();
        Optional<TileId> pendingDiscard = round.pendingDiscard().map(TileInstance::id);

        for (int seat = 0; seat < 4; seat++) {
            PlayerId player = match.seats().get(seat);
            List<TileInstance> hand = round.concealedHand(player);
            boolean winner = winners.contains(player);
            for (int index = 0; index < hand.size(); index++) {
                tiles.add(node(
                        ids,
                        hand.get(index),
                        seat,
                        RiichiViewZone.HAND,
                        index,
                        winner,
                        top.ellan.mahjong.spi.RuleTilePresentation.natural(index)));
            }
        }

        for (int seat = 0; seat < 4; seat++) {
            PlayerId player = match.seats().get(seat);
            List<TileInstance> discards = snapshot.discards().getOrDefault(player, List.of());
            Optional<TileId> riichiDiscard = round.riichiDeclarationDiscard(player);
            for (int index = 0; index < discards.size(); index++) {
                TileInstance discard = discards.get(index);
                if (round.discardWasCalled(player, discard.id())) {
                    continue;
                }
                top.ellan.mahjong.spi.RuleTilePresentation presentation =
                        new top.ellan.mahjong.spi.RuleTilePresentation(
                                index,
                                riichiDiscard.filter(discard.id()::equals).isPresent()
                                        ? top.ellan.mahjong.spi.RuleTileRotation.CLOCKWISE
                                        : top.ellan.mahjong.spi.RuleTileRotation.NATURAL,
                                0,
                                pendingDiscard.filter(discard.id()::equals).isPresent());
                tiles.add(node(
                        ids,
                        discard,
                        seat,
                        RiichiViewZone.DISCARD,
                        index,
                        true,
                        presentation));
            }

            List<Meld> melds = snapshot.melds().getOrDefault(player, List.of());
            for (int meldIndex = 0; meldIndex < melds.size(); meldIndex++) {
                addMeld(ids, melds.get(meldIndex), seat, meldIndex, snapshot.phase(), tiles);
            }
        }

        boolean[] occupiedWallProjection =
                new boolean[RiichiProjectionIds.PHYSICAL_TILE_COUNT];
        for (TileInstance tile : round.liveWall()) {
            addWallTile(provider, ids, tile, false, occupiedWallProjection, tiles);
        }
        for (TileInstance tile : round.rinshan()) {
            addWallTile(provider, ids, tile, false, occupiedWallProjection, tiles);
        }
        for (TileInstance tile : round.revealedDoraIndicators()) {
            long projection = ids.project(tile);
            int wallSlot = provider.wallSlot(projection);
            occupiedWallProjection[(int) projection] = true;
            tiles.add(node(
                    ids,
                    tile,
                    RiichiViewZone.INDICATOR,
                    wallSlot,
                    true,
                    top.ellan.mahjong.spi.RuleTilePresentation.natural(wallSlot)));
        }
        for (int wallSlot = DEAD_WALL_START; wallSlot < RINSHAN_START; wallSlot++) {
            long projection = provider.projectionAtWallSlot(wallSlot);
            if (!occupiedWallProjection[(int) projection]) {
                tiles.add(hiddenWallNode(projection, wallSlot));
            }
        }
        addPointSticks(match, snapshot.scores(), tiles);

        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("profile", "mahjong-soul");
        attributes.put("matchPhase", match.phase().name());
        attributes.put("handNumber", Integer.toString(match.position().handNumber()));
        attributes.put("roundWind", match.position().roundWind().name());
        attributes.put("dealer", Integer.toString(match.position().dealerIndex()));
        attributes.put("roundPhase", snapshot.phase().name());
        int currentSeat = match.seats().indexOf(snapshot.currentPlayer());
        attributes.put(
                "currentPlayer",
                Integer.toString(currentSeat));
        attributes.put("wallRemaining", Integer.toString(snapshot.liveWallSize()));
        attributes.put("rinshanRemaining", Integer.toString(snapshot.rinshanSize()));
        attributes.put("honba", Integer.toString(snapshot.honba()));
        attributes.put("riichiSticks", Integer.toString(snapshot.riichiSticks()));
        attributes.put("kanCount", Integer.toString(snapshot.kanCount()));
        attributes.put("revealedDora", Integer.toString(snapshot.revealedDoraCount()));
        attributes.put("scores", csvSeatScores(match, snapshot.scores()));
        attributes.put("ranking", csvRanking(match));
        if (!winners.isEmpty()) {
            attributes.put("winners", csvSeatNumbers(match, winners));
        }
        if (!snapshot.tenpaiPlayers().isEmpty()) {
            attributes.put("tenpai", csvSeatNumbers(match, snapshot.tenpaiPlayers()));
        }
        if (!snapshot.nagashiWinners().isEmpty()) {
            attributes.put("nagashi", csvSeatNumbers(match, snapshot.nagashiWinners()));
        }
        snapshot.endReason().ifPresent(reason -> attributes.put("endReason", reason));
        String phase = match.phase() == RiichiMatchPhase.ACTIVE_ROUND
                ? snapshot.phase().name()
                : match.phase().name();
        Optional<top.ellan.mahjong.spi.TileInstanceId> lastDiscard = round.pendingDiscard()
                .map(ids::project)
                .map(top.ellan.mahjong.spi.TileInstanceId::new);
        return new top.ellan.mahjong.spi.PublicRuleView(
                revision,
                phase,
                tiles,
                attributes,
                new top.ellan.mahjong.spi.RuleTablePresentation(
                        4,
                        PHYSICAL_WALL,
                        6,
                        Optional.of(new top.ellan.mahjong.spi.SeatId(
                                match.position().dealerIndex())),
                        currentSeat < 0
                                ? Optional.empty()
                                : Optional.of(new top.ellan.mahjong.spi.SeatId(currentSeat)),
                        lastDiscard));
    }

    public top.ellan.mahjong.spi.PrivateRuleView privateView(
            RiichiProviderState provider,
            long revision,
            top.ellan.mahjong.spi.PlayerId viewer) {
        RiichiMatchState match = provider.match();
        PlayerId domain = RiichiProviderState.toDomain(viewer);
        int seat = match.seats().indexOf(domain);
        if (seat < 0) {
            throw new IllegalArgumentException("viewer is not seated in this Riichi match");
        }
        RiichiProjectionIds ids = provider.projectionIds();
        ArrayList<top.ellan.mahjong.spi.RuleViewTile> tiles = new ArrayList<>();
        List<TileInstance> hand = match.roundState().concealedHand(domain);
        for (int index = 0; index < hand.size(); index++) {
            tiles.add(node(
                    ids,
                    hand.get(index),
                    seat,
                    RiichiViewZone.HAND,
                    index,
                    true,
                    top.ellan.mahjong.spi.RuleTilePresentation.natural(index)));
        }
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("profile", "mahjong-soul");
        attributes.put("initialSeat", Integer.toString(seat));
        attributes.put("handSize", Integer.toString(tiles.size()));
        return new top.ellan.mahjong.spi.PrivateRuleView(
                revision,
                viewer,
                new top.ellan.mahjong.spi.SeatId(seat),
                tiles,
                attributes);
    }

    private static void addMeld(
            RiichiProjectionIds ids,
            Meld meld,
            int seat,
            int meldIndex,
            RoundPhase phase,
            List<top.ellan.mahjong.spi.RuleViewTile> tiles) {
        int claimedIndex = claimedIndex(meld);
        List<TileInstance> meldTiles = meld.tiles();
        for (int tileIndex = 0; tileIndex < meldTiles.size(); tileIndex++) {
            TileInstance tile = meldTiles.get(tileIndex);
            boolean faceUp = meld.type() != MeldType.ANKAN
                    || phase == RoundPhase.ENDED
                    || (tileIndex > 0 && tileIndex < meldTiles.size() - 1);
            boolean addedTile = meld.type() == MeldType.KAKAN
                    && tileIndex == meldTiles.size() - 1;
            int layoutIndex = meldIndex * 4 + (addedTile ? claimedIndex : tileIndex);
            boolean sideways = tileIndex == claimedIndex || addedTile;
            top.ellan.mahjong.spi.RuleTilePresentation presentation =
                    new top.ellan.mahjong.spi.RuleTilePresentation(
                            layoutIndex,
                            sideways
                                    ? top.ellan.mahjong.spi.RuleTileRotation.CLOCKWISE
                                    : top.ellan.mahjong.spi.RuleTileRotation.NATURAL,
                            addedTile ? 1 : 0,
                            false);
            tiles.add(node(
                    ids,
                    tile,
                    seat,
                    RiichiViewZone.MELD,
                    meldIndex * 4 + tileIndex,
                    faceUp,
                    presentation));
        }
    }

    private static int claimedIndex(Meld meld) {
        if (meld.claimedTile().isEmpty()) {
            return -1;
        }
        TileId claimed = meld.claimedTile().orElseThrow();
        for (int index = 0; index < meld.tiles().size(); index++) {
            if (meld.tiles().get(index).id().equals(claimed)) {
                return index;
            }
        }
        throw new IllegalStateException("open meld lost its claimed tile");
    }

    private static void addWallTile(
            RiichiProviderState provider,
            RiichiProjectionIds ids,
            TileInstance tile,
            boolean faceUp,
            boolean[] occupied,
            List<top.ellan.mahjong.spi.RuleViewTile> tiles) {
        long projection = ids.project(tile);
        int wallSlot = provider.wallSlot(projection);
        occupied[(int) projection] = true;
        tiles.add(node(
                ids,
                tile,
                RiichiViewZone.WALL,
                wallSlot,
                faceUp,
                top.ellan.mahjong.spi.RuleTilePresentation.natural(wallSlot)));
    }

    private static top.ellan.mahjong.spi.RuleViewTile hiddenWallNode(
            long projection, int wallSlot) {
        return new top.ellan.mahjong.spi.RuleViewTile(
                new top.ellan.mahjong.spi.TileInstanceId(projection),
                new top.ellan.mahjong.spi.TileVisualId("riichi:tile/back"),
                Optional.empty(),
                top.ellan.mahjong.spi.RuleViewZone.WALL,
                wallSlot,
                false,
                top.ellan.mahjong.spi.RuleTilePresentation.natural(wallSlot));
    }

    private static top.ellan.mahjong.spi.RuleViewTile node(
            RiichiProjectionIds ids,
            TileInstance tile,
            int seat,
            RiichiViewZone zone,
            int index,
            boolean faceUp,
            top.ellan.mahjong.spi.RuleTilePresentation presentation) {
        return new top.ellan.mahjong.spi.RuleViewTile(
                new top.ellan.mahjong.spi.TileInstanceId(ids.project(tile)),
                visual(tile, faceUp),
                Optional.of(new top.ellan.mahjong.spi.SeatId(seat)),
                spiZone(zone),
                index,
                faceUp,
                presentation);
    }

    private static top.ellan.mahjong.spi.RuleViewTile node(
            RiichiProjectionIds ids,
            TileInstance tile,
            RiichiViewZone zone,
            int index,
            boolean faceUp,
            top.ellan.mahjong.spi.RuleTilePresentation presentation) {
        return new top.ellan.mahjong.spi.RuleViewTile(
                new top.ellan.mahjong.spi.TileInstanceId(ids.project(tile)),
                visual(tile, faceUp),
                Optional.empty(),
                spiZone(zone),
                index,
                faceUp,
                presentation);
    }

    private static top.ellan.mahjong.spi.TileVisualId visual(
            TileInstance tile, boolean faceUp) {
        if (!faceUp) {
            return BACK;
        }
        int kind = tile.tile().kind().ordinal();
        return tile.tile().red() ? RED_FACE_VISUALS[kind] : FACE_VISUALS[kind];
    }

    private static String canonicalVisualName(String notation) {
        if (notation.length() == 2
                && notation.charAt(0) >= '1'
                && notation.charAt(0) <= '9'
                && (notation.charAt(1) == 'm'
                        || notation.charAt(1) == 'p'
                        || notation.charAt(1) == 's')) {
            return notation.substring(1, 2) + notation.substring(0, 1);
        }
        return switch (notation) {
            case "1z" -> "east";
            case "2z" -> "south";
            case "3z" -> "west";
            case "4z" -> "north";
            case "5z" -> "white_dragon";
            case "6z" -> "green_dragon";
            case "7z" -> "red_dragon";
            default -> throw new IllegalArgumentException(
                    "Unsupported Riichi tile notation: " + notation);
        };
    }

    private static top.ellan.mahjong.spi.TileVisualId[] createFaceVisuals(boolean red) {
        top.ellan.mahjong.rules.riichi.model.TileKind[] kinds =
                top.ellan.mahjong.rules.riichi.model.TileKind.values();
        top.ellan.mahjong.spi.TileVisualId[] result =
                new top.ellan.mahjong.spi.TileVisualId[kinds.length];
        for (top.ellan.mahjong.rules.riichi.model.TileKind kind : kinds) {
            result[kind.ordinal()] = new top.ellan.mahjong.spi.TileVisualId(
                    "riichi:tile/"
                            + canonicalVisualName(kind.notation())
                            + (red ? "_red" : ""));
        }
        return result;
    }

    private static void addPointSticks(
            RiichiMatchState match,
            Map<PlayerId, Integer> scores,
            List<top.ellan.mahjong.spi.RuleViewTile> tiles) {
        for (int seat = 0; seat < match.seats().size(); seat++) {
            int score = scores.getOrDefault(match.seats().get(seat), 0);
            if (score < 0) {
                continue;
            }
            if (score % 100 != 0) {
                throw new IllegalStateException("Riichi score cannot be represented by point sticks");
            }
            int remaining = score;
            int stickIndex = 0;
            for (int denomination : POINT_STICK_DENOMINATIONS) {
                int count = remaining / denomination;
                remaining %= denomination;
                for (int copy = 0; copy < count; copy++) {
                    if (stickIndex >= POINT_STICK_IDS_PER_SEAT) {
                        throw new IllegalStateException("Point-stick projection capacity exceeded");
                    }
                    tiles.add(new top.ellan.mahjong.spi.RuleViewTile(
                            new top.ellan.mahjong.spi.TileInstanceId(
                                    POINT_STICK_ID_BASE
                                            + (long) seat * POINT_STICK_IDS_PER_SEAT
                                            + stickIndex),
                            new top.ellan.mahjong.spi.TileVisualId(
                                    "riichi:stick/p" + denomination),
                            Optional.of(new top.ellan.mahjong.spi.SeatId(seat)),
                            top.ellan.mahjong.spi.RuleViewZone.POINT_STICK,
                            stickIndex,
                            true,
                            top.ellan.mahjong.spi.RuleTilePresentation.natural(stickIndex)));
                    stickIndex++;
                }
            }
        }
    }

    private static top.ellan.mahjong.spi.RuleViewZone spiZone(RiichiViewZone zone) {
        return switch (zone) {
            case WALL -> top.ellan.mahjong.spi.RuleViewZone.WALL;
            case HAND -> top.ellan.mahjong.spi.RuleViewZone.HAND;
            case DISCARD -> top.ellan.mahjong.spi.RuleViewZone.DISCARD;
            case MELD -> top.ellan.mahjong.spi.RuleViewZone.MELD;
            case INDICATOR -> top.ellan.mahjong.spi.RuleViewZone.INDICATOR;
            case AUXILIARY -> top.ellan.mahjong.spi.RuleViewZone.AUXILIARY;
        };
    }

    private static String csvSeatScores(RiichiMatchState match, Map<PlayerId, Integer> scores) {
        ArrayList<String> values = new ArrayList<>(4);
        for (PlayerId player : match.seats()) {
            values.add(Integer.toString(scores.getOrDefault(player, 0)));
        }
        return String.join(",", values);
    }

    private static String csvRanking(RiichiMatchState match) {
        ArrayList<String> values = new ArrayList<>(4);
        for (PlayerId player : match.ranking()) {
            values.add(Integer.toString(match.seats().indexOf(player)));
        }
        return String.join(",", values);
    }

    private static String csvSeatNumbers(RiichiMatchState match, Set<PlayerId> players) {
        ArrayList<String> values = new ArrayList<>(4);
        for (PlayerId player : match.seats()) {
            if (players.contains(player)) {
                values.add(Integer.toString(match.seats().indexOf(player)));
            }
        }
        return String.join(",", values);
    }
}

package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPhase;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RiichiRoundState;
import top.ellan.mahjong.rules.riichi.engine.RoundSnapshot;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
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

    public top.ellan.mahjong.spi.PublicRuleView publicView(
            RiichiMatchState match, long revision) {
        RiichiRoundState round = match.roundState();
        RoundSnapshot snapshot = round.publicSnapshot();
        RiichiProjectionIds ids = RiichiProjectionIds.forHand(match.currentHandSeed());
        ArrayList<top.ellan.mahjong.spi.RuleViewTile> tiles = new ArrayList<>();
        Set<PlayerId> winners = snapshot.winners();
        for (int seat = 0; seat < 4; seat++) {
            PlayerId player = match.seats().get(seat);
            List<TileInstance> hand = round.concealedHand(player);
            boolean winner = winners.contains(player);
            for (int index = 0; index < hand.size(); index++) {
                tiles.add(node(ids, hand.get(index), seat, RiichiViewZone.HAND, index, winner));
            }
        }
        for (int seat = 0; seat < 4; seat++) {
            PlayerId player = match.seats().get(seat);
            List<TileInstance> discards = snapshot.discards().getOrDefault(player, List.of());
            for (int index = 0; index < discards.size(); index++) {
                tiles.add(node(
                        ids, discards.get(index), seat, RiichiViewZone.DISCARD, index, true));
            }
            List<Meld> melds = snapshot.melds().getOrDefault(player, List.of());
            for (int meldIndex = 0; meldIndex < melds.size(); meldIndex++) {
                List<TileInstance> meldTiles = melds.get(meldIndex).tiles();
                for (int index = 0; index < meldTiles.size(); index++) {
                    tiles.add(node(
                            ids,
                            meldTiles.get(index),
                            seat,
                            RiichiViewZone.MELD,
                            meldIndex * 4 + index,
                            true));
                }
            }
        }
        List<TileInstance> wall = round.liveWall();
        for (int index = 0; index < wall.size(); index++) {
            tiles.add(node(ids, wall.get(index), RiichiViewZone.WALL, index, false));
        }
        List<TileInstance> rinshan = round.rinshan();
        for (int index = 0; index < rinshan.size(); index++) {
            tiles.add(node(
                    ids, rinshan.get(index), RiichiViewZone.WALL, wall.size() + index, false));
        }
        List<TileInstance> dora = round.revealedDoraIndicators();
        for (int index = 0; index < dora.size(); index++) {
            tiles.add(node(ids, dora.get(index), RiichiViewZone.INDICATOR, index, true));
        }
        addPointSticks(match, snapshot.scores(), tiles);

        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("profile", "mahjong-soul");
        attributes.put("matchPhase", match.phase().name());
        attributes.put("handNumber", Integer.toString(match.position().handNumber()));
        attributes.put("roundWind", match.position().roundWind().name());
        attributes.put("dealer", Integer.toString(match.position().dealerIndex()));
        attributes.put("roundPhase", snapshot.phase().name());
        attributes.put("currentPlayer", Integer.toString(snapshot.currentPlayer() == null
                ? -1 : match.seats().indexOf(snapshot.currentPlayer())));
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
        return new top.ellan.mahjong.spi.PublicRuleView(revision, phase, tiles, attributes);
    }

    public top.ellan.mahjong.spi.PrivateRuleView privateView(
            RiichiMatchState match,
            long revision,
            top.ellan.mahjong.spi.PlayerId viewer) {
        PlayerId domain = RiichiProviderState.toDomain(viewer);
        int seat = match.seats().indexOf(domain);
        if (seat < 0) {
            throw new IllegalArgumentException("viewer is not seated in this Riichi match");
        }
        RiichiProjectionIds ids = RiichiProjectionIds.forHand(match.currentHandSeed());
        ArrayList<top.ellan.mahjong.spi.RuleViewTile> tiles = new ArrayList<>();
        List<TileInstance> hand = match.roundState().concealedHand(domain);
        for (int index = 0; index < hand.size(); index++) {
            tiles.add(node(ids, hand.get(index), seat, RiichiViewZone.HAND, index, true));
        }
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put("profile", "mahjong-soul");
        attributes.put("initialSeat", Integer.toString(seat));
        attributes.put("handSize", Integer.toString(tiles.size()));
        return new top.ellan.mahjong.spi.PrivateRuleView(revision, viewer, tiles, attributes);
    }

    private static top.ellan.mahjong.spi.RuleViewTile node(
            RiichiProjectionIds ids,
            TileInstance tile,
            int seat,
            RiichiViewZone zone,
            int index,
            boolean faceUp) {
        return new top.ellan.mahjong.spi.RuleViewTile(
                new top.ellan.mahjong.spi.TileInstanceId(ids.project(tile)),
                visual(tile, faceUp),
                Optional.of(new top.ellan.mahjong.spi.SeatId(seat)),
                spiZone(zone),
                index,
                faceUp);
    }

    private static top.ellan.mahjong.spi.RuleViewTile node(
            RiichiProjectionIds ids,
            TileInstance tile,
            RiichiViewZone zone,
            int index,
            boolean faceUp) {
        return new top.ellan.mahjong.spi.RuleViewTile(
                new top.ellan.mahjong.spi.TileInstanceId(ids.project(tile)),
                visual(tile, faceUp),
                Optional.empty(),
                spiZone(zone),
                index,
                faceUp);
    }

    private static top.ellan.mahjong.spi.TileVisualId visual(TileInstance tile, boolean faceUp) {
        if (!faceUp) {
            return new top.ellan.mahjong.spi.TileVisualId("riichi:tile/back");
        }
        String kind = tile.tile().kind().notation();
        return new top.ellan.mahjong.spi.TileVisualId(
                tile.tile().red() ? "riichi:tile/" + kind + "r" : "riichi:tile/" + kind);
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
                            8 + stickIndex,
                            true));
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

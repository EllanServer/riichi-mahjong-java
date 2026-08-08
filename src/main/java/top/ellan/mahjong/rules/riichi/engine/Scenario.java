package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Immutable, deterministic integration and replay entrypoint. */
public record Scenario(
        RiichiRules rules,
        List<PlayerId> seatOrder,
        int dealerIndex,
        Wind roundWind,
        int honba,
        int riichiSticks,
        Map<PlayerId, Integer> scores,
        Map<PlayerId, List<TileInstance>> hands,
        Map<PlayerId, List<Meld>> melds,
        List<TileInstance> liveWall,
        List<TileInstance> rinshan,
        List<Tile> doraIndicatorSequence,
        List<Tile> uraDoraIndicatorSequence,
        int currentPlayerIndex,
        RoundPhase phase,
        Map<PlayerId, Integer> kanCounts) {

    public Scenario {
        Objects.requireNonNull(rules, "rules");
        seatOrder = List.copyOf(Objects.requireNonNull(seatOrder, "seatOrder"));
        Objects.requireNonNull(roundWind, "roundWind");
        scores = immutableMap(scores);
        hands = immutableLists(hands);
        melds = immutableLists(melds);
        liveWall = List.copyOf(Objects.requireNonNull(liveWall, "liveWall"));
        rinshan = List.copyOf(Objects.requireNonNull(rinshan, "rinshan"));
        doraIndicatorSequence = List.copyOf(Objects.requireNonNull(doraIndicatorSequence, "doraIndicatorSequence"));
        uraDoraIndicatorSequence = List.copyOf(Objects.requireNonNull(uraDoraIndicatorSequence, "uraDoraIndicatorSequence"));
        Objects.requireNonNull(phase, "phase");
        kanCounts = immutableMap(kanCounts);
        validate(rules, seatOrder, dealerIndex, currentPlayerIndex, honba, riichiSticks, phase, scores, hands, melds,
                liveWall, rinshan, kanCounts);
    }

    public static Scenario standard(RiichiRules rules, List<PlayerId> players, long seed) {
        LinkedHashMap<PlayerId, Integer> scores = new LinkedHashMap<>();
        players.forEach(player -> scores.put(player, rules.startingPoints()));
        return standard(rules, players, seed, 0, Wind.EAST, 0, 0, scores);
    }

    public static Scenario standard(
            RiichiRules rules,
            List<PlayerId> players,
            long seed,
            int dealerIndex,
            Wind roundWind,
            int honba,
            int riichiSticks,
            Map<PlayerId, Integer> currentScores) {
        List<PlayerId> seats = List.copyOf(players);
        if (seats.size() != 4 || seats.stream().distinct().count() != 4) {
            throw new IllegalArgumentException("standard Riichi requires four unique players");
        }
        if (dealerIndex < 0 || dealerIndex >= seats.size()) {
            throw new IllegalArgumentException("dealer index is outside the seat order");
        }
        Objects.requireNonNull(roundWind, "roundWind");
        Map<PlayerId, Integer> scores = Map.copyOf(Objects.requireNonNull(currentScores, "currentScores"));
        if (!scores.keySet().equals(Set.copyOf(seats))) {
            throw new IllegalArgumentException("current scores must exactly match the seats");
        }
        ArrayList<TileInstance> wall = standardWall(rules);
        Collections.shuffle(wall, new Random(seed));
        List<TileInstance> deadWall = new ArrayList<>(wall.subList(wall.size() - 14, wall.size()));
        wall.subList(wall.size() - 14, wall.size()).clear();

        LinkedHashMap<PlayerId, List<TileInstance>> hands = new LinkedHashMap<>();
        seats.forEach(player -> hands.put(player, new ArrayList<>()));
        for (int tile = 0; tile < 13; tile++) {
            for (PlayerId player : seats) {
                hands.get(player).add(wall.removeFirst());
            }
        }
        hands.get(seats.get(dealerIndex)).add(wall.removeFirst());
        LinkedHashMap<PlayerId, List<Meld>> melds = new LinkedHashMap<>();
        seats.forEach(player -> {
            melds.put(player, List.of());
        });
        List<Tile> doraSequence = List.of(8, 6, 4, 2, 0).stream().map(deadWall::get).map(TileInstance::tile).toList();
        List<Tile> uraSequence = List.of(9, 7, 5, 3, 1).stream().map(deadWall::get).map(TileInstance::tile).toList();
        return new Scenario(
                rules, seats, dealerIndex, roundWind, honba, riichiSticks, scores, hands, melds,
                wall, deadWall.subList(10, 14), doraSequence, uraSequence,
                dealerIndex, RoundPhase.AWAITING_DISCARD, Map.of());
    }

    public static Builder builder(List<PlayerId> seatOrder) {
        return new Builder(seatOrder);
    }

    private static ArrayList<TileInstance> standardWall(RiichiRules rules) {
        ArrayList<TileInstance> wall = new ArrayList<>(136);
        long id = 0;
        for (TileKind kind : TileKind.values()) {
            int redCopies = rules.redFives().copiesOf(kind);
            for (int copy = 0; copy < 4; copy++) {
                wall.add(new TileInstance(new TileId(id++), new Tile(kind, copy < redCopies)));
            }
        }
        return wall;
    }

    private static void validate(
            RiichiRules rules,
            List<PlayerId> seats,
            int dealer,
            int current,
            int honba,
            int sticks,
            RoundPhase phase,
            Map<PlayerId, Integer> scores,
            Map<PlayerId, List<TileInstance>> hands,
            Map<PlayerId, List<Meld>> melds,
            List<TileInstance> liveWall,
            List<TileInstance> rinshan,
            Map<PlayerId, Integer> kanCounts) {
        if (seats.size() != 4 || seats.stream().distinct().count() != 4) {
            throw new IllegalArgumentException("scenario requires four unique players");
        }
        if (dealer < 0 || dealer >= 4 || current < 0 || current >= 4) {
            throw new IllegalArgumentException("invalid seat index");
        }
        if (phase != RoundPhase.AWAITING_DRAW && phase != RoundPhase.AWAITING_DISCARD) {
            throw new IllegalArgumentException(
                    "phase-1 scenarios can start only at a draw or discard boundary");
        }
        if (honba < 0 || honba > Integer.MAX_VALUE / 300
                || sticks < 0 || sticks > Integer.MAX_VALUE / 1_000) {
            throw new IllegalArgumentException("round counters are negative or overflow settlement arithmetic");
        }
        Set<PlayerId> seatSet = Set.copyOf(seats);
        if (!scores.keySet().equals(seatSet) || !hands.keySet().equals(seatSet) || !melds.keySet().equals(seatSet)) {
            throw new IllegalArgumentException("score, hand, and meld maps must exactly match the seats");
        }
        if (!seatSet.containsAll(kanCounts.keySet()) || scores.values().stream().anyMatch(score -> score < 0)) {
            throw new IllegalArgumentException("invalid score or kan map");
        }
        HashSet<TileId> ids = new HashSet<>();
        EnumMap<TileKind, Integer> copies = new EnumMap<>(TileKind.class);
        EnumMap<TileKind, Integer> redCopies = new EnumMap<>(TileKind.class);
        hands.values().stream().flatMap(List::stream).forEach(tile -> recordPhysical(tile, ids, copies, redCopies));
        melds.values().stream().flatMap(List::stream).flatMap(meld -> meld.tiles().stream())
                .forEach(tile -> recordPhysical(tile, ids, copies, redCopies));
        liveWall.forEach(tile -> recordPhysical(tile, ids, copies, redCopies));
        rinshan.forEach(tile -> recordPhysical(tile, ids, copies, redCopies));
        copies.forEach((kind, count) -> {
            if (count > 4) throw new IllegalArgumentException("more than four copies of " + kind + " in scenario");
        });
        redCopies.forEach((kind, count) -> {
            if (count > rules.redFives().copiesOf(kind)) {
                throw new IllegalArgumentException("red-five supply exceeded for " + kind);
            }
        });
    }

    private static void recordPhysical(
            TileInstance tile,
            Set<TileId> ids,
            Map<TileKind, Integer> copies,
            Map<TileKind, Integer> redCopies) {
        if (!ids.add(tile.id())) throw new IllegalArgumentException("duplicate physical tile id: " + tile.id());
        copies.merge(tile.tile().kind(), 1, Integer::sum);
        if (tile.tile().red()) redCopies.merge(tile.tile().kind(), 1, Integer::sum);
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> source) {
        return Map.copyOf(Objects.requireNonNull(source, "source"));
    }

    private static <K, V> Map<K, List<V>> immutableLists(Map<K, List<V>> source) {
        LinkedHashMap<K, List<V>> result = new LinkedHashMap<>();
        Objects.requireNonNull(source, "source").forEach((key, value) -> result.put(key, List.copyOf(value)));
        return Map.copyOf(result);
    }

    public static final class Builder {
        private final List<PlayerId> seats;
        private RiichiRules rules = RiichiRules.mahjongSoul();
        private int dealer;
        private Wind roundWind = Wind.EAST;
        private int honba;
        private int riichiSticks;
        private final Map<PlayerId, Integer> scores = new LinkedHashMap<>();
        private final Set<PlayerId> explicitScores = new HashSet<>();
        private final Map<PlayerId, List<TileInstance>> hands = new LinkedHashMap<>();
        private final Map<PlayerId, List<Meld>> melds = new LinkedHashMap<>();
        private List<TileInstance> liveWall = List.of();
        private List<TileInstance> rinshan = List.of();
        private List<Tile> doraSequence = List.of();
        private List<Tile> uraSequence = List.of();
        private int current;
        private RoundPhase phase = RoundPhase.AWAITING_DRAW;
        private Map<PlayerId, Integer> kanCounts = Map.of();

        private Builder(List<PlayerId> seats) {
            this.seats = List.copyOf(seats);
            this.seats.forEach(player -> {
                scores.put(player, rules.startingPoints());
                hands.put(player, List.of());
                melds.put(player, List.of());
            });
        }

        public Builder rules(RiichiRules value) {
            rules = Objects.requireNonNull(value, "value");
            seats.stream().filter(player -> !explicitScores.contains(player))
                    .forEach(player -> scores.put(player, rules.startingPoints()));
            return this;
        }

        public Builder dealerIndex(int value) { dealer = value; return this; }
        public Builder roundWind(Wind value) { roundWind = value; return this; }
        public Builder honba(int value) { honba = value; return this; }
        public Builder riichiSticks(int value) { riichiSticks = value; return this; }
        public Builder currentPlayerIndex(int value) { current = value; return this; }
        public Builder phase(RoundPhase value) { phase = value; return this; }
        public Builder liveWall(List<TileInstance> value) { liveWall = List.copyOf(value); return this; }
        public Builder rinshan(List<TileInstance> value) { rinshan = List.copyOf(value); return this; }
        public Builder doraIndicatorSequence(List<Tile> value) { doraSequence = List.copyOf(value); return this; }
        public Builder uraDoraIndicatorSequence(List<Tile> value) { uraSequence = List.copyOf(value); return this; }
        public Builder kanCounts(Map<PlayerId, Integer> value) { kanCounts = Map.copyOf(value); return this; }

        public Builder score(PlayerId player, int value) {
            scores.put(player, value);
            explicitScores.add(player);
            return this;
        }
        public Builder hand(PlayerId player, List<TileInstance> value) { hands.put(player, List.copyOf(value)); return this; }
        public Builder melds(PlayerId player, List<Meld> value) { melds.put(player, List.copyOf(value)); return this; }

        public Scenario build() {
            return new Scenario(
                    rules, seats, dealer, roundWind, honba, riichiSticks, scores, hands, melds,
                    liveWall, rinshan, doraSequence, uraSequence, current, phase, kanCounts);
        }
    }
}

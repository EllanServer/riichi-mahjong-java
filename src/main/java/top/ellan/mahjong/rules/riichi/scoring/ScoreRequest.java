package top.ellan.mahjong.rules.riichi.scoring;

import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Complete scoring context. The winning tile appears in concealedTiles iff winningTileInHand is true. */
public record ScoreRequest(
        List<Tile> concealedTiles,
        List<Meld> melds,
        Tile winningTile,
        boolean winningTileInHand,
        WinMethod winMethod,
        Wind seatWind,
        Wind roundWind,
        boolean riichi,
        boolean doubleRiichi,
        boolean ippatsu,
        boolean chankan,
        boolean rinshan,
        boolean lastTile,
        boolean firstTurn,
        List<Tile> doraIndicators,
        List<Tile> uraDoraIndicators,
        RiichiRules rules) {

    public ScoreRequest {
        concealedTiles = List.copyOf(Objects.requireNonNull(concealedTiles, "concealedTiles"));
        melds = List.copyOf(Objects.requireNonNull(melds, "melds"));
        Objects.requireNonNull(winningTile, "winningTile");
        Objects.requireNonNull(winMethod, "winMethod");
        Objects.requireNonNull(seatWind, "seatWind");
        Objects.requireNonNull(roundWind, "roundWind");
        doraIndicators = List.copyOf(Objects.requireNonNull(doraIndicators, "doraIndicators"));
        uraDoraIndicators = List.copyOf(Objects.requireNonNull(uraDoraIndicators, "uraDoraIndicators"));
        Objects.requireNonNull(rules, "rules");
        if (riichi && doubleRiichi) {
            throw new IllegalArgumentException("riichi and double riichi are mutually exclusive");
        }
        if (ippatsu && !(riichi || doubleRiichi)) {
            throw new IllegalArgumentException("ippatsu requires riichi");
        }
        if (chankan && winMethod != WinMethod.RON) {
            throw new IllegalArgumentException("chankan requires ron");
        }
        if (rinshan && winMethod != WinMethod.TSUMO) {
            throw new IllegalArgumentException("rinshan requires tsumo");
        }
        if (chankan && rinshan) {
            throw new IllegalArgumentException("chankan and rinshan are mutually exclusive");
        }
        if ((riichi || doubleRiichi) && melds.stream().anyMatch(Meld::open)) {
            throw new IllegalArgumentException("riichi requires a closed hand");
        }
        if (firstTurn && winMethod != WinMethod.TSUMO) {
            throw new IllegalArgumentException("firstTurn represents tenhou/chiihou and requires tsumo");
        }
        if (firstTurn && (riichi || doubleRiichi || ippatsu || lastTile || rinshan)) {
            throw new IllegalArgumentException("first-turn win context is contradictory");
        }
        if (firstTurn && !melds.isEmpty()) {
            throw new IllegalArgumentException("tenhou/chiihou cannot have a declared meld");
        }
        if (melds.size() > 4) {
            throw new IllegalArgumentException("a hand cannot contain more than four melds");
        }
        if (doraIndicators.size() > 5 || uraDoraIndicators.size() > 5) {
            throw new IllegalArgumentException("at most five dora and ura-dora indicators can be visible");
        }
        int expectedFullConcealed = 14 - melds.size() * 3;
        int actualFullConcealed = concealedTiles.size() + (winningTileInHand ? 0 : 1);
        if (actualFullConcealed != expectedFullConcealed) {
            throw new IllegalArgumentException(
                    "invalid concealed tile count: expected " + expectedFullConcealed + ", got " + actualFullConcealed);
        }
        if (winningTileInHand && !concealedTiles.contains(winningTile)) {
            throw new IllegalArgumentException("winning tile is not present in concealedTiles");
        }
        validatePhysicalCopies(
                concealedTiles,
                melds,
                winningTile,
                winningTileInHand,
                doraIndicators,
                uraDoraIndicators,
                rules);
    }

    public List<Tile> fullConcealedTiles() {
        if (winningTileInHand) {
            return concealedTiles;
        }
        List<Tile> result = new ArrayList<>(concealedTiles.size() + 1);
        result.addAll(concealedTiles);
        result.add(winningTile);
        return List.copyOf(result);
    }

    public boolean closedHand() {
        return melds.stream().noneMatch(Meld::open);
    }

    private static void validatePhysicalCopies(
            List<Tile> concealed,
            List<Meld> melds,
            Tile winning,
            boolean winningInHand,
            List<Tile> doraIndicators,
            List<Tile> uraDoraIndicators,
            RiichiRules rules) {
        EnumMap<TileKind, Integer> counts = new EnumMap<>(TileKind.class);
        EnumMap<TileKind, Integer> redCounts = new EnumMap<>(TileKind.class);
        HashSet<top.ellan.mahjong.rules.riichi.model.TileId> meldTileIds = new HashSet<>();
        concealed.forEach(tile -> counts.merge(tile.kind(), 1, Integer::sum));
        concealed.stream().filter(Tile::red)
                .forEach(tile -> redCounts.merge(tile.kind(), 1, Integer::sum));
        for (Meld meld : melds) {
            meld.tiles().forEach(tile -> {
                if (!meldTileIds.add(tile.id())) {
                    throw new IllegalArgumentException("physical tile id appears in more than one meld: " + tile.id());
                }
                counts.merge(tile.tile().kind(), 1, Integer::sum);
                if (tile.tile().red()) redCounts.merge(tile.tile().kind(), 1, Integer::sum);
            });
        }
        if (!winningInHand) {
            counts.merge(winning.kind(), 1, Integer::sum);
            if (winning.red()) redCounts.merge(winning.kind(), 1, Integer::sum);
        }
        for (Tile indicator : doraIndicators) {
            counts.merge(indicator.kind(), 1, Integer::sum);
            if (indicator.red()) redCounts.merge(indicator.kind(), 1, Integer::sum);
        }
        for (Tile indicator : uraDoraIndicators) {
            counts.merge(indicator.kind(), 1, Integer::sum);
            if (indicator.red()) redCounts.merge(indicator.kind(), 1, Integer::sum);
        }
        counts.forEach((kind, count) -> {
            if (count > 4) {
                throw new IllegalArgumentException("more than four physical copies of " + kind);
            }
        });
        redCounts.forEach((kind, count) -> {
            int configured = rules.redFives().copiesOf(kind);
            if (count > configured) {
                throw new IllegalArgumentException(
                        "red-five supply exceeded for " + kind + ": configured " + configured + ", got " + count);
            }
        });
    }
}

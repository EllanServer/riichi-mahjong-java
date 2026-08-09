package top.ellan.mahjong.rules.riichi.internal;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.RiichiServices;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeHandEvaluatorCorpusTest {
    private static final int RANDOM_CASES = 8_000;
    private static final PlayerId SOURCE = new PlayerId("corpus-source");
    private static final AtomicLong TILE_IDS = new AtomicLong(10_000);
    private static final HandEvaluator EVALUATOR = RiichiServices.handEvaluator();

    @Test
    void representativeStandardSevenPairsAndKokushiShapes() {
        assertAnalysis("123m 123p 123s 789s 1z", List.of(), 0, Set.of(TileKind.EAST));
        assertAnalysis("1122m 3344p 5566s 7z", List.of(), 0, Set.of(TileKind.RED_DRAGON));
        assertAnalysis("19m 19p 19s 1234567z", List.of(), 0, terminalsAndHonors());

        assertEquals(-1, EVALUATOR.analyze(tiles("123m 123p 123s 789s 11z"), List.of()).shanten());
        assertEquals(-1, EVALUATOR.analyze(tiles("1122m 3344p 5566s 77z"), List.of()).shanten());
        assertEquals(-1, EVALUATOR.analyze(tiles("119m 19p 19s 1234567z"), List.of()).shanten());
    }

    @Test
    void supportsLegalOpenAndConcealedKanMelds() {
        List<Meld> melds = List.of(
                chii(TileKind.M1, TileKind.M2, TileKind.M3),
                ankan(TileKind.P9));
        HandAnalysis analysis = EVALUATOR.analyze(tiles("123s 789s 5z"), melds);
        assertEquals(0, analysis.shanten());
        assertEquals(Set.of(TileKind.WHITE_DRAGON), analysis.waits());
        assertEquals(-1, EVALUATOR.analyze(tiles("123s 789s 55z"), melds).shanten());

        HandAnalysis drawn = EVALUATOR.analyze(tiles("123s 78s 55z 9m"), melds);
        assertEquals(0, drawn.shanten());
        assertTrue(drawn.waits().isEmpty());
    }

    @Test
    void fourteenTileZeroShantenHandDoesNotExposeWaits() {
        HandAnalysis analysis = EVALUATOR.analyze(tiles("123m 123p 123s 78s 11z 9m"), List.of());
        assertEquals(0, analysis.shanten());
        assertTrue(analysis.waits().isEmpty());
    }

    @Test
    void rejectsPhysicalIdReusedAcrossMelds() {
        long duplicate = TILE_IDS.getAndIncrement();
        Meld first = ponWithIds(TileKind.M1, duplicate, nextId(), nextId());
        Meld second = ponWithIds(TileKind.P1, duplicate, nextId(), nextId());
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> EVALUATOR.analyze(tiles("123s 456s 7z"), List.of(first, second)));
        assertTrue(error.getMessage().contains("more than one meld"));
    }

    @Test
    void deterministicCorpusIsOrderInvariantAndEveryReportedWaitCompletes() {
        Random random = new Random(0x5249_4943_4849L);
        for (int index = 0; index < RANDOM_CASES; index++) {
            List<Tile> hand = randomHand(random, 13);
            HandAnalysis original = EVALUATOR.analyze(hand, List.of());
            ArrayList<Tile> shuffled = new ArrayList<>(hand);
            Collections.shuffle(shuffled, random);
            assertEquals(original, EVALUATOR.analyze(shuffled, List.of()));
            for (TileKind wait : original.waits()) {
                ArrayList<Tile> completed = new ArrayList<>(hand);
                completed.add(Tile.of(wait));
                assertTrue(EVALUATOR.analyze(completed, List.of()).complete(),
                        () -> "reported wait did not complete " + wait + " for " + hand);
            }
        }
    }

    private static void assertAnalysis(
            String notation, List<Meld> melds, int shanten, Set<TileKind> waits) {
        HandAnalysis analysis = EVALUATOR.analyze(tiles(notation), melds);
        assertEquals(shanten, analysis.shanten());
        assertEquals(waits, analysis.waits());
    }

    private static List<Tile> randomHand(Random random, int size) {
        ArrayList<Tile> wall = new ArrayList<>(136);
        for (TileKind kind : TileKind.values()) {
            for (int copy = 0; copy < 4; copy++) wall.add(Tile.of(kind));
        }
        Collections.shuffle(wall, random);
        return List.copyOf(wall.subList(0, size));
    }

    private static List<Tile> tiles(String notation) {
        ArrayList<Tile> result = new ArrayList<>();
        for (String group : notation.split(" ")) {
            char suit = group.charAt(group.length() - 1);
            for (int index = 0; index < group.length() - 1; index++) {
                result.add(Tile.parse(group.charAt(index) + Character.toString(suit)));
            }
        }
        return List.copyOf(result);
    }

    private static Meld chii(TileKind first, TileKind second, TileKind third) {
        List<TileInstance> meldTiles = List.of(instance(first), instance(second), instance(third));
        return new Meld(MeldType.CHII, meldTiles, Optional.of(SOURCE), Optional.of(meldTiles.getFirst().id()));
    }

    private static Meld ankan(TileKind kind) {
        return new Meld(
                MeldType.ANKAN,
                List.of(instance(kind), instance(kind), instance(kind), instance(kind)),
                Optional.empty(),
                Optional.empty());
    }

    private static Meld ponWithIds(TileKind kind, long first, long second, long third) {
        List<TileInstance> meldTiles = List.of(
                instance(first, kind), instance(second, kind), instance(third, kind));
        return new Meld(MeldType.PON, meldTiles, Optional.of(SOURCE), Optional.of(meldTiles.getFirst().id()));
    }

    private static TileInstance instance(TileKind kind) {
        return instance(nextId(), kind);
    }

    private static TileInstance instance(long id, TileKind kind) {
        return new TileInstance(new TileId(id), Tile.of(kind));
    }

    private static long nextId() {
        return TILE_IDS.getAndIncrement();
    }

    private static Set<TileKind> terminalsAndHonors() {
        return Set.of(
                TileKind.M1, TileKind.M9, TileKind.P1, TileKind.P9, TileKind.S1, TileKind.S9,
                TileKind.EAST, TileKind.SOUTH, TileKind.WEST, TileKind.NORTH,
                TileKind.WHITE_DRAGON, TileKind.GREEN_DRAGON, TileKind.RED_DRAGON);
    }
}

package top.ellan.mahjong.rules.riichi.benchmark;

import top.ellan.mahjong.rules.riichi.RiichiServices;
import top.ellan.mahjong.rules.riichi.engine.KanTracker;
import top.ellan.mahjong.rules.riichi.engine.Reaction;
import top.ellan.mahjong.rules.riichi.engine.ReactionOptions;
import top.ellan.mahjong.rules.riichi.engine.ReactionWindow;
import top.ellan.mahjong.rules.riichi.evaluation.HandAnalysis;
import top.ellan.mahjong.rules.riichi.evaluation.HandEvaluator;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.ScoreCalculator;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** JMH-free smoke benchmark for the native evaluator, native scorer and command core. */
public final class RiichiMicrobenchmark {
    private static volatile int blackhole;

    private RiichiMicrobenchmark() {
    }

    public static void main(String[] args) {
        HandEvaluator evaluator = RiichiServices.handEvaluator();
        ScoreRequest request = scoreRequest(baseHand());
        List<List<Tile>> nativeColdHands = randomHands(request.concealedTiles(), 1_024);
        int nativeHotOperations = 100_000;

        // Warm the native code with a key excluded from the cold corpus.
        for (int index = 0; index < 2_000; index++) {
            consume(evaluator.analyze(request.concealedTiles(), List.of()));
        }

        long nativeColdStart = System.nanoTime();
        for (List<Tile> hand : nativeColdHands) consume(evaluator.analyze(hand, List.of()));
        long nativeColdNanos = System.nanoTime() - nativeColdStart;

        long nativeHotStart = System.nanoTime();
        for (int index = 0; index < nativeHotOperations; index++) {
            consume(evaluator.analyze(request.concealedTiles(), List.of()));
        }
        long nativeHotNanos = System.nanoTime() - nativeHotStart;

        ScoreCalculator calculator = RiichiServices.scoreCalculator();
        List<List<Tile>> scoreHands = shuffledHands(request.concealedTiles(), 128);
        List<ScoreRequest> scoreVariants = scoreHands.stream()
                .map(RiichiMicrobenchmark::scoreRequest)
                .toList();
        int scoringHotOperations = 10_000;
        int reactionOperations = 10_000;

        // Initialize and JIT the native scorer with a key excluded from the cold corpus.
        for (int index = 0; index < 50; index++) consume(calculator.score(request));

        long scoringColdStart = System.nanoTime();
        for (ScoreRequest variant : scoreVariants) consume(calculator.score(variant));
        long scoringColdNanos = System.nanoTime() - scoringColdStart;

        long scoringHotStart = System.nanoTime();
        for (int index = 0; index < scoringHotOperations; index++) {
            consume(calculator.score(scoreVariants.get(index % scoreVariants.size())));
        }
        long scoringHotNanos = System.nanoTime() - scoringHotStart;

        for (int index = 0; index < 1_000; index++) consume(reactionRound());
        long reactionStart = System.nanoTime();
        for (int index = 0; index < reactionOperations; index++) consume(reactionRound());
        long reactionNanos = System.nanoTime() - reactionStart;

        System.out.printf(
                "native shanten cold (cache miss): %.1f ns/op (%d ops)%n"
                        + "native shanten hot (cache hit): %.1f ns/op (%d ops)%n"
                        + "native score cold (cache miss): %.1f ns/op (%d ops)%n"
                        + "native score hot (cache hit): %.1f ns/op (%d ops)%n"
                        + "reaction-window: %.1f ns/op (%d ops)%nblackhole=%d%n",
                nativeColdNanos / (double) nativeColdHands.size(),
                nativeColdHands.size(),
                nativeHotNanos / (double) nativeHotOperations,
                nativeHotOperations,
                scoringColdNanos / (double) scoreVariants.size(),
                scoreVariants.size(),
                scoringHotNanos / (double) scoringHotOperations,
                scoringHotOperations,
                reactionNanos / (double) reactionOperations,
                reactionOperations,
                blackhole);
    }

    private static int reactionRound() {
        PlayerId left = new PlayerId("left");
        PlayerId across = new PlayerId("across");
        ReactionWindow window = new ReactionWindow(
                List.of(left, across),
                Map.of(
                        left, new ReactionOptions(true, false, false, List.of()),
                        across, new ReactionOptions(true, false, false, List.of())),
                RiichiRules.RonMode.MULTI_RON,
                new KanTracker());
        window.submit(left, Reaction.ron());
        window.submit(across, Reaction.skip());
        return window.resolution().orElseThrow().ronWinners().size();
    }

    private static List<Tile> baseHand() {
        return Arrays.stream(new String[]{
                "2m", "3m", "4m", "3m", "4m", "5m", "4p", "5p", "6p",
                "6s", "7s", "8s", "6p"}).map(Tile::parse).toList();
    }

    private static ScoreRequest scoreRequest(List<Tile> hand) {
        return new ScoreRequest(
                hand,
                List.of(),
                Tile.of(TileKind.P6),
                false,
                WinMethod.RON,
                Wind.SOUTH,
                Wind.EAST,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                List.of(),
                List.of(),
                RiichiRules.mahjongSoul());
    }

    private static List<List<Tile>> shuffledHands(List<Tile> base, int count) {
        LinkedHashSet<List<Tile>> unique = new LinkedHashSet<>();
        unique.add(List.copyOf(base));
        Random random = new Random(0x5EED_2026L);
        while (unique.size() <= count) {
            ArrayList<Tile> shuffled = new ArrayList<>(base);
            Collections.shuffle(shuffled, random);
            unique.add(List.copyOf(shuffled));
        }
        unique.remove(base);
        return unique.stream().limit(count).toList();
    }

    private static List<List<Tile>> randomHands(List<Tile> excluded, int count) {
        ArrayList<Tile> wall = new ArrayList<>(136);
        for (TileKind kind : TileKind.values()) {
            for (int copy = 0; copy < 4; copy++) wall.add(Tile.of(kind));
        }
        LinkedHashSet<List<Tile>> unique = new LinkedHashSet<>();
        ArrayList<Tile> canonicalExcluded = new ArrayList<>(excluded);
        canonicalExcluded.sort(Tile::compareTo);
        unique.add(List.copyOf(canonicalExcluded));
        Random random = new Random(0x4e41_5449_5645L);
        while (unique.size() <= count) {
            Collections.shuffle(wall, random);
            ArrayList<Tile> hand = new ArrayList<>(wall.subList(0, 13));
            hand.sort(Tile::compareTo);
            unique.add(List.copyOf(hand));
        }
        unique.remove(canonicalExcluded);
        return List.copyOf(unique);
    }

    private static void consume(ScoreResult result) {
        blackhole ^= result.han() * 31 + result.fu();
    }

    private static void consume(HandAnalysis result) {
        blackhole ^= result.shanten() * 31 + result.waits().size();
    }

    private static void consume(int value) {
        blackhole ^= value;
    }
}

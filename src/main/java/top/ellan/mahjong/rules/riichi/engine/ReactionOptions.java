package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.TileId;

import java.util.List;
import java.util.Objects;

public record ReactionOptions(
        boolean ron,
        boolean pon,
        boolean minkan,
        List<List<TileId>> chiiChoices) {

    public ReactionOptions {
        chiiChoices = Objects.requireNonNull(chiiChoices, "chiiChoices").stream()
                .map(List::copyOf)
                .toList();
        if (chiiChoices.stream().anyMatch(choice -> choice.size() != 2 || choice.stream().distinct().count() != 2)) {
            throw new IllegalArgumentException("each chii choice must contain two distinct physical tile ids");
        }
    }

    public ReactionOptions withoutMinkanWhenFourKans(KanTracker tracker) {
        Objects.requireNonNull(tracker, "tracker");
        return new ReactionOptions(ron, pon, minkan && tracker.canOfferDiscardKan(), chiiChoices);
    }

    public boolean accepts(Reaction reaction) {
        return switch (reaction.type()) {
            case RON -> ron;
            case PON -> pon && reaction.consumedTiles().size() == 2;
            case MINKAN -> minkan && reaction.consumedTiles().size() == 3;
            case CHII -> chiiChoices.stream().anyMatch(choice -> samePhysicalTiles(choice, reaction.consumedTiles()));
            case SKIP -> true;
        };
    }

    public boolean empty() {
        return !ron && !pon && !minkan && chiiChoices.isEmpty();
    }

    private static boolean samePhysicalTiles(List<TileId> left, List<TileId> right) {
        return left.size() == right.size() && left.containsAll(right);
    }
}

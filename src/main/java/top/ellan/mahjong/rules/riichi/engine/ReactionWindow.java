package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Single-use reaction collection ordered nearest-to-farthest from the discarder. */
public final class ReactionWindow {
    public enum SubmitStatus { ACCEPTED, RESOLVED }

    private final List<PlayerId> priorityOrder;
    private final Map<PlayerId, ReactionOptions> options;
    private final Map<PlayerId, Reaction> responses = new LinkedHashMap<>();
    private final RiichiRules.RonMode ronMode;
    private ReactionResolution resolution;

    public ReactionWindow(
            List<PlayerId> priorityOrder,
            Map<PlayerId, ReactionOptions> rawOptions,
            RiichiRules.RonMode ronMode,
            KanTracker kanTracker) {
        this.priorityOrder = List.copyOf(Objects.requireNonNull(priorityOrder, "priorityOrder"));
        Objects.requireNonNull(rawOptions, "rawOptions");
        this.ronMode = Objects.requireNonNull(ronMode, "ronMode");
        Objects.requireNonNull(kanTracker, "kanTracker");
        if (this.priorityOrder.stream().distinct().count() != this.priorityOrder.size()) {
            throw new IllegalArgumentException("priority order contains duplicate players");
        }
        LinkedHashMap<PlayerId, ReactionOptions> sanitized = new LinkedHashMap<>();
        for (PlayerId player : this.priorityOrder) {
            ReactionOptions candidate = rawOptions.get(player);
            if (candidate != null) {
                candidate = candidate.withoutMinkanWhenFourKans(kanTracker);
                if (!candidate.empty()) sanitized.put(player, candidate);
            }
        }
        this.options = Map.copyOf(sanitized);
        if (this.options.isEmpty()) {
            resolution = ReactionResolution.noClaim();
        }
    }

    private ReactionWindow(ReactionWindow source) {
        priorityOrder = source.priorityOrder;
        options = source.options;
        ronMode = source.ronMode;
        responses.putAll(source.responses);
        resolution = source.resolution;
    }

    ReactionWindow copy() {
        return new ReactionWindow(this);
    }

    public SubmitStatus submit(PlayerId player, Reaction reaction) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reaction, "reaction");
        if (resolution != null) {
            throw new RuleViolationException(RuleViolation.REACTION_WINDOW_CLOSED, "reaction window is closed");
        }
        ReactionOptions playerOptions = options.get(player);
        if (playerOptions == null) {
            throw new RuleViolationException(RuleViolation.REACTION_NOT_AVAILABLE, "player has no reaction");
        }
        if (responses.containsKey(player)) {
            throw new RuleViolationException(
                    RuleViolation.REACTION_ALREADY_SUBMITTED,
                    "a player cannot replace an earlier reaction");
        }
        if (!playerOptions.accepts(reaction)) {
            throw new RuleViolationException(RuleViolation.INVALID_REACTION_PAYLOAD, "reaction is not legal");
        }
        responses.put(player, reaction);
        if (responses.size() == options.size()) {
            resolution = resolveResponses();
            return SubmitStatus.RESOLVED;
        }
        return SubmitStatus.ACCEPTED;
    }

    public Map<PlayerId, ReactionOptions> options() {
        return options;
    }

    public Map<PlayerId, Reaction> responses() {
        return Map.copyOf(responses);
    }

    /** True once {@code player} has answered, so the window must stop offering it actions. */
    public boolean hasResponded(PlayerId player) {
        return responses.containsKey(Objects.requireNonNull(player, "player"));
    }

    public Optional<ReactionResolution> resolution() {
        return Optional.ofNullable(resolution);
    }

    private ReactionResolution resolveResponses() {
        List<PlayerId> ronPlayers = priorityOrder.stream()
                .filter(options::containsKey)
                .filter(player -> responses.get(player).type() == ReactionType.RON)
                .toList();
        if (!ronPlayers.isEmpty()) {
            List<PlayerId> winners = ronMode == RiichiRules.RonMode.HEAD_BUMP
                    ? List.of(ronPlayers.getFirst()) : ronPlayers;
            return new ReactionResolution(winners, Optional.empty(), Optional.empty());
        }
        for (PlayerId player : priorityOrder) {
            if (!options.containsKey(player)) continue;
            Reaction reaction = responses.get(player);
            if (reaction.type() == ReactionType.PON || reaction.type() == ReactionType.MINKAN) {
                return new ReactionResolution(List.of(), Optional.of(player), Optional.of(reaction));
            }
        }
        for (PlayerId player : priorityOrder) {
            if (!options.containsKey(player)) continue;
            Reaction reaction = responses.get(player);
            if (reaction.type() == ReactionType.CHII) {
                return new ReactionResolution(List.of(), Optional.of(player), Optional.of(reaction));
            }
        }
        return ReactionResolution.noClaim();
    }
}

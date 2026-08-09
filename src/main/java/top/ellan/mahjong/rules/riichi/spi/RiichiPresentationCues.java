package top.ellan.mahjong.rules.riichi.spi;

import java.util.LinkedHashSet;
import java.util.List;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchEvent;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchTransition;
import top.ellan.mahjong.rules.riichi.engine.RoundEvent;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.spi.RulePresentationCue;
import top.ellan.mahjong.spi.RulePresentationCueType;

/** Converts typed Riichi engine facts into bounded transient presentation semantics. */
final class RiichiPresentationCues {
    private RiichiPresentationCues() {}

    static List<RulePresentationCue> from(
            RiichiProviderState next, RiichiMatchTransition transition) {
        LinkedHashSet<RulePresentationCue> cues = new LinkedHashSet<>();
        for (RiichiMatchEvent event : transition.matchEvents()) {
            if (event.type() == RiichiMatchEvent.Type.ROUND_STARTED) {
                cues.add(broadcast(RulePresentationCueType.TILE_SHUFFLE));
            }
        }
        for (RoundEvent event : transition.roundEvents()) {
            addRoundEvent(next, event, cues);
        }
        return List.copyOf(cues);
    }

    private static void addRoundEvent(
            RiichiProviderState next,
            RoundEvent event,
            LinkedHashSet<RulePresentationCue> cues) {
        switch (event.type()) {
            case DRAWN, RINSHAN_DRAWN -> {
                cues.add(broadcast(RulePresentationCueType.TILE_DRAW));
                event.player().ifPresent(player -> cues.add(RulePresentationCue.toPlayer(
                        RulePresentationCueType.TURN_CHANGE,
                        spiPlayer(next, player))));
            }
            case DISCARDED -> cues.add(broadcast(RulePresentationCueType.TILE_DISCARD));
            case RIICHI_DECLARED -> cues.add(broadcast(RulePresentationCueType.RIICHI));
            case MELD_DECLARED -> addMeldCue(event.detail(), cues);
            case KAN_REGISTERED -> cues.add(broadcast(RulePresentationCueType.REACTION_KAN));
            case ROUND_ENDED -> cues.add(broadcast(isWin(event.detail())
                    ? RulePresentationCueType.ROUND_WIN
                    : RulePresentationCueType.ROUND_DRAW));
            case REACTION_OPENED, REACTION_SUBMITTED, KAN_DECLARED -> {
                // A declaration sound is emitted only when the call becomes effective.
            }
        }
    }

    private static void addMeldCue(
            String detail, LinkedHashSet<RulePresentationCue> cues) {
        if ("CHII".equals(detail)) {
            cues.add(broadcast(RulePresentationCueType.REACTION_CHI));
        } else if ("PON".equals(detail)) {
            cues.add(broadcast(RulePresentationCueType.REACTION_PON));
        }
    }

    private static boolean isWin(String detail) {
        return "TSUMO".equals(detail) || "RON".equals(detail) || "CHANKAN".equals(detail);
    }

    private static top.ellan.mahjong.spi.PlayerId spiPlayer(
            RiichiProviderState state, PlayerId domainPlayer) {
        int initialSeat = state.match().seats().indexOf(domainPlayer);
        if (initialSeat < 0) {
            throw new IllegalStateException("Riichi cue references an unseated player");
        }
        return state.player(initialSeat);
    }

    private static RulePresentationCue broadcast(RulePresentationCueType type) {
        return RulePresentationCue.broadcast(type);
    }
}

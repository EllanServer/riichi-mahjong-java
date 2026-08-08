package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;

import java.util.Objects;

/** The only intended behavioral difference between the two phase-1 profiles. */
public final class KanDoraPolicy {
    private KanDoraPolicy() {
    }

    public static boolean revealBeforeRinshan(RiichiRules.Profile profile, MeldType type) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(type, "type");
        if (!type.isKan()) {
            throw new IllegalArgumentException("not a kan: " + type);
        }
        return type == MeldType.ANKAN || profile == RiichiRules.Profile.EARLY_KAN_DORA;
    }

    public static boolean revealBeforeNextDiscard(RiichiRules.Profile profile, MeldType type) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(type, "type");
        return profile == RiichiRules.Profile.MAJSOUL
                && (type == MeldType.MINKAN || type == MeldType.KAKAN);
    }
}

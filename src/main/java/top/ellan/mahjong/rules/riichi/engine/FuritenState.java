package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.util.LinkedHashSet;
import java.util.Set;

/** Explicit missed-ron state; evaluator failures never mutate furiten. */
public final class FuritenState {
    record Snapshot(Set<TileKind> ownDiscards, boolean temporary, boolean riichiPermanent) {
    }

    private final Set<TileKind> ownDiscards = new LinkedHashSet<>();
    private boolean temporary;
    private boolean riichiPermanent;

    public void recordOwnDiscard(TileKind tile) {
        ownDiscards.add(tile);
    }

    public void missLegalRon(boolean inRiichi) {
        if (inRiichi) {
            riichiPermanent = true;
        } else {
            temporary = true;
        }
    }

    public void onOwnDraw() {
        temporary = false;
    }

    public boolean isFuriten(Set<TileKind> currentWaits) {
        return temporary || riichiPermanent || currentWaits.stream().anyMatch(ownDiscards::contains);
    }

    public Set<TileKind> ownDiscards() {
        return Set.copyOf(ownDiscards);
    }

    public boolean temporary() {
        return temporary;
    }

    public boolean riichiPermanent() {
        return riichiPermanent;
    }

    Snapshot snapshot() {
        return new Snapshot(Set.copyOf(ownDiscards), temporary, riichiPermanent);
    }

    void restore(Snapshot snapshot) {
        ownDiscards.clear();
        ownDiscards.addAll(snapshot.ownDiscards());
        temporary = snapshot.temporary();
        riichiPermanent = snapshot.riichiPermanent();
    }
}

package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.RiichiMatchEvent;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchTransition;
import top.ellan.mahjong.rules.riichi.engine.RoundEvent;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Canonical event summaries for the SPI boundary. */
public final class RiichiSpiEvents {
    private RiichiSpiEvents() {}

    public static List<top.ellan.mahjong.spi.RuleEvent> encode(
            RiichiMatchTransition transition, RiichiMatchState source) {
        ArrayList<top.ellan.mahjong.spi.RuleEvent> events = new ArrayList<>();
        RiichiProjectionIds ids = RiichiProjectionIds.forHand(source.currentHandSeed());
        for (RoundEvent event : transition.roundEvents()) {
            events.add(roundEvent(source, ids, event));
        }
        for (RiichiMatchEvent event : transition.matchEvents()) {
            events.add(matchEvent(event));
        }
        return List.copyOf(events);
    }

    private static top.ellan.mahjong.spi.RuleEvent roundEvent(
            RiichiMatchState source, RiichiProjectionIds ids, RoundEvent event) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        try {
            out.writeByte(event.type().ordinal());
            PlayerId player = event.player().orElse(null);
            out.writeByte(player == null ? 0xff : source.seats().indexOf(player));
            TileInstance tile = event.tile().orElse(null);
            out.writeByte(tile == null ? 0xff : (int) ids.project(tile));
            out.writeUTF(event.detail());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return new top.ellan.mahjong.spi.RuleEvent(
                "round." + event.type().name().toLowerCase(),
                buffer.toByteArray());
    }

    private static top.ellan.mahjong.spi.RuleEvent matchEvent(RiichiMatchEvent event) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        try {
            out.writeByte(event.type().ordinal());
            out.writeUTF(event.detail());
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return new top.ellan.mahjong.spi.RuleEvent(
                "match." + event.type().name().toLowerCase(),
                buffer.toByteArray());
    }
}

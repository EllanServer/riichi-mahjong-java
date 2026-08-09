package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RoundCommand;
import top.ellan.mahjong.rules.riichi.engine.Reaction;
import top.ellan.mahjong.rules.riichi.engine.ReactionType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compact SPI mapping whose player-originated payloads contain only opaque projection IDs. */
public final class RiichiSpiActions {
    private RiichiSpiActions() {}

    public static top.ellan.mahjong.spi.LegalAction encode(
            RiichiProjectionIds ids,
            PlayerId actor,
            RoundCommand command) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        if (command instanceof RoundCommand.Draw) {
            return new top.ellan.mahjong.spi.LegalAction(
                    "draw",
                    new top.ellan.mahjong.spi.RuleAction("draw", new byte[0]),
                    top.ellan.mahjong.spi.ActionPresentation.actionRow("action.draw"));
        }
        if (command instanceof RoundCommand.Discard discard) {
            long projection = ids.project(discard.tile());
            byte[] payload = new byte[] {(byte) projection, (byte) (discard.declareRiichi() ? 1 : 0)};
            String key = discard.declareRiichi()
                    ? "discard_riichi:" + projection
                    : "discard:" + projection;
            return new top.ellan.mahjong.spi.LegalAction(
                    key,
                    new top.ellan.mahjong.spi.RuleAction("discard", payload),
                    discard.declareRiichi()
                            ? top.ellan.mahjong.spi.ActionPresentation
                                    .secondaryRow("action.discard_riichi")
                                    .withEmphasis()
                            : top.ellan.mahjong.spi.ActionPresentation.handTile(
                                    "action.discard",
                                    new top.ellan.mahjong.spi.TileInstanceId(projection)));
        }
        if (command instanceof RoundCommand.Respond respond) {
            Reaction reaction = respond.reaction();
            int[] projections = reaction.consumedTiles().stream()
                    .mapToInt(tile -> Math.toIntExact(ids.project(tile)))
                    .toArray();
            byte[] payload = new byte[1 + projections.length];
            payload[0] = (byte) reaction.type().ordinal();
            for (int index = 0; index < projections.length; index++) {
                payload[1 + index] = (byte) projections[index];
            }
            String key = switch (reaction.type()) {
                case RON -> "ron";
                case PON -> "pon";
                case MINKAN -> "minkan";
                case CHII -> "chii";
                case SKIP -> "skip";
            };
            top.ellan.mahjong.spi.ActionPresentation presentation =
                    top.ellan.mahjong.spi.ActionPresentation.actionRow("action." + key);
            if (reaction.type() == ReactionType.RON) {
                presentation = presentation.withEmphasis();
            }
            return new top.ellan.mahjong.spi.LegalAction(
                    key,
                    new top.ellan.mahjong.spi.RuleAction("respond", payload),
                    presentation);
        }
        if (command instanceof RoundCommand.DeclareSelfKan kan) {
            byte[] payload = new byte[] {(byte) kan.kind().ordinal()};
            return new top.ellan.mahjong.spi.LegalAction(
                    "declare_self_kan:" + kan.kind().notation(),
                    new top.ellan.mahjong.spi.RuleAction("declare_self_kan", payload),
                    top.ellan.mahjong.spi.ActionPresentation.actionRow(
                            "action.declare_self_kan"));
        }
        if (command instanceof RoundCommand.DeclareNineTerminals) {
            return new top.ellan.mahjong.spi.LegalAction(
                    "declare_nine_terminals",
                    new top.ellan.mahjong.spi.RuleAction("declare_nine_terminals", new byte[0]),
                    top.ellan.mahjong.spi.ActionPresentation.secondaryRow(
                            "action.declare_nine_terminals"));
        }
        if (command instanceof RoundCommand.DeclareTsumo) {
            return new top.ellan.mahjong.spi.LegalAction(
                    "declare_tsumo",
                    new top.ellan.mahjong.spi.RuleAction("declare_tsumo", new byte[0]),
                    top.ellan.mahjong.spi.ActionPresentation
                            .actionRow("action.declare_tsumo")
                            .withEmphasis());
        }
        throw new IllegalArgumentException("unsupported Riichi round command");
    }

    public static RoundCommand decode(
            RiichiMatchState state,
            RiichiProjectionIds ids,
            PlayerId actor,
            top.ellan.mahjong.spi.RuleAction action) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        byte[] payload = action.payload();
        return switch (action.type()) {
            case "draw" -> {
                requireEmpty(payload);
                yield new RoundCommand.Draw(actor);
            }
            case "discard" -> decodeDiscard(state, ids, actor, payload);
            case "respond" -> decodeRespond(state, ids, actor, payload);
            case "declare_self_kan" -> decodeSelfKan(actor, payload);
            case "declare_nine_terminals" -> {
                requireEmpty(payload);
                yield new RoundCommand.DeclareNineTerminals(actor);
            }
            case "declare_tsumo" -> {
                requireEmpty(payload);
                yield new RoundCommand.DeclareTsumo(actor);
            }
            default -> throw new IllegalArgumentException("unsupported Riichi action type");
        };
    }

    private static RoundCommand decodeDiscard(
            RiichiMatchState state,
            RiichiProjectionIds ids,
            PlayerId actor,
            byte[] payload) {
        if (payload.length != 2) {
            throw new IllegalArgumentException("discard payload must be [projection, riichi]");
        }
        TileId tile = ids.resolve(Byte.toUnsignedInt(payload[0]));
        boolean riichi = payload[1] != 0;
        requireInHand(state, actor, tile);
        return new RoundCommand.Discard(actor, tile, riichi);
    }

    private static RoundCommand decodeRespond(
            RiichiMatchState state,
            RiichiProjectionIds ids,
            PlayerId actor,
            byte[] payload) {
        if (payload.length < 1) {
            throw new IllegalArgumentException("respond payload requires a reaction type");
        }
        int typeOrdinal = Byte.toUnsignedInt(payload[0]);
        ReactionType[] types = ReactionType.values();
        if (typeOrdinal >= types.length) {
            throw new IllegalArgumentException("invalid reaction type ordinal");
        }
        ReactionType type = types[typeOrdinal];
        int expected = switch (type) {
            case RON, SKIP -> 0;
            case PON, CHII -> 2;
            case MINKAN -> 3;
        };
        if (payload.length != 1 + expected) {
            throw new IllegalArgumentException("invalid response tile count");
        }
        ArrayList<TileId> tiles = new ArrayList<>(expected);
        for (int index = 0; index < expected; index++) {
            TileId tile = ids.resolve(Byte.toUnsignedInt(payload[1 + index]));
            requireInHand(state, actor, tile);
            tiles.add(tile);
        }
        return new RoundCommand.Respond(actor, new Reaction(type, tiles));
    }

    private static RoundCommand decodeSelfKan(PlayerId actor, byte[] payload) {
        if (payload.length != 1) {
            throw new IllegalArgumentException("self-kan payload requires a tile kind");
        }
        int kindOrdinal = Byte.toUnsignedInt(payload[0]);
        TileKind[] kinds = TileKind.values();
        if (kindOrdinal >= kinds.length) {
            throw new IllegalArgumentException("invalid tile kind ordinal");
        }
        return new RoundCommand.DeclareSelfKan(actor, kinds[kindOrdinal]);
    }

    private static void requireEmpty(byte[] payload) {
        if (payload.length != 0) {
            throw new IllegalArgumentException("payload must be empty");
        }
    }

    private static void requireInHand(RiichiMatchState state, PlayerId actor, TileId tile) {
        boolean inHand = state.roundState().concealedHand(actor).stream()
                .anyMatch(instance -> instance.id().equals(tile));
        if (!inHand) {
            throw new IllegalArgumentException("tile is not in the actor's hand");
        }
    }
}

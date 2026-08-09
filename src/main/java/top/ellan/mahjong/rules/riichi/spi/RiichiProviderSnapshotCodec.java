package top.ellan.mahjong.rules.riichi.spi;

import top.ellan.mahjong.rules.riichi.engine.Reaction;
import top.ellan.mahjong.rules.riichi.engine.ReactionType;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPhase;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPosition;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchRules;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RiichiRoundEngine;
import top.ellan.mahjong.rules.riichi.engine.RiichiRoundState;
import top.ellan.mahjong.rules.riichi.engine.RoundCommand;
import top.ellan.mahjong.rules.riichi.engine.RoundPhase;
import top.ellan.mahjong.rules.riichi.engine.Scenario;
import top.ellan.mahjong.rules.riichi.model.Meld;
import top.ellan.mahjong.rules.riichi.model.MeldType;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.RedFiveConfiguration;
import top.ellan.mahjong.rules.riichi.model.RiichiRules;
import top.ellan.mahjong.rules.riichi.model.Tile;
import top.ellan.mahjong.rules.riichi.model.TileId;
import top.ellan.mahjong.rules.riichi.model.TileInstance;
import top.ellan.mahjong.rules.riichi.model.TileKind;
import top.ellan.mahjong.rules.riichi.model.Wind;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Deterministic binary snapshot format for Riichi matches. */
public final class RiichiProviderSnapshotCodec {
    private static final int MAGIC = 0x52494348; // "RICH"
    private static final int VERSION = 1;
    private static final int SCHEMA_VERSION = 1;

    public top.ellan.mahjong.spi.RuleStateSnapshot snapshot(
            RiichiProviderState state, long sequence) {
        byte[] payload = encode(state);
        return new top.ellan.mahjong.spi.RuleStateSnapshot(
                SCHEMA_VERSION, sequence, payload, sha256Hex(payload));
    }

    public RiichiProviderState restore(top.ellan.mahjong.spi.RuleStateSnapshot snapshot) {
        byte[] payload = snapshot.payload();
        if (!sha256Hex(payload).equals(snapshot.sha256())) {
            throw new IllegalArgumentException("snapshot digest mismatch");
        }
        return decode(payload);
    }

    private byte[] encode(RiichiProviderState state) {
        RiichiMatchState match = state.match();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(buffer);
        try {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            for (int seat = 0; seat < 4; seat++) {
                writePlayerId(out, state.player(seat));
            }
            writeMatch(out, match);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
        return buffer.toByteArray();
    }

    private RiichiProviderState decode(byte[] payload) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IllegalArgumentException("not a Riichi snapshot");
            }
            int version = in.readInt();
            if (version != VERSION) {
                throw new IllegalArgumentException("unsupported Riichi snapshot version");
            }
            ArrayList<top.ellan.mahjong.spi.PlayerId> players = new ArrayList<>(4);
            for (int seat = 0; seat < 4; seat++) {
                players.add(readPlayerId(in));
            }
            List<PlayerId> seats = players.stream()
                    .map(RiichiProviderState::toDomain)
                    .toList();
            RiichiMatchState match = readMatch(in, seats);
            return new RiichiProviderState(match, players);
        } catch (EOFException truncated) {
            throw new IllegalArgumentException("truncated Riichi snapshot", truncated);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void writeMatch(DataOutputStream out, RiichiMatchState match) throws IOException {
        out.writeLong(match.revision());
        writeRules(out, match.rules());
        out.writeLong(match.rootSeed());
        out.writeLong(match.handSerial());
        writePosition(out, match.position());
        writeScores(out, match.seats(), match.scores());
        out.writeByte(match.phase().ordinal());
        out.writeByte(match.endReason().isPresent() ? 1 : 0);
        if (match.endReason().isPresent()) {
            out.writeUTF(match.endReason().get());
        }
        RiichiRoundState round = match.roundState();
        out.writeLong(round.revision());
        if (round.history().size() != round.revision()) {
            throw new IllegalArgumentException("round revision disagrees with its command history");
        }
        Scenario scenario = round.scenario();
        if (!scenario.rules().equals(match.rules().roundRules())) {
            throw new IllegalArgumentException("scenario rules disagree with match rules");
        }
        if (!scenario.seatOrder().equals(match.seats())) {
            throw new IllegalArgumentException("scenario seats disagree with match seats");
        }
        writeScenario(out, scenario);
        writeHistory(out, scenario.seatOrder(), round.history());
    }

    private RiichiMatchState readMatch(DataInputStream in, List<PlayerId> seats)
            throws IOException {
        long revision = in.readLong();
        RiichiMatchRules rules = readRules(in);
        long rootSeed = in.readLong();
        long handSerial = in.readLong();
        RiichiMatchPosition position = readPosition(in);
        Map<PlayerId, Integer> scores = readScores(in, seats);
        RiichiMatchPhase phase = enumAt(RiichiMatchPhase.values(), in.readUnsignedByte());
        Optional<String> endReason = in.readUnsignedByte() != 0
                ? Optional.of(in.readUTF())
                : Optional.empty();
        long roundRevision = in.readLong();
        Scenario scenario = readScenario(in, rules.roundRules(), seats);
        List<RoundCommand> history = readHistory(in, seats);
        if (roundRevision != history.size()) {
            throw new IllegalArgumentException("round revision disagrees with its command history");
        }
        RiichiMatchState restored = restoreRound(
                revision,
                rules,
                seats,
                rootSeed,
                handSerial,
                position,
                scores,
                phase,
                scenario,
                history,
                endReason);
        return restored;
    }

    private RiichiMatchState restoreRound(
            long revision,
            RiichiMatchRules rules,
            List<PlayerId> seats,
            long rootSeed,
            long handSerial,
            RiichiMatchPosition position,
            Map<PlayerId, Integer> scores,
            RiichiMatchPhase phase,
            Scenario scenario,
            List<RoundCommand> history,
            Optional<String> endReason) {
        RiichiRoundState round = RiichiRoundState.start(
                scenario,
                top.ellan.mahjong.rules.riichi.RiichiServices.handEvaluator(),
                top.ellan.mahjong.rules.riichi.RiichiServices.scoreCalculator());
        RiichiRoundEngine engine = new RiichiRoundEngine();
        for (RoundCommand command : history) {
            var transition = engine.apply(round, command);
            if (!transition.accepted()) {
                throw new IllegalArgumentException(
                        "snapshot replay diverged: " + transition.message());
            }
            round = transition.state();
        }
        return RiichiMatchState.restored(
                revision,
                rules,
                seats,
                rootSeed,
                handSerial,
                position,
                scores,
                phase,
                round,
                endReason,
                top.ellan.mahjong.rules.riichi.RiichiServices.handEvaluator(),
                top.ellan.mahjong.rules.riichi.RiichiServices.scoreCalculator());
    }

    private void writeRules(DataOutputStream out, RiichiMatchRules matchRules)
            throws IOException {
        RiichiRules rules = matchRules.roundRules();
        out.writeByte(rules.profile().ordinal());
        out.writeByte(rules.ronMode().ordinal());
        out.writeInt(rules.startingPoints());
        out.writeInt(rules.targetPoints());
        out.writeInt(rules.minimumYakuHan());
        RedFiveConfiguration redFives = rules.redFives();
        out.writeByte(redFives.man());
        out.writeByte(redFives.pin());
        out.writeByte(redFives.sou());
        out.writeByte(rules.openTanyao() ? 1 : 0);
        out.writeByte(rules.kiriageMangan() ? 1 : 0);
        out.writeByte(rules.kazoeYakuman() ? 1 : 0);
        out.writeByte(rules.multipleYakuman() ? 1 : 0);
        out.writeByte(rules.complexYakuman() ? 1 : 0);
        out.writeByte(matchRules.scheduledLastWind().ordinal());
        out.writeByte(matchRules.maximumWind().ordinal());
        out.writeByte(matchRules.bustEndsMatch() ? 1 : 0);
    }

    private RiichiMatchRules readRules(DataInputStream in) throws IOException {
        RiichiRules.Profile profile = enumAt(RiichiRules.Profile.values(), in.readUnsignedByte());
        RiichiRules.RonMode ronMode = enumAt(RiichiRules.RonMode.values(), in.readUnsignedByte());
        int startingPoints = in.readInt();
        int targetPoints = in.readInt();
        int minimumYakuHan = in.readInt();
        RedFiveConfiguration redFives = new RedFiveConfiguration(
                in.readUnsignedByte(), in.readUnsignedByte(), in.readUnsignedByte());
        boolean openTanyao = in.readUnsignedByte() != 0;
        boolean kiriageMangan = in.readUnsignedByte() != 0;
        boolean kazoeYakuman = in.readUnsignedByte() != 0;
        boolean multipleYakuman = in.readUnsignedByte() != 0;
        boolean complexYakuman = in.readUnsignedByte() != 0;
        RiichiRules rules = new RiichiRules(
                profile,
                ronMode,
                startingPoints,
                targetPoints,
                minimumYakuHan,
                redFives,
                openTanyao,
                kiriageMangan,
                kazoeYakuman,
                multipleYakuman,
                complexYakuman);
        Wind scheduledLastWind = enumAt(Wind.values(), in.readUnsignedByte());
        Wind maximumWind = enumAt(Wind.values(), in.readUnsignedByte());
        boolean bustEndsMatch = in.readUnsignedByte() != 0;
        return new RiichiMatchRules(rules, scheduledLastWind, maximumWind, bustEndsMatch);
    }

    private void writePosition(DataOutputStream out, RiichiMatchPosition position)
            throws IOException {
        out.writeByte(position.roundWind().ordinal());
        out.writeInt(position.handNumber());
        out.writeInt(position.dealerIndex());
        out.writeInt(position.honba());
        out.writeInt(position.riichiSticks());
    }

    private RiichiMatchPosition readPosition(DataInputStream in) throws IOException {
        Wind roundWind = enumAt(Wind.values(), in.readUnsignedByte());
        int handNumber = in.readInt();
        int dealerIndex = in.readInt();
        int honba = in.readInt();
        int riichiSticks = in.readInt();
        return new RiichiMatchPosition(roundWind, handNumber, dealerIndex, honba, riichiSticks);
    }

    private void writeScenario(DataOutputStream out, Scenario scenario) throws IOException {
        out.writeInt(scenario.dealerIndex());
        out.writeByte(scenario.roundWind().ordinal());
        out.writeInt(scenario.honba());
        out.writeInt(scenario.riichiSticks());
        writeScores(out, scenario.seatOrder(), scenario.scores());
        for (PlayerId player : scenario.seatOrder()) {
            writeTiles(out, scenario.hands().getOrDefault(player, List.of()));
        }
        for (PlayerId player : scenario.seatOrder()) {
            writeMelds(out, scenario.seatOrder(), scenario.melds().getOrDefault(player, List.of()));
        }
        writeTiles(out, scenario.liveWall());
        writeTiles(out, scenario.rinshan());
        writeTiles(out, scenario.doraIndicatorSequence());
        writeTiles(out, scenario.uraDoraIndicatorSequence());
        out.writeInt(scenario.currentPlayerIndex());
        out.writeByte(scenario.phase().ordinal());
        for (PlayerId player : scenario.seatOrder()) {
            out.writeInt(scenario.kanCounts().getOrDefault(player, 0));
        }
    }

    private Scenario readScenario(
            DataInputStream in, RiichiRules rules, List<PlayerId> seatOrder) throws IOException {
        int dealerIndex = in.readInt();
        Wind roundWind = enumAt(Wind.values(), in.readUnsignedByte());
        int honba = in.readInt();
        int riichiSticks = in.readInt();
        Map<PlayerId, Integer> scores = readScores(in, seatOrder);
        LinkedHashMap<PlayerId, List<TileInstance>> hands = new LinkedHashMap<>();
        for (PlayerId player : seatOrder) {
            hands.put(player, readTiles(in));
        }
        LinkedHashMap<PlayerId, List<Meld>> melds = new LinkedHashMap<>();
        for (PlayerId player : seatOrder) {
            melds.put(player, readMelds(in, seatOrder));
        }
        List<TileInstance> liveWall = readTiles(in);
        List<TileInstance> rinshan = readTiles(in);
        List<TileInstance> dora = readTiles(in);
        List<TileInstance> uraDora = readTiles(in);
        int currentPlayerIndex = in.readInt();
        RoundPhase phase = enumAt(RoundPhase.values(), in.readUnsignedByte());
        LinkedHashMap<PlayerId, Integer> kanCounts = new LinkedHashMap<>();
        for (PlayerId player : seatOrder) {
            kanCounts.put(player, in.readInt());
        }
        return new Scenario(
                rules,
                seatOrder,
                dealerIndex,
                roundWind,
                honba,
                riichiSticks,
                scores,
                hands,
                melds,
                liveWall,
                rinshan,
                dora,
                uraDora,
                currentPlayerIndex,
                phase,
                kanCounts);
    }

    private void writeHistory(
            DataOutputStream out, List<PlayerId> seats, List<RoundCommand> history)
            throws IOException {
        out.writeInt(history.size());
        for (RoundCommand command : history) {
            writeCommand(out, seats, command);
        }
    }

    private List<RoundCommand> readHistory(DataInputStream in, List<PlayerId> seats)
            throws IOException {
        int count = in.readInt();
        ArrayList<RoundCommand> history = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            history.add(readCommand(in, seats));
        }
        return List.copyOf(history);
    }

    private void writeCommand(
            DataOutputStream out, List<PlayerId> seats, RoundCommand command) throws IOException {
        int seat = seats.indexOf(command.player());
        if (seat < 0) {
            throw new IllegalArgumentException("command references an unseated player");
        }
        if (command instanceof RoundCommand.Draw) {
            out.writeByte(0);
            out.writeByte(seat);
        } else if (command instanceof RoundCommand.Discard discard) {
            out.writeByte(1);
            out.writeByte(seat);
            out.writeLong(discard.tile().value());
            out.writeByte(discard.declareRiichi() ? 1 : 0);
        } else if (command instanceof RoundCommand.Respond respond) {
            out.writeByte(2);
            out.writeByte(seat);
            out.writeByte(respond.reaction().type().ordinal());
            out.writeInt(respond.reaction().consumedTiles().size());
            for (TileId tile : respond.reaction().consumedTiles()) {
                out.writeLong(tile.value());
            }
        } else if (command instanceof RoundCommand.DeclareSelfKan kan) {
            out.writeByte(3);
            out.writeByte(seat);
            out.writeByte(kan.kind().ordinal());
        } else if (command instanceof RoundCommand.DeclareNineTerminals) {
            out.writeByte(4);
            out.writeByte(seat);
        } else if (command instanceof RoundCommand.DeclareTsumo) {
            out.writeByte(5);
            out.writeByte(seat);
        } else {
            throw new IllegalArgumentException("unsupported Riichi round command");
        }
    }

    private RoundCommand readCommand(DataInputStream in, List<PlayerId> seats)
            throws IOException {
        int type = in.readUnsignedByte();
        int seat = in.readUnsignedByte();
        if (seat >= seats.size()) {
            throw new IllegalArgumentException("command references an unseated player");
        }
        PlayerId actor = seats.get(seat);
        return switch (type) {
            case 0 -> new RoundCommand.Draw(actor);
            case 1 -> new RoundCommand.Discard(
                    actor, new TileId(in.readLong()), in.readUnsignedByte() != 0);
            case 2 -> {
                ReactionType reactionType = enumAt(
                        ReactionType.values(), in.readUnsignedByte());
                int count = in.readInt();
                ArrayList<TileId> tiles = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    tiles.add(new TileId(in.readLong()));
                }
                yield new RoundCommand.Respond(actor, new Reaction(reactionType, tiles));
            }
            case 3 -> new RoundCommand.DeclareSelfKan(
                    actor, enumAt(TileKind.values(), in.readUnsignedByte()));
            case 4 -> new RoundCommand.DeclareNineTerminals(actor);
            case 5 -> new RoundCommand.DeclareTsumo(actor);
            default -> throw new IllegalArgumentException("unsupported command type: " + type);
        };
    }

    private void writeMelds(
            DataOutputStream out, List<PlayerId> seats, List<Meld> melds) throws IOException {
        out.writeInt(melds.size());
        for (Meld meld : melds) {
            out.writeByte(meld.type().ordinal());
            writeTiles(out, meld.tiles());
            PlayerId claimedFrom = meld.claimedFrom().orElse(null);
            out.writeByte(claimedFrom == null ? 0xff : seats.indexOf(claimedFrom));
            out.writeLong(meld.claimedTile().map(TileId::value).orElse(-1L));
        }
    }

    private List<Meld> readMelds(DataInputStream in, List<PlayerId> seats) throws IOException {
        int count = in.readInt();
        ArrayList<Meld> melds = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            MeldType type = enumAt(MeldType.values(), in.readUnsignedByte());
            List<TileInstance> tiles = readTiles(in);
            int claimedSeat = in.readUnsignedByte();
            PlayerId claimedFrom = claimedSeat == 0xff ? null : seats.get(claimedSeat);
            long claimedTile = in.readLong();
            melds.add(new Meld(
                    type,
                    tiles,
                    Optional.ofNullable(claimedFrom),
                    claimedTile < 0 ? Optional.empty() : Optional.of(new TileId(claimedTile))));
        }
        return List.copyOf(melds);
    }

    private void writeTiles(DataOutputStream out, List<TileInstance> tiles) throws IOException {
        out.writeInt(tiles.size());
        for (TileInstance tile : tiles) {
            writeTile(out, tile);
        }
    }

    private List<TileInstance> readTiles(DataInputStream in) throws IOException {
        int count = in.readInt();
        ArrayList<TileInstance> tiles = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            tiles.add(readTile(in));
        }
        return List.copyOf(tiles);
    }

    private void writeTile(DataOutputStream out, TileInstance tile) throws IOException {
        out.writeLong(tile.id().value());
        out.writeByte(tile.tile().kind().ordinal());
        out.writeByte(tile.tile().red() ? 1 : 0);
    }

    private TileInstance readTile(DataInputStream in) throws IOException {
        long id = in.readLong();
        TileKind kind = enumAt(TileKind.values(), in.readUnsignedByte());
        boolean red = in.readUnsignedByte() != 0;
        return new TileInstance(new TileId(id), new Tile(kind, red));
    }

    private void writeScores(
            DataOutputStream out, List<PlayerId> seats, Map<PlayerId, Integer> scores)
            throws IOException {
        for (PlayerId player : seats) {
            out.writeInt(scores.getOrDefault(player, 0));
        }
    }

    private Map<PlayerId, Integer> readScores(DataInputStream in, List<PlayerId> seats)
            throws IOException {
        LinkedHashMap<PlayerId, Integer> scores = new LinkedHashMap<>();
        for (PlayerId player : seats) {
            scores.put(player, in.readInt());
        }
        return scores;
    }

    private void writePlayerId(
            DataOutputStream out, top.ellan.mahjong.spi.PlayerId player) throws IOException {
        out.writeLong(player.value().getMostSignificantBits());
        out.writeLong(player.value().getLeastSignificantBits());
    }

    private top.ellan.mahjong.spi.PlayerId readPlayerId(DataInputStream in) throws IOException {
        long most = in.readLong();
        long least = in.readLong();
        return new top.ellan.mahjong.spi.PlayerId(new UUID(most, least));
    }

    private static <E extends Enum<E>> E enumAt(E[] values, int ordinal) {
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid enum ordinal");
        }
        return values[ordinal];
    }

    private static String sha256Hex(byte[] payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK has no SHA-256", impossible);
        }
    }
}

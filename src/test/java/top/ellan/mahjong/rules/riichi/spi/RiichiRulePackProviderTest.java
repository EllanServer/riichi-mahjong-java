package top.ellan.mahjong.rules.riichi.spi;

import org.junit.jupiter.api.Test;
import top.ellan.mahjong.rules.riichi.RiichiServices;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPhase;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPosition;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchRules;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.engine.RiichiRoundState;
import top.ellan.mahjong.rules.riichi.engine.Scenario;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Wind;
import top.ellan.mahjong.spi.LegalAction;
import top.ellan.mahjong.spi.MatchPlayer;
import top.ellan.mahjong.spi.MatchSeed;
import top.ellan.mahjong.spi.MatchSetup;
import top.ellan.mahjong.spi.PrivateRuleView;
import top.ellan.mahjong.spi.PublicRuleView;
import top.ellan.mahjong.spi.RuleAction;
import top.ellan.mahjong.spi.RulePackProvider;
import top.ellan.mahjong.spi.RuleState;
import top.ellan.mahjong.spi.RuleStateSnapshot;
import top.ellan.mahjong.spi.RuleTransition;
import top.ellan.mahjong.spi.RuleViewZone;
import top.ellan.mahjong.spi.SeatId;
import top.ellan.mahjong.spi.TransitionDisposition;
import top.ellan.mahjong.tck.RulePackTck;
import top.ellan.mahjong.tck.RulePackTckCase;
import top.ellan.mahjong.tck.RulePackTckReport;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiichiRulePackProviderTest {
    private final RiichiRulePackProvider provider = new RiichiRulePackProvider();
    private final List<top.ellan.mahjong.spi.MatchPlayer> players = players();

    @Test
    void officialProviderPassesTheReusableRulePackTck() {
        MatchSetup setup = setup();
        RulePackTckReport report = RulePackTck.verify(
                provider,
                new RulePackTckCase(
                        setup,
                        Map.of(players.getFirst().playerId(),
                                new RuleAction("discard", new byte[] {(byte) 0xff}))));

        assertEquals(4, report.playersVerified());
        assertTrue(report.legalActionsVerified() >= 14);
        assertEquals(1, report.rejectedActionsVerified());
        assertEquals(3, report.snapshotsVerified());
    }

    @Test
    void serviceLoaderDescriptorAndResourcesExposeOneOfficialPack() throws Exception {
        List<RulePackProvider> providers = ServiceLoader.load(RulePackProvider.class)
                .stream()
                .map(ServiceLoader.Provider::get)
                .toList();
        assertEquals(1, providers.size());
        assertTrue(providers.getFirst() instanceof RiichiRulePackProvider);
        assertEquals(RiichiRulePackProvider.RULE_ID, provider.descriptor().ruleId());
        assertEquals(RiichiRulePackProvider.PROFILE_ID,
                provider.descriptor().profiles().getFirst().id());
        assertEquals(1, provider.descriptor().stateSchemaVersion());
        assertEquals(Set.of("assets/riichi/tile-visuals.properties"),
                provider.descriptor().requiredResources());

        Properties manifest = new Properties();
        try (InputStream input = RiichiRulePackProvider.class.getClassLoader()
                .getResourceAsStream("META-INF/mahjong-rule-pack.properties")) {
            assertTrue(input != null);
            manifest.load(input);
        }
        assertEquals(provider.descriptor().ruleId().value(), manifest.getProperty("id"));
        assertEquals(provider.descriptor().version(), manifest.getProperty("version"));
        assertEquals(provider.descriptor().spiVersion(), manifest.getProperty("spiVersion"));
        assertEquals(provider.descriptor().requiredCoreVersion(),
                manifest.getProperty("requiredCoreVersion"));
        assertEquals(Integer.toString(provider.descriptor().stateSchemaVersion()),
                manifest.getProperty("stateSchemaVersion"));
        assertEquals("assets/riichi/tile-visuals.properties",
                manifest.getProperty("requiredResources"));
        assertTrue(RiichiRulePackProvider.class.getClassLoader().getResource(
                "assets/riichi/tile-visuals.properties") != null);
    }

    @Test
    void publicAndPrivateViewsNeverPublishAnotherPlayersConcealedFaces() {
        RuleState state = provider.createMatch(setup());
        PublicRuleView publicView = provider.publicView(state, 7);
        assertEquals(7, publicView.stateRevision());
        assertTrue(publicView.tiles().size() >= 120);
        assertEquals(publicView.tiles().size(), new HashSet<>(publicView.tiles().stream()
                .map(tile -> tile.instanceId().value()).toList()).size());
        assertEquals(List.of(17, 17, 17, 17),
                publicView.tablePresentation().wall().stackCountsBySide());
        assertEquals(136, publicView.tablePresentation().wall().tileCapacity());
        assertTrue(publicView.tiles().stream()
                .filter(tile -> tile.zone() == RuleViewZone.WALL
                        || tile.zone() == RuleViewZone.HAND)
                .allMatch(tile -> !tile.faceUp()
                        && tile.visualId().value().equals("riichi:tile/back")));
        assertEquals(12, publicView.tiles().stream()
                .filter(tile -> tile.zone() == RuleViewZone.POINT_STICK)
                .count());
        assertTrue(publicView.tiles().stream()
                .filter(tile -> tile.zone() == RuleViewZone.POINT_STICK)
                .allMatch(tile -> tile.faceUp()
                        && tile.owner().isPresent()
                        && tile.visualId().value().matches(
                                "riichi:stick/p(?:100|1000|5000|10000)")));

        top.ellan.mahjong.spi.PlayerId east = players.getFirst().playerId();
        PrivateRuleView privateView = provider.privateView(state, east, 7);
        assertEquals(east, privateView.viewer());
        assertEquals(new SeatId(0), privateView.seat());
        assertEquals(14, privateView.tiles().size());
        assertTrue(privateView.tiles().stream().allMatch(tile -> tile.faceUp()
                && tile.zone() == RuleViewZone.HAND
                && tile.owner().orElseThrow().equals(new SeatId(0))
                && !tile.visualId().value().equals("riichi:tile/back")));
        assertThrows(IllegalArgumentException.class, () -> provider.privateView(
                state,
                new top.ellan.mahjong.spi.PlayerId(
                        UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")),
                7));
    }

    @Test
    void generatedActionsCarryOpaqueIdsAndForgedProjectionIsRejected() {
        RuleState state = provider.createMatch(setup());
        top.ellan.mahjong.spi.PlayerId east = players.getFirst().playerId();
        List<LegalAction> discards = provider.legalActions(state, east).stream()
                .filter(action -> action.action().type().equals("discard"))
                .toList();
        assertTrue(discards.size() >= 13);
        for (LegalAction discard : discards) {
            byte[] payload = discard.action().payload();
            assertEquals(2, payload.length);
            assertTrue(discard.key().startsWith("discard:"));
            if (payload[1] == 0) {
                assertEquals(top.ellan.mahjong.spi.ActionPlacement.HAND_TILE,
                        discard.actionPresentation().placement());
                assertEquals(Byte.toUnsignedInt(payload[0]),
                        discard.actionPresentation().targetTile().orElseThrow().value());
            } else {
                assertEquals(top.ellan.mahjong.spi.ActionPlacement.SECONDARY_ROW,
                        discard.actionPresentation().placement());
            }
        }

        RuleTransition forged = provider.transition(
                state, east, new RuleAction("discard", new byte[] {(byte) 0xff}));
        assertEquals(TransitionDisposition.REJECTED, forged.disposition());
        assertSame(state, forged.nextState());
        assertEquals("invalid_action_payload", forged.reasonCode());
    }

    @Test
    void concealedKanUsesTwoOuterBacksAndTwoVisibleCenters() {
        assertFalse(RiichiViewProjector.meldTileFaceUp(
                top.ellan.mahjong.rules.riichi.model.MeldType.ANKAN,
                top.ellan.mahjong.rules.riichi.engine.RoundPhase.AWAITING_DRAW,
                0,
                4));
        assertTrue(RiichiViewProjector.meldTileFaceUp(
                top.ellan.mahjong.rules.riichi.model.MeldType.ANKAN,
                top.ellan.mahjong.rules.riichi.engine.RoundPhase.AWAITING_DRAW,
                1,
                4));
        assertTrue(RiichiViewProjector.meldTileFaceUp(
                top.ellan.mahjong.rules.riichi.model.MeldType.ANKAN,
                top.ellan.mahjong.rules.riichi.engine.RoundPhase.AWAITING_DRAW,
                2,
                4));
        assertFalse(RiichiViewProjector.meldTileFaceUp(
                top.ellan.mahjong.rules.riichi.model.MeldType.ANKAN,
                top.ellan.mahjong.rules.riichi.engine.RoundPhase.AWAITING_DRAW,
                3,
                4));
    }

    @Test
    void addedKanStacksOnAThreePositionPonWithoutLeavingAGap() {
        top.ellan.mahjong.spi.RuleTilePresentation claimed =
                RiichiViewProjector.meldPresentation(
                        top.ellan.mahjong.rules.riichi.model.MeldType.KAKAN,
                        4,
                        0,
                        0,
                        1,
                        true,
                        false,
                        0);
        top.ellan.mahjong.spi.RuleTilePresentation ordinary0 =
                RiichiViewProjector.meldPresentation(
                        top.ellan.mahjong.rules.riichi.model.MeldType.KAKAN,
                        4,
                        0,
                        0,
                        1,
                        false,
                        false,
                        0);
        top.ellan.mahjong.spi.RuleTilePresentation ordinary1 =
                RiichiViewProjector.meldPresentation(
                        top.ellan.mahjong.rules.riichi.model.MeldType.KAKAN,
                        4,
                        0,
                        0,
                        1,
                        false,
                        false,
                        1);
        top.ellan.mahjong.spi.RuleTilePresentation added =
                RiichiViewProjector.meldPresentation(
                        top.ellan.mahjong.rules.riichi.model.MeldType.KAKAN,
                        4,
                        0,
                        0,
                        1,
                        false,
                        true,
                        0);
        assertEquals(2, claimed.layoutIndex());
        assertEquals(List.of(0, 1), List.of(ordinary0.layoutIndex(), ordinary1.layoutIndex()));
        assertEquals(claimed.layoutIndex(), added.layoutIndex());
        assertEquals(1, added.stackLevel());
    }

    @Test
    void providerSnapshotAuthenticatesPlayerAssignmentsAndRestoresAuthorization() {
        RuleState state = provider.createMatch(setup());
        RuleStateSnapshot snapshot = provider.snapshot(state, 23);
        RuleState restored = provider.restore(snapshot);
        assertEquals(provider.stateHash(state), provider.stateHash(restored));
        for (MatchPlayer player : players) {
            assertEquals(
                    provider.privateView(state, player.playerId(), 0),
                    provider.privateView(restored, player.playerId(), 0));
        }

        byte[] corrupted = snapshot.payload();
        corrupted[8] ^= 1;
        RuleStateSnapshot invalid = new RuleStateSnapshot(
                snapshot.schemaVersion(), snapshot.sequence(), corrupted, snapshot.sha256());
        assertThrows(IllegalArgumentException.class, () -> provider.restore(invalid));
    }

    @Test
    void snapshotRestoreReplaysCommandsAndPreservesTransitionalState() {
        RuleState state = provider.createMatch(setup());
        top.ellan.mahjong.spi.PlayerId east = players.getFirst().playerId();
        LegalAction discard = provider.legalActions(state, east).stream()
                .filter(action -> action.action().type().equals("discard"))
                .findFirst()
                .orElseThrow();
        RuleTransition advanced = provider.transition(state, east, discard.action());
        assertTrue(advanced.accepted());

        RuleStateSnapshot snapshot = provider.snapshot(advanced.nextState(), 1);
        RuleState restored = provider.restore(snapshot);
        assertEquals(
                provider.stateHash(advanced.nextState()),
                provider.stateHash(restored));
        assertEquals(
                provider.legalActions(advanced.nextState(), east),
                provider.legalActions(restored, east));
    }

    @Test
    void onlyTheIncomingDealerCanAdvanceACompletedHand() {
        RiichiProviderState boundary = boundaryProviderState();
        top.ellan.mahjong.spi.PlayerId east = players.getFirst().playerId();
        top.ellan.mahjong.spi.PlayerId south = players.get(1).playerId();
        assertTrue(provider.legalActions(boundary, east).isEmpty());
        LegalAction start = provider.legalActions(boundary, south).getFirst();
        assertEquals("start_next_hand", start.key());

        RuleTransition transition = provider.transition(boundary, south, start.action());
        assertTrue(transition.accepted());
        RiichiProviderState next = (RiichiProviderState) transition.nextState();
        assertEquals(RiichiMatchPhase.ACTIVE_ROUND, next.match().phase());
        assertEquals(2, next.match().position().handNumber());
        assertEquals("1", provider.publicView(next, 1).attributes().get("dealer"));
        assertTrue(provider.legalActions(next, south).stream()
                .filter(action -> action.action().type().equals("discard"))
                .count() >= 13);
    }

    @Test
    void unsupportedProfilesPlayerCountsAndConfigurationFailClosed() {
        MatchSetup wrongProfile = new MatchSetup(
                new top.ellan.mahjong.spi.ProfileId("local-house-rule"),
                new MatchSeed(1, 2),
                players,
                Map.of());
        assertThrows(IllegalArgumentException.class, () -> provider.createMatch(wrongProfile));

        MatchSetup threePlayers = new MatchSetup(
                RiichiRulePackProvider.PROFILE_ID,
                new MatchSeed(1, 2),
                players.subList(0, 3),
                Map.of());
        assertThrows(IllegalArgumentException.class, () -> provider.createMatch(threePlayers));
        MatchSetup configured = new MatchSetup(
                RiichiRulePackProvider.PROFILE_ID,
                new MatchSeed(1, 2),
                players,
                Map.of("sanyuanFaan", "6"));
        assertThrows(IllegalArgumentException.class, () -> provider.createMatch(configured));
    }

    private RiichiProviderState boundaryProviderState() {
        RiichiMatchRules rules = RiichiMatchRules.mahjongSoulHanchan();
        ArrayList<PlayerId> seats = new ArrayList<>(4);
        for (top.ellan.mahjong.spi.MatchPlayer player : players) {
            seats.add(RiichiProviderState.toDomain(player.playerId()));
        }
        long rootSeed = 42L;
        long handSerial = 1L;
        RiichiMatchPosition position = new RiichiMatchPosition(Wind.SOUTH, 2, 1, 0, 0);
        LinkedHashMap<PlayerId, Integer> scores = new LinkedHashMap<>();
        seats.forEach(player -> scores.put(player, 25_000));
        Scenario scenario = Scenario.standard(
                rules.roundRules(), seats, 99L, 1, Wind.SOUTH, 0, 0, scores);
        RiichiRoundState round = RiichiRoundState.start(
                scenario,
                RiichiServices.handEvaluator(),
                RiichiServices.scoreCalculator());
        RiichiMatchState boundary = RiichiMatchState.restored(
                1,
                rules,
                seats,
                rootSeed,
                handSerial,
                position,
                scores,
                RiichiMatchPhase.BETWEEN_ROUNDS,
                round,
                Optional.empty(),
                RiichiServices.handEvaluator(),
                RiichiServices.scoreCalculator());
        ArrayList<top.ellan.mahjong.spi.PlayerId> spiPlayers = new ArrayList<>(4);
        for (top.ellan.mahjong.spi.MatchPlayer player : players) {
            spiPlayers.add(player.playerId());
        }
        return new RiichiProviderState(boundary, spiPlayers);
    }

    private MatchSetup setup() {
        return new MatchSetup(
                RiichiRulePackProvider.PROFILE_ID,
                new MatchSeed(0x0123_4567_89ab_cdefL, 0xfedc_ba98_7654_3210L),
                players,
                Map.of());
    }

    private static List<top.ellan.mahjong.spi.MatchPlayer> players() {
        ArrayList<top.ellan.mahjong.spi.MatchPlayer> result = new ArrayList<>(4);
        for (int index = 0; index < 4; index++) {
            result.add(new top.ellan.mahjong.spi.MatchPlayer(
                    new top.ellan.mahjong.spi.PlayerId(new UUID(0, index + 1L)),
                    new SeatId(index)));
        }
        return List.copyOf(result);
    }
}

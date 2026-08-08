package top.ellan.mahjong.rules.riichi;

import org.junit.jupiter.api.Test;
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
import top.ellan.mahjong.rules.riichi.scoring.Limit;
import top.ellan.mahjong.rules.riichi.scoring.ScoreRequest;
import top.ellan.mahjong.rules.riichi.scoring.ScoreResult;
import top.ellan.mahjong.rules.riichi.scoring.WinMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreContextValidationTest {
    private static final AtomicLong IDS = new AtomicLong();
    private static final List<PlayerId> SEATS = List.of(
            new PlayerId("a"), new PlayerId("b"), new PlayerId("c"), new PlayerId("d"));
    private static final List<Tile> PINFU_TANYAO = TestFixtures.tiles(
            "2m", "3m", "4m", "3m", "4m", "5m", "4p", "5p", "6p",
            "6s", "7s", "8s", "6p");

    @Test
    void redSupplyIsValidatedPerSuitRatherThanByAmbiguousTotal() {
        List<Tile> oneRed = TestFixtures.tiles(
                "2m", "3m", "4m", "3m", "4m", "5m", "4p", "0p", "6p",
                "6s", "7s", "8s", "6p");
        assertThrows(IllegalArgumentException.class,
                () -> request(oneRed, Tile.of(TileKind.P6), rules(RedFiveConfiguration.none(), false, true)));

        List<Tile> twoRedPin = TestFixtures.tiles(
                "0p", "0p", "2m", "3m", "4m", "3m", "4m", "5m", "6p",
                "6s", "7s", "8s", "6p");
        assertThrows(IllegalArgumentException.class,
                () -> request(twoRedPin, Tile.of(TileKind.P6), RiichiRules.mahjongSoul()));
        assertDoesNotThrow(() -> request(
                twoRedPin, Tile.of(TileKind.P6), rules(new RedFiveConfiguration(0, 2, 0), false, true)));
    }

    @Test
    void visibleIndicatorsParticipateInPhysicalTileAndRedSupplyValidation() {
        assertThrows(IllegalArgumentException.class, () -> new ScoreRequest(
                PINFU_TANYAO, List.of(), Tile.of(TileKind.P6), false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                List.of(Tile.of(TileKind.EAST), Tile.of(TileKind.EAST), Tile.of(TileKind.EAST)),
                List.of(Tile.of(TileKind.EAST), Tile.of(TileKind.EAST)),
                RiichiRules.mahjongSoul()));

        assertThrows(IllegalArgumentException.class, () -> new ScoreRequest(
                PINFU_TANYAO, List.of(), Tile.of(TileKind.P6), false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                List.of(Tile.red(TileKind.P5), Tile.red(TileKind.P5)), List.of(), RiichiRules.mahjongSoul()));
    }

    @Test
    void winningTileInHandMustActuallyMatchAConcealedTile() {
        List<Tile> fourteen = new ArrayList<>(PINFU_TANYAO);
        fourteen.add(Tile.of(TileKind.P6));
        assertThrows(IllegalArgumentException.class, () -> new ScoreRequest(
                fourteen, List.of(), Tile.of(TileKind.EAST), true, WinMethod.TSUMO,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                List.of(), List.of(), RiichiRules.mahjongSoul()));
    }

    @Test
    void openHandCannotClaimRiichiContext() {
        Meld pon = openPon(TileKind.EAST);
        List<Tile> concealed = TestFixtures.tiles(
                "2m", "3m", "4m", "2p", "3p", "4p", "2s", "3s", "4s", "9m");
        assertThrows(IllegalArgumentException.class, () -> new ScoreRequest(
                concealed, List.of(pon), Tile.of(TileKind.M9), false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, true, false, false, false, false, false, false,
                List.of(), List.of(), RiichiRules.mahjongSoul()));
    }

    @Test
    void kazoeDisabledUsesSanbaimanLabelAndPaymentCeiling() {
        List<Tile> redHand = TestFixtures.tiles(
                "2m", "3m", "4m", "3m", "4m", "5m", "4p", "0p", "6p",
                "6s", "7s", "8s", "6p");
        List<Tile> fiveIndicators = List.of(
                Tile.of(TileKind.M2), Tile.of(TileKind.M2), Tile.of(TileKind.M2),
                Tile.of(TileKind.M3), Tile.of(TileKind.M3));
        ScoreResult capped = RiichiServices.scoreCalculator().score(new ScoreRequest(
                redHand, List.of(), Tile.of(TileKind.P6), false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                fiveIndicators, List.of(), rules(RedFiveConfiguration.standardThree(), false, false)));
        ScoreResult yakuman = RiichiServices.scoreCalculator().score(new ScoreRequest(
                redHand, List.of(), Tile.of(TileKind.P6), false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                fiveIndicators, List.of(), rules(RedFiveConfiguration.standardThree(), false, true)));

        assertEquals(13, capped.han());
        assertEquals(Limit.SANBAIMAN, capped.limit());
        assertEquals(24_000, capped.ronPayment().orElseThrow().discarderPays());
        assertEquals(Limit.YAKUMAN, yakuman.limit());
        assertEquals(32_000, yakuman.ronPayment().orElseThrow().discarderPays());
    }

    @Test
    void kiriageManganLabelTracksTheConfiguredPaymentRule() {
        List<Tile> indicator = List.of(Tile.of(TileKind.M2));
        ScoreResult ordinary = RiichiServices.scoreCalculator().score(new ScoreRequest(
                PINFU_TANYAO, List.of(), Tile.of(TileKind.P6), false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                indicator, List.of(), rules(RedFiveConfiguration.standardThree(), false, true)));
        ScoreResult kiriage = RiichiServices.scoreCalculator().score(new ScoreRequest(
                PINFU_TANYAO, List.of(), Tile.of(TileKind.P6), false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                indicator, List.of(), rules(RedFiveConfiguration.standardThree(), true, true)));

        assertEquals(4, ordinary.han());
        assertEquals(30, ordinary.fu());
        assertEquals(Limit.NONE, ordinary.limit());
        assertEquals(7_700, ordinary.ronPayment().orElseThrow().discarderPays());
        assertEquals(Limit.MANGAN, kiriage.limit());
        assertEquals(8_000, kiriage.ronPayment().orElseThrow().discarderPays());
    }

    @Test
    void scenarioRuleChangeRefreshesOnlyNonExplicitStartingScores() {
        RiichiRules custom = new RiichiRules(
                RiichiRules.Profile.MAJSOUL, RiichiRules.RonMode.MULTI_RON,
                30_000, 35_000, 1, RedFiveConfiguration.none(), true,
                false, true, true, true);
        Scenario scenario = Scenario.builder(SEATS)
                .score(SEATS.getFirst(), 12_300)
                .rules(custom)
                .build();
        assertEquals(Integer.valueOf(12_300), scenario.scores().get(SEATS.getFirst()));
        assertEquals(Integer.valueOf(30_000), scenario.scores().get(SEATS.get(1)));
    }

    @Test
    void scenarioRejectsPhasesWhosePendingStateCannotYetBeSerialized() {
        assertThrows(IllegalArgumentException.class,
                () -> Scenario.builder(SEATS).phase(RoundPhase.AWAITING_REACTIONS).build());
        assertThrows(IllegalArgumentException.class,
                () -> Scenario.builder(SEATS).phase(RoundPhase.ENDED).build());
    }

    private static ScoreRequest request(List<Tile> hand, Tile winning, RiichiRules rules) {
        return new ScoreRequest(
                hand, List.of(), winning, false, WinMethod.RON,
                Wind.SOUTH, Wind.EAST, false, false, false, false, false, false, false,
                List.of(), List.of(), rules);
    }

    private static RiichiRules rules(
            RedFiveConfiguration redFives, boolean kiriageMangan, boolean kazoeYakuman) {
        RiichiRules standard = RiichiRules.mahjongSoul();
        return new RiichiRules(
                standard.profile(), standard.ronMode(), standard.startingPoints(), standard.targetPoints(),
                standard.minimumYakuHan(), redFives, standard.openTanyao(), kiriageMangan,
                kazoeYakuman, standard.multipleYakuman(), standard.complexYakuman());
    }

    private static Meld openPon(TileKind kind) {
        ArrayList<TileInstance> tiles = new ArrayList<>(3);
        for (int copy = 0; copy < 3; copy++) {
            tiles.add(new TileInstance(new TileId(IDS.getAndIncrement()), Tile.of(kind)));
        }
        return new Meld(
                MeldType.PON, tiles, Optional.of(new PlayerId("source")), Optional.of(tiles.getFirst().id()));
    }
}

package top.ellan.mahjong.rules.riichi.spi;

import java.util.List;
import java.util.Random;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.spi.RuleDiceRoll;
import top.ellan.mahjong.spi.RuleOpeningPresentation;
import top.ellan.mahjong.spi.RuleWallDirection;
import top.ellan.mahjong.spi.RuleWallPresentation;
import top.ellan.mahjong.spi.SeatId;

/** Deterministic four-player Riichi wall opening, cached once per immutable hand seed. */
record RiichiOpeningLayout(
        RuleWallPresentation wall,
        RuleOpeningPresentation opening) {
    private static final long DICE_SALT = 0x5249_4943_4849_4449L;
    private static final int STACKS_PER_SIDE = 17;
    private static final int TOTAL_STACKS = STACKS_PER_SIDE * 4;

    static RiichiOpeningLayout forMatch(RiichiMatchState match) {
        Random random = new Random(match.currentHandSeed() ^ DICE_SALT);
        RuleDiceRoll roll = new RuleDiceRoll(
                List.of(random.nextInt(6) + 1, random.nextInt(6) + 1));
        int dealer = match.position().dealerIndex();
        int openDoor = Math.floorMod(dealer + roll.total() - 1, 4);
        int breakOffset = roll.total();
        int drawStart = Math.floorMod(
                openDoor * STACKS_PER_SIDE + breakOffset, TOTAL_STACKS);
        return new RiichiOpeningLayout(
                new RuleWallPresentation(
                        List.of(17, 17, 17, 17),
                        drawStart,
                        RuleWallDirection.CLOCKWISE),
                new RuleOpeningPresentation(
                        match.handSerial(),
                        List.of(roll),
                        new SeatId(openDoor),
                        breakOffset));
    }
}

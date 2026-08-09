package top.ellan.mahjong.rules.riichi.spi;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchPhase;
import top.ellan.mahjong.rules.riichi.engine.RiichiMatchState;
import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.spi.RuleMatchResult;
import top.ellan.mahjong.spi.RulePlayerResult;
import top.ellan.mahjong.spi.SeatId;

/** Canonical terminal result projection for durable core history. */
final class RiichiMatchResults {
    private static final String RANK_SYSTEM = "riichi.mahjong-soul.final-score.v1";
    private static final int PAYLOAD_VERSION = 1;

    private RiichiMatchResults() {}

    static Optional<RuleMatchResult> from(RiichiProviderState state) {
        RiichiMatchState match = state.match();
        if (match.phase() != RiichiMatchPhase.ENDED) {
            return Optional.empty();
        }
        ArrayList<RulePlayerResult> players = new ArrayList<>(4);
        for (int seat = 0; seat < 4; seat++) {
            PlayerId domainPlayer = match.seats().get(seat);
            int placement = match.ranking().indexOf(domainPlayer) + 1;
            int score = match.scores().get(domainPlayer);
            long rankingPointsMilli = Math.multiplyExact((long) score, 1_000L);
            players.add(new RulePlayerResult(
                    state.player(seat),
                    new SeatId(seat),
                    placement,
                    score,
                    rankingPointsMilli,
                    payload(seat, placement, score, rankingPointsMilli)));
        }
        return Optional.of(new RuleMatchResult(RANK_SYSTEM, List.copyOf(players)));
    }

    private static byte[] payload(
            int seat, int placement, int score, long rankingPointsMilli) {
        return ByteBuffer.allocate(Integer.BYTES * 4 + Long.BYTES)
                .putInt(PAYLOAD_VERSION)
                .putInt(seat)
                .putInt(placement)
                .putInt(score)
                .putLong(rankingPointsMilli)
                .array();
    }
}

package top.ellan.mahjong.rules.riichi.engine;

import top.ellan.mahjong.rules.riichi.model.PlayerId;
import top.ellan.mahjong.rules.riichi.model.Wind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure Mahjong Soul hanchan boundary progression. */
public final class RiichiMatchProgression {
    private RiichiMatchProgression() {
    }

    public static RiichiMatchAdvance advance(
            RiichiMatchRules rules,
            List<PlayerId> rawSeats,
            RiichiMatchPosition current,
            RiichiRoundResult result) {
        Objects.requireNonNull(rules, "rules");
        List<PlayerId> seats = List.copyOf(Objects.requireNonNull(rawSeats, "seats"));
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(result, "result");
        if (seats.size() != 4 || seats.stream().distinct().count() != 4
                || !result.scores().keySet().equals(java.util.Set.copyOf(seats))) {
            throw new IllegalArgumentException("progression requires four unique scored seats");
        }
        if (current.roundWind().ordinal() > rules.maximumWind().ordinal()) {
            throw new IllegalArgumentException("position exceeds the configured maximum wind");
        }

        PlayerId dealer = seats.get(current.dealerIndex());
        boolean win = !result.winners().isEmpty();
        boolean exhaustive = result.endReason().equals("EXHAUSTIVE_DRAW")
                || result.endReason().equals("NAGASHI_MANGAN");
        boolean abortive = result.abortiveDraw().isPresent();
        int categories = (win ? 1 : 0) + (exhaustive ? 1 : 0) + (abortive ? 1 : 0);
        if (categories != 1) {
            throw new IllegalArgumentException("round result has an ambiguous progression category");
        }

        boolean dealerWon = result.winners().contains(dealer);
        boolean dealerTenpai = result.tenpaiPlayers().contains(dealer);
        boolean dealerContinues = abortive || dealerWon || (exhaustive && dealerTenpai);
        int nextHonba = dealerContinues || exhaustive || abortive
                ? Math.addExact(current.honba(), 1)
                : 0;
        RiichiMatchPosition next = dealerContinues
                ? new RiichiMatchPosition(
                        current.roundWind(),
                        current.handNumber(),
                        current.dealerIndex(),
                        nextHonba,
                        result.riichiSticks())
                : nextDealerPosition(current, nextHonba, result.riichiSticks());

        Map<PlayerId, Integer> scores = result.scores();
        List<PlayerId> ranking = ranking(seats, scores);
        boolean targetReached = scores.get(ranking.getFirst()) >= rules.roundRules().targetPoints();
        boolean bust = rules.bustEndsMatch() && scores.values().stream().anyMatch(score -> score < 0);
        boolean scheduledAllLast = current.roundWind() == rules.scheduledLastWind()
                && current.handNumber() == 4;
        boolean suddenDeath = current.roundWind().ordinal() > rules.scheduledLastWind().ordinal();
        boolean dealerTopStop = dealerContinues
                && (dealerWon || (exhaustive && dealerTenpai))
                && ranking.getFirst().equals(dealer)
                && targetReached;
        boolean dealerDoubleRonPriority = dealerWon && result.winners().size() > 1;

        String endReason = null;
        if (bust) {
            endReason = "BUST";
        } else if (scheduledAllLast) {
            if (dealerTopStop) {
                endReason = "ALL_LAST_DEALER_TOP";
            } else if (!dealerContinues && targetReached) {
                endReason = "SCHEDULED_LENGTH_COMPLETE";
            }
        } else if (suddenDeath) {
            if (targetReached && !dealerDoubleRonPriority) {
                endReason = "SUDDEN_DEATH_TARGET";
            } else if (!dealerContinues
                    && next.roundWind().ordinal() > rules.maximumWind().ordinal()) {
                endReason = "MAXIMUM_WIND_COMPLETE";
            }
        }

        if (endReason == null && !dealerContinues
                && next.roundWind().ordinal() > rules.maximumWind().ordinal()) {
            endReason = "MAXIMUM_WIND_COMPLETE";
        }

        if (endReason != null) {
            LinkedHashMap<PlayerId, Integer> finalScores = new LinkedHashMap<>(scores);
            if (result.riichiSticks() > 0) {
                PlayerId top = ranking.getFirst();
                finalScores.put(top, Math.addExact(
                        finalScores.get(top), Math.multiplyExact(result.riichiSticks(), 1_000)));
            }
            List<PlayerId> finalRanking = ranking(seats, finalScores);
            RiichiMatchPosition endedAt = new RiichiMatchPosition(
                    current.roundWind(),
                    current.handNumber(),
                    current.dealerIndex(),
                    current.honba(),
                    0);
            return new RiichiMatchAdvance(
                    true,
                    dealerContinues,
                    endedAt,
                    finalScores,
                    finalRanking,
                    Optional.of(endReason));
        }

        return new RiichiMatchAdvance(
                false,
                dealerContinues,
                next,
                scores,
                ranking,
                Optional.empty());
    }

    private static RiichiMatchPosition nextDealerPosition(
            RiichiMatchPosition current,
            int honba,
            int riichiSticks) {
        int nextHand = current.handNumber() + 1;
        int nextWind = current.roundWind().ordinal();
        if (nextHand == 5) {
            nextHand = 1;
            nextWind++;
        }
        Wind[] winds = Wind.values();
        if (nextWind >= winds.length) {
            return new RiichiMatchPosition(
                    winds[winds.length - 1],
                    4,
                    (current.dealerIndex() + 1) % 4,
                    honba,
                    riichiSticks);
        }
        return new RiichiMatchPosition(
                winds[nextWind],
                nextHand,
                (current.dealerIndex() + 1) % 4,
                honba,
                riichiSticks);
    }

    static List<PlayerId> ranking(List<PlayerId> seats, Map<PlayerId, Integer> scores) {
        ArrayList<PlayerId> result = new ArrayList<>(seats);
        result.sort(Comparator
                .<PlayerId>comparingInt(scores::get)
                .reversed()
                .thenComparingInt(seats::indexOf));
        return List.copyOf(result);
    }
}

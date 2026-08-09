# riichi-mahjong-java

Java 21 Japanese Riichi Mahjong rules repository extracted from MahjongPaper.
It contains no Bukkit, Paper, Adventure, database, UI, scheduler, Kotlin
runtime, reflection bridge or native library. Every public type is Java under
`top.ellan.mahjong.rules.riichi`.

Shanten, physical waits and scoring are native Java over compact 34-kind count
vectors. Scoring is split into structural decomposition, yaku recognition, fu,
points and bonus counting; no string parsing occurs on the scoring hot path.
See [RULE_COVERAGE.md](RULE_COVERAGE.md).

## Build

```shell
./gradlew clean check
./gradlew microbenchmark
```

`check` runs JUnit 5, verifies the SPI boundary, rejects Kotlin/Kotlinx classes
inside the fat JAR, and fails if production gains any third-party runtime
artifact. Scoring failures throw `EvaluationException`; they are never reported
as an ordinary no-yaku result.

## Supported phase-1 surface

- immutable tile, tile-id, meld, rules, score request/result and settlement models;
- native Java standard/seven-pairs/thirteen-orphans shanten and physical waits, including legal melds;
- native Java Riichi scoring, including standard, seven-pairs and thirteen-orphans shapes;
- per-suit red-five supply, repeated indicators, minimum-yaku-han enforcement and typed payments;
- deterministic round-start/fixture `Scenario` construction and complete replay snapshots;
- copy-on-write `RiichiRoundEngine` revisions for isolated actor/async transitions;
- draw, discard, riichi declaration, chi/pon/open-kan reactions, priority and post-call kuikae;
- ankan/kakan, explicit chankan windows, Kokushi-only ankan robbery, and riichi-ankan wait preservation;
- fifth-kan rejection on self/discard paths and profile-specific open-kan dora timing;
- one-shot reactions, own/temporary/riichi furiten and ippatsu interruption;
- nine-terminals, four-winds, four-riichi, triple-ron and multi-player four-kan abortive-draw triggers;
- ron/tsumo settlement, honba/riichi pool, and per-player payment aggregation;
- exhaustive-draw tenpai/noten payments and river/call-aware nagashi mangan;
- automatic Daisangen/Daisuushii pao registration and integrated ron/tsumo payment splitting;
- immutable East-South match progression with renchan, honba, riichi carry, busts, West sudden death and rankings.
- SPI/TCK 1.5 revision-bound automatic draws, reaction-window expiry, next-round advancement,
  and shanten-aware Riichi bot/trustee decisions from precomputed legal actions;
- deterministic one-roll physical-wall opening metadata, with rendering and animation owned by CraftEngine.

The suite includes 42 named real-world yaku/yakuman examples plus state,
settlement, fail-closed and model-invariant regressions. Native shanten also has
an 8,000-hand deterministic metamorphic corpus: input order cannot change an
answer and every reported physical wait must complete the hand.

## Explicitly fail-closed in phase 1

The command core rejects rather than guesses when a scenario asks it to:

- score renhou;
- use flowers, jokers, unknown tiles, three-player walls, or a fifth physical copy.

Provider snapshots encode the immutable match header, the complete physical
starting scenario and every accepted revision-bound command. Restore replays
that canonical log through the same pure transition engine. Open reaction
windows, submitted reactions, pending kan robbery, discards, calls, riichi,
furiten, ippatsu, dora state, settlement and ended rounds therefore restore to
the same state hash and legal-action set. Snapshot schema and payload digest are
validated before replay.

For a fixture created directly in `AWAITING_DISCARD`, the final tile in the
current player's hand is treated as the last draw. Production matches are
always created from the deterministic provider entrypoint.

Mahjong Soul four-player ranked rules are the baseline. `EARLY_KAN_DORA` is a
compatibility profile whose only intended difference is successful open-kan
dora timing. New behavior requires a versioned rule fixture and regression test.
The rule pack declares deterministic delays only; MahjongPaper owns timer threads,
revision checks, persistence and fault isolation.

`RiichiOpeningLayout` derives one two-dice roll from the immutable hand seed, then
publishes the total-selected open side and break on the common 17-stack wall. It
does not own models, transforms, culling or animation; those remain CraftEngine
configuration and a bounded platform projection.

## Rule references

- [Mahjong Soul official four-player rules](https://mahjongsoul.com/news/46)
- [Mahjong Soul official FAQ](https://mahjongsoul.com/faq)

These official pages were checked on 2026-08-09 and remain the profile
authority for scoring, timing, state and settlement behavior.

# riichi-mahjong-java

Java 21 Japanese Riichi Mahjong phase-1 rules repository extracted from
MahjongPaper. It contains no Bukkit, Paper, Adventure, database, UI, scheduler,
or Kotlin source files, and every public type is Java under
`top.ellan.mahjong.rules.riichi`.

Shanten and physical-wait evaluation are native Java over compact 34-kind count
vectors; this path does not load or invoke the scoring backend. Scoring still
uses the pinned Kotlin artifact `mahjong-utils-jvm:0.7.7`, which resolves Kotlin
stdlib and kotlinx.serialization transitively. A reflective internal scoring
bridge prevents those implementation types from leaking into the public API.
See [RULE_COVERAGE.md](RULE_COVERAGE.md) and
[THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).

## Build

```shell
./gradlew clean check
./gradlew microbenchmark
```

`check` verifies the exact SHA-256 of the scoring backend in addition to running
JUnit 5. The internal Java bridge owns all contact with that Kotlin library.
Scoring failures throw `EvaluationException`; they are never reported as an
ordinary no-yaku result.

## Supported phase-1 surface

- immutable tile, tile-id, meld, rules, score request/result and settlement models;
- native Java standard/seven-pairs/thirteen-orphans shanten and physical waits, including legal melds;
- Riichi scoring through a Java public API backed by the pinned scoring runtime;
- per-suit red-five supply, repeated indicators, minimum-yaku-han enforcement and typed payments;
- deterministic round-start/fixture `Scenario` construction and replayable command results;
- copy-on-write `RiichiRoundEngine` revisions for isolated actor/async transitions;
- draw, discard, riichi declaration, chi/pon/open-kan reactions, priority and post-call kuikae;
- ankan/kakan, explicit chankan windows, Kokushi-only ankan robbery, and riichi-ankan wait preservation;
- fifth-kan rejection on self/discard paths and profile-specific open-kan dora timing;
- one-shot reactions, own/temporary/riichi furiten and ippatsu interruption;
- nine-terminals, four-winds, four-riichi and multi-player four-kan abortive-draw triggers;
- ron/tsumo settlement, honba/riichi pool, and per-player payment aggregation;
- exhaustive-draw tenpai/noten payments and river/call-aware nagashi mangan;
- automatic Daisangen/Daisuushii pao registration and integrated ron/tsumo payment splitting.

The migrated suite includes 42 named real-world yaku/yakuman examples plus
state, settlement, fail-closed and model-invariant regressions. Native shanten
also has a fixed-seed 12,000-hand differential corpus against the pinned former
backend, covering closed 13/14-tile hands and 1-4 legal melds.

## Explicitly fail-closed in phase 1

The command core rejects rather than guesses when a scenario asks it to:

- continue into match-length extension / final ranking policy;
- calculate dealer continuation and complete-match advancement;
- score renhou or serialize/restore an already-open reaction window or ended round;
- derive platform timeouts, bot decisions, persistence, or hidden-information views;
- use flowers, jokers, unknown tiles, three-player walls, or a fifth physical copy.

`Scenario` currently serializes only a draw/discard boundary and does not carry
historical discards, riichi/furiten/ippatsu history, or pending reactions. For a
fixture created directly in `AWAITING_DISCARD`, the final tile in the current
player's hand is treated as the last draw. It must not be presented as an
arbitrary mid-round persistence format.

Mahjong Soul four-player ranked rules are the baseline. `EARLY_KAN_DORA` is a
compatibility profile whose only intended difference is successful open-kan
dora timing. New behavior requires a versioned rule fixture and regression test.

## Rule references

- [Mahjong Soul official four-player rules](https://mahjongsoul.com/news/46)
- [Mahjong Soul official FAQ](https://mahjongsoul.com/faq)

These official pages were checked on 2026-08-09. The platform pages are the
profile reference; mahjong-utils remains an implementation dependency and is
not treated as the authority for platform timing, state or settlement rules.

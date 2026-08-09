# Microbenchmark policy

GitHub Actions runs `./gradlew microbenchmark` on Java 21 after correctness
checks. The JMH-free harness warms code before separately timing native shanten,
native scoring and deterministic reaction resolution.

The evaluator converts input once to `byte[34]`, packs canonical cache keys into
primitive longs and recursively searches standard shapes without strings or
reflection. Seven pairs and thirteen orphans use direct count scans. Its cold
corpus contains 1,024 fixed-seed, distinct logical hands; its hot loop reuses one
canonical key. The bounded synchronized analysis cache holds 4,096 entries.

Score timings are reported separately. "Cold" means a score-cache miss on a
fully initialized and warmed runtime, not JVM process startup; "hot" means an
exact `ScoreRequest` cache hit. The score cache holds 2,048 entries. A miss uses
compact count arrays and bounded structural decomposition; it performs no
reflection, Kotlin boxing or backend conversion. The harness prints:

- native shanten cold / hot;
- native score cold / hot;
- reaction window resolution.

The printed values are smoke metrics, not cross-machine absolute targets. CI
must store results per Java/runtime/CPU and gate median and allocation
regressions only against a matching baseline. Before stable release, record the
Linux runner output for cold/warm shanten, waits, scoring, reaction scans, round
creation and full snapshot replay.

## Historical before-migration baseline

The following 2026-08-08 GraalVM JDK 25 run used the removed reflective scoring
backend. It is retained only as a migration comparison and is not a current
performance claim.

| Operation | Result |
| --- | ---: |
| native shanten cache miss | 13,118.1 ns/op |
| native shanten cache hit | 249.6 ns/op |
| former score-backend cache miss | 1,112,845.3 ns/op |
| former score-backend cache hit | 649.5 ns/op |
| reaction window | 7,189.4 ns/op |

Correctness tests always run before the benchmark. A faster result is invalid
if shanten/waits, event streams, yaku, fu, payments or state invariants differ.

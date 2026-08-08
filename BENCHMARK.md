# Microbenchmark policy

Run `./gradlew microbenchmark` on an otherwise idle machine. The JMH-free harness
warms code before separately timing native shanten, the scoring backend, and
deterministic reaction resolution.

The native evaluator converts input once to `byte[34]`, packs canonical cache
keys into primitive longs, and recursively searches standard shapes without
strings or reflection. Seven pairs and thirteen orphans use direct count scans.
Its cold corpus contains 1,024 fixed-seed, distinct logical hands; its hot loop
reuses one canonical key. The bounded synchronized analysis cache holds 4,096
entries.

Score timings are reported separately. “Cold” means a score-cache miss on a
fully initialized and warmed runtime, not JVM process startup; “hot” means an
exact `ScoreRequest` cache hit. The score cache holds 2,048 entries. Reflection
(`Method.invoke`), Kotlin value-class boxing, meld conversion and backend
allocations remain on the score miss path. The benchmark prints these four
lines explicitly:

- native shanten cold / hot;
- score backend cold / hot.

The printed values are smoke metrics, not cross-machine absolute targets. CI
should store results per Java/runtime/CPU and gate median and allocation
regressions only against a matching baseline. Before a stable release, add JMH
coverage for cold/warm shanten, waits, scoring, reaction scans, round creation,
full replay, and allocation/op.

Latest local smoke run (2026-08-08, GraalVM JDK 25.0.2, one run):

| Operation | Result |
| --- | ---: |
| native shanten cache miss | 13,118.1 ns/op |
| native shanten cache hit | 249.6 ns/op |
| score-backend cache miss | 1,112,845.3 ns/op |
| score-backend cache hit | 649.5 ns/op |
| reaction window | 7,189.4 ns/op |

These values are a reproducibility breadcrumb for this host, not a portable SLA.

Correctness tests always run before the benchmark. A faster result is invalid
if shanten/waits, event streams, yaku, fu, payments, or state invariants differ.

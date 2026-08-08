# Rule coverage

This matrix describes release `0.1.0-SNAPSHOT`; it is a capability boundary,
not a claim that mahjong-utils is the authority for every platform rule.

| Area | Status | Notes |
| --- | --- | --- |
| 34 tiles / physical IDs | Implemented | Invalid honors, red non-fives, fifth copies and duplicate IDs are rejected. |
| Red fives | Implemented | Supply is typed per man/pin/sou suit and enforced in walls and score requests. |
| Shanten / physical waits | Native Java | Standard, seven-pairs and thirteen-orphans shanten use compact 34-kind counts; legal chi/pon/kan melds are supported and exhausted fifth-copy waits are removed. |
| Standard yaku / yakuman | Runtime-backed | 42 migrated named examples cover ordinary, situational and major yakuman cases. Renhou is not implemented. |
| Dora / ura / red dora | Implemented around backend | Repeated indicators count independently; bonus han cannot satisfy minimum-yaku-han. |
| Kazoe / kiriage labels | Implemented | Public `Limit` and backend payments follow the configured toggles. |
| Chi / pon / daiminkan | Implemented | Seat priority, physical chi alternatives, red/non-red alternatives and kuikae are tested. |
| Ankan | Partial, fail-closed | Non-riichi ankan works. Riichi wait preservation and a positive Kokushi robbery window are rejected. |
| Kakan / chankan window | Not implemented | Direct `ScoreRequest` can score chankan, but the round command flow rejects kakan. |
| Kan limit / suukaikan | Implemented | Fifth kan rejected on self and discard paths; four kans by one player continue, otherwise abort after the post-rinshan discard. |
| Kan dora timing | Implemented profiles | Mahjong Soul delayed open-kan reveal and early compatibility timing are distinct. |
| Last live-wall tile | Implemented | Only ron is offered after the final discard; no kan/call is allowed. |
| Furiten | Implemented core | Own-discard, temporary and riichi-permanent lifetimes are explicit. |
| Ippatsu | Implemented core | Calls and kans interrupt; next own discard expires it. |
| Abortive draws | Trigger implemented | Nine terminals, four winds, four riichi and multi-player four-kan triggers; no match continuation policy. |
| Ron / tsumo settlement | Implemented core | Honba, riichi pool/refund, multi-ron aggregation and overflow preflight are tested. |
| Pao | Standalone only | Daisangen/Daisuushii split calculator exists; `RiichiRound` does not auto-register liability. |
| Exhaustive draw / nagashi | Not implemented | The round ends with a reason but does not calculate noten payments or nagashi mangan. |
| Match progression | Not implemented | Dealer continuation, round wind advancement, bust/end conditions and rankings stay in the host. |
| Persistence / replay resume | Partial | Deterministic start/fixture scenarios only; pending reaction and historical player state are not serialized. |
| Three-player / flowers / jokers | Not supported | Inputs fail validation or are outside the 34-kind domain. |

## Runtime boundary

All repository source and public API types are Java. `HandEvaluator` is native
Java and is initialized independently from the scoring backend. Its standard,
seven-pairs and thirteen-orphans results are checked by representative edge
tests plus 12,000 deterministic differential hands against mahjong-utils 0.7.7;
the oracle exists only in test source. `MahjongUtilsBridge` remains a reflective
Kotlin/JVM boundary for `ScoreCalculator`, so upgrading the scoring dependency
still requires compatibility, scoring-gold and performance verification.

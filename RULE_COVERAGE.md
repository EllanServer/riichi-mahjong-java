# Rule coverage

This matrix describes the current `2.0` release candidate and its fail-closed
boundary.

| Area | Status | Notes |
| --- | --- | --- |
| 34 tiles / physical IDs | Implemented | Invalid honors, red non-fives, fifth copies and duplicate IDs are rejected. |
| Red fives | Implemented | Supply is typed per man/pin/sou suit and enforced in walls and score requests. |
| Shanten / physical waits | Native Java | Standard, seven-pairs and thirteen-orphans shanten use compact 34-kind counts; legal chi/pon/kan melds are supported and exhausted fifth-copy waits are removed. |
| Standard yaku / yakuman | Native Java | 42 named examples cover ordinary, situational and major yakuman cases. Renhou is not implemented. |
| Dora / ura / red dora | Native Java | Repeated indicators count independently; bonus han cannot satisfy minimum-yaku-han. |
| Fu / point limits | Native Java | Wait, pair, open/concealed triplet and kan fu feed exact ron/tsumo, kazoe and kiriage calculations. |
| Chi / pon / daiminkan | Implemented | Seat priority, physical chi alternatives, red/non-red alternatives and kuikae are tested. |
| Ankan | Implemented core | Riichi declarations require the drawn fourth tile and preserve the exact wait set; only a scored Kokushi hand may rob an ankan. |
| Kakan / chankan window | Implemented core | A pon is upgraded only after the ron-only reaction window closes; robbery leaves the original pon and removes only the added tile. |
| Kan limit / suukaikan | Implemented | Fifth kan rejected on self and discard paths; four kans by one player continue, otherwise abort after the post-rinshan discard. |
| Kan dora timing | Implemented profiles | Mahjong Soul delayed open-kan reveal and early compatibility timing are distinct. |
| Last live-wall tile | Implemented | Only ron is offered after the final discard; no kan/call is allowed. |
| Furiten | Implemented core | Own-discard, temporary and riichi-permanent lifetimes are explicit. |
| Ippatsu | Implemented core | Calls and kans interrupt; next own discard expires it. |
| Abortive draws | Implemented | Nine terminals, four winds, four riichi, triple ron and multi-player four-kan triggers all retain dealer and increment honba in match progression. |
| Ron / tsumo settlement | Implemented core | Honba, riichi pool/refund, multi-ron aggregation and overflow preflight are tested. |
| Pao | Implemented core | The third open dragon or fourth open wind set binds the feeding player; integrated ron/tsumo settlement splits only the liable yakuman portion. |
| Exhaustive draw / nagashi | Implemented core | The 3,000-point noten pool, formal tenpai set, unclaimed terminal/honor river check, mangan payments and riichi-pool carry are explicit. |
| Match progression | Implemented core | Immutable East-South state owns dealer continuation, honba/riichi carry, bust, automatic all-last stop, West sudden death, maximum wind and East-one tie ordering. |
| Persistence / replay resume | Implemented | Versioned binary snapshots encode match metadata, physical start state and the accepted command log; open reactions and subsequent transitions are replay-verified. |
| Three-player / flowers / jokers | Not supported | Inputs fail validation or are outside the 34-kind domain. |

## Runtime boundary

All production source and public API types are Java. `HandEvaluator` and
`ScoreCalculator` are independent native services. The fat-JAR check rejects
`mahjongutils/`, `kotlin/` and `kotlinx/` entries, while a second Gradle sentinel
requires an empty production runtime classpath. Tests use JUnit and the parent
SPI/TCK only; neither is bundled.

`RiichiRoundEngine` is the immutable production-facing transition boundary. It
deep-copies concealed hands, walls, discards, furiten, reactions, kan tracking
and settlement state before applying a command. Accepted work returns a new
revision; rejected or competing work cannot mutate the input revision. The
snapshot codec persists the deterministic starting image plus every accepted
command and verifies state-hash/legal-action equivalence after restore.

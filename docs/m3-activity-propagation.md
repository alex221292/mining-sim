# M3.3 cross-boundary activity

## Behavior and findings

Storage chunks are 64×64; activity tiles are independent 32×32 regions. ACTIVE_REGIONS starts with every tile marked. An actual edit marks its tile; at the start of the next `step()`, `collectActivity` activates it and its eight in-bounds neighbors, then clears pending marks. `clear()` marks all tiles, even if the world was already empty. A no-op `set` does not mark anything. Moves use two normal writes, so both source and destination changes (including water swaps) mark their tiles. The next tick collects those marks; no regions newly affected during a tick are inserted into the current mask. A tile can sleep after no nearby changes; an external edit or movement in a neighboring tile reactivates it. The 3×3 tile neighborhood conservatively covers the current eight-cell water search and local gravity rules. A single simulation owns each world's tracking state.

No new correctness defect was demonstrated, so physics and activation code were not changed. `CrossChunkActivityTest` checks edits at 0/31/32/63/64/129 on both axes, corners and partial edges, all valid neighboring tiles, clear, long water search across x=63/64, water displacement at the four-chunk intersection, sleeping and reactivation. In controlled forced-path fixtures, FULL_SCAN and ACTIVE_REGIONS match **each tick** as water and sand travel diagonally across two chunk boundaries, as sand falls vertically across several chunks, after support removal, and with simultaneous distant disturbances. Existing `RowSegmentTest`, `ChunkedWorldTest` and `ActivityTest` cover additional directions, water, material counts, determinism and the pre-M3.2 reference hash.

These are not a general equivalence proof. ACTIVE_REGIONS deliberately omits sleeping particles' random draws, and in-tick changes wake other tiles next tick, so random choices and timing can diverge from FULL_SCAN. Tests assert exact equality only for controlled cases; arbitrary worlds may differ. FULL_SCAN remains the default. Both modes retain bottom-up row order, per-tick `moved[]` semantics and M3.2.2 RowSegment. Neither camera visibility nor render-dirty state controls physics.

## Diagnostics and benchmark

Run `./gradlew propagationBenchmark` (Windows: `gradlew.bat propagationBenchmark`); add `-PtraceActivity` to print selected ticks from an ACTIVE_REGIONS lifecycle. Existing optional `Simulation` instrumentation reports active tiles, visited cells, successful moves and tick durations; it is off in normal app use. A move counter counts a swap as one successful movement operation, not two particles. Timings are from uninstrumented runs; counts come from a separate instrumented replay. No per-cell logging is added.

One exploratory, unisolated run on Windows 11 amd64, Ryzen 9 5900X (24 logical processors), Eclipse Adoptium JDK 25.0.5, Gradle 9.2.1. Seed 12345, 800×600, 30 warmup ticks, five batches of 40 measured ticks, fresh world per batch; setup, four pre-settling ticks in non-dense scenarios, disturbances and rendering are outside timing. Mean/median/p95 are ms/tick. Mode order, warmup, JIT, system scheduling and evolving worlds affect results; this is not JMH or a frame-time guarantee.

| Scenario | FULL_SCAN mean/median/p95 | ACTIVE_REGIONS mean/median/p95 | Active tiles / visited cells / moves per tick |
|---|---:|---:|---:|
| Local falling sand | .901/.886/.973 | .100/.102/.110 | 9.2/475 / 9,370 / 1 |
| Four distant sand disturbances | .943/.941/.961 | .109/.102/.130 | 33.6/475 / 34,355 / 4 |
| Dense sand/water | 6.665/6.604/7.038 | 6.956/6.961/7.073 | 475/475 / 480,000 / 268,964.4 |
| Settled water | 1.631/1.626/1.667 | .019/.019/.021 | 0/475 / 0 / 0 |
| Water crossing chunk corner | 1.371/1.365/1.415 | .048/.047/.062 | 9.3/475 / 9,472 / 1 |

LOCAL adds one sand cell at (63,63); DISTANT adds four at x=31,256,512,767 and y=63; DENSE is the historical 30% sand/30% water seed; SETTLED is 12 full-width water rows above a stone floor; CROSSING is a one-cell diagonal air channel in stone with water placed at (58,58), crossing (63,63)→(64,64) during timing. These fixtures demonstrate specific behavior, not representative gameplay workloads.

In the optional LOCAL trace, the disturbance produced 9 active tiles / 9,216 visited cells / 1 move on tick 1, 12 / 12,288 / 1 on tick 2, and 9 / 9,216 / 1 on ticks 3, 32 and 64. At tick 536 it produced 6 / 5,376 / 1; at ticks 540 and 545 it was asleep (0 / 0 / 0). A new edit then produced 6 / 6,144 / 1. These counts include conservatively awakened tiles, not only cells containing moving material.

Existing `benchmark`, `activityBenchmark`, `materialBenchmark`, and `worldAccessBenchmark` also completed. In the existing FULL_SCAN benchmark, 1600×900 active mean/median/p95 was 19.035/17.705/24.790 ms, versus the previous M3.2.2 candidate's 17.484/17.433/18.029 and 17.713/17.671/18.144 in two earlier runs. No production simulation or storage code changed here; the unisolated p95 and mean vary noticeably, so this is not a controlled regression finding. Rendering was not benchmarked or visually verified.

Visible storage chunks, dirty rendering, simulation activity and future structural propagation must remain separate concepts. Future selective simulation should investigate deterministic scheduling and wake coverage before changing semantics; this milestone adds no camera-based sleeping, streaming, render cache or structural subsystem.

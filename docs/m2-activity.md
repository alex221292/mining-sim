# M2.2 activity experiment

**Decision:** postpone active regions as the default. `FULL_SCAN` is the application default and correctness reference; `ACTIVE_REGIONS` remains opt-in for evaluation. The experiment speeds up sparse/settled cases but adds noticeable dense-world overhead and has different scheduling/random consumption. Existing water rules and global bottom-up order are shared; no material physics is duplicated. This is not a chunked world.

## Modes and limits

Construct `Simulation(world, seed, Simulation.Mode.ACTIVE_REGIONS, false)` to opt in; the two-argument constructor uses `FULL_SCAN`. A world must have one simulation owner: enabling or disabling tracking changes its shared mutation notifications. A changed tile and its eight neighbors wake on the **next** tick; external `World.set` and `clear` participate. The 32-cell tile exceeds water's eight-cell search reach. On each row, active tiles and cells are processed in the same horizontal direction as full scan. A destination is still marked moved for the rest of the tick.

Sleeping skips blocked particles and therefore their seeded random draws. A tile made relevant during a tick may be processed next tick, unlike full scan. Identical seeds are repeatable **within each mode**, but arbitrary states need not match between modes. Tests compare exact vertical-fall states, repeatability and per-material conservation over 400 ticks, waking and boundary cases, and the M1.6 basin's level and count after 300 ticks. These are not a proof for every world; active mode is not the default while scheduling equivalence and coverage remain limited.

## Reproduction and metrics

Run `./gradlew activityBenchmark` (Windows: `gradlew.bat activityBenchmark`); `--args=diagnose` runs dense-world diagnostic variants. Identical seed 12345, initial worlds, 50 warmup ticks, five batches of 40 separately timed ticks per mode; each batch starts from a fresh world and includes its first fully active tick. Times exclude initialization, rendering and instrumentation. A separate instrumented 40-tick replay reports average active/total tiles, visited cells, successful moves, time collecting activity, time resetting the moved/activity masks and total observed tick duration. Instrumentation itself changes timings, so use its counters for context rather than subtracting them from the uninstrumented results. Empty and settled worlds have 2.5% average activity because only the first of 40 ticks is fully active.

`EMPTY`, `STATIC` and `ACTIVE` retain the existing benchmark initialization: air, 70% stone/10% wood, or 30% sand/30% water, respectively. `SETTLED` is 12 full-width rows of water over a stone floor; `LOCAL` is a 20×20 alternating sand/water block near the top. No external inputs occur during the measured ticks. The separate original `benchmark` task remains available. Timing values below are one exploratory, unisolated run on Windows 11 amd64, Ryzen 9 5900X (24 logical processors), Adoptium JDK 25.0.5 and Gradle 9.2.1. Means/medians/p95 are milliseconds per tick; mode order, JIT, GC and scheduling affect the results.

| Size | Scenario | Full mean/median/p95 | Active mean/median/p95 | Active tiles / visited cells |
|---|---|---:|---:|---:|
| 400×300 | Empty | .079/.081/.092 | .017/.013/.016 | 2.50% / 2.50% |
| 400×300 | Static | .068/.067/.071 | .015/.013/.018 | 2.50% / 2.50% |
| 400×300 | Active | 1.197/1.201/1.261 | 1.402/1.398/1.497 | 100% / 100% |
| 400×300 | Settled | .405/.420/.435 | .014/.004/.007 | 2.50% / 2.50% |
| 400×300 | Local | .335/.316/.363 | .032/.024/.031 | 7.12% / 7.62% |
| 800×600 | Empty | 1.344/1.296/1.461 | .050/.017/.035 | 2.50% / 2.50% |
| 800×600 | Static | 1.335/1.259/1.446 | .049/.017/.021 | 2.50% / 2.50% |
| 800×600 | Active | 5.040/5.062/5.172 | 5.569/5.617/5.830 | 100% / 100% |
| 800×600 | Settled | 1.485/1.559/1.595 | .052/.016/.021 | 2.50% / 2.50% |
| 800×600 | Local | 1.395/1.428/1.938 | .069/.037/.046 | 3.76% / 3.78% |
| 1600×900 | Empty | 4.101/4.197/4.545 | .152/.051/.065 | 2.50% / 2.50% |
| 1600×900 | Static | 4.081/4.028/4.469 | .151/.052/.070 | 2.50% / 2.50% |
| 1600×900 | Active | 15.211/15.229/15.640 | 16.824/16.985/17.443 | 100% / 100% |
| 1600×900 | Settled | 4.447/4.589/4.863 | .162/.052/.056 | 2.50% / 2.50% |
| 1600×900 | Local | 4.050/4.024/4.425 | .168/.072/.083 | 2.91% / 2.93% |

## Controlled dense-world investigation

The diagnostic variants retain identical rules and worlds: `DIAGNOSTIC_FULL_TRACKED` uses the full row scan but enables `World.set` marking; `DIAGNOSTIC_TILED_TRACKED` additionally traverses all tiles, with no activity collection; `ACTIVE_REGIONS` adds collection and skips sleeping tiles. These variants are diagnostic, not app modes. In a separate single run, mean milliseconds/tick were:

| Size | Full, no marking | Full, marking | All tiles, marking | Active regions |
|---|---:|---:|---:|---:|
| 400×300 | 1.229 | 1.338 | 1.369 | 1.369 |
| 800×600 | 4.966 | 5.457 | 5.464 | 5.473 |
| 1600×900 | 14.764 | 16.178 | 16.462 | 16.492 |

This A/B supports marking overhead as a substantial contributor, not an exact causal decomposition: JIT and run order can affect results. Per-row tile traversal adds a smaller, noisy difference in these runs. Dense runs report 100% active tiles and visited cells, with 62,481/268,964/825,533 average successful moves per tick for the three sizes: movement and neighbor activation prevent useful sleeping here. For the 1600×900 dense instrumented replay, collection was about .016 ms/tick and reset .043 ms/tick versus 16.701 ms total observed; these instrumented values are approximate and do not include marking cost in collection. Thus bookkeeping adds work without avoiding the scan in this workload; collection alone does not explain the whole difference. Additional scenario-isolated JFR and repeated randomized-order runs would improve confidence.

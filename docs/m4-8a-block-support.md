# M4.8a — fixed-block support

Blocks remain on the existing 4×4 or 8×8 grid. Only full orthogonal faces connect; diagonal corners and gaps do not. Off-grid positions and partial faces are not supported. MSIM v2 and its alignment validation are unchanged.

At each fixed tick, before block movement, a deterministic multi-source 0–1 BFS calculates minimum horizontal cost. A stationary unpinned block directly obstructed by a world boundary or a non-AIR, non-WATER, non-STAONE material is a source. Horizontal edges cost the block size, upward edges cost zero, downward edges do not exist, and paths above 32 cells are rejected. Vertical transitions do not replenish the budget. Pinned and FALLING blocks do not provide or relay support. Only the existing block registry is used; there are no persistent bonds. The result is derived again on the next tick after editing, material movement or loading. F3 reports the last tick's analysis, so it can be stale while paused or after a block moves during the tick.

A supported STATIC block resets its wait; unsupported blocks use the existing eight-tick delay and existing collision and water movement. Already FALLING blocks do not stop on a side contact. Physical obstacles still block motion even where a block has no structural path. Each tick recomputes the graph; this is a game rule, not structural mechanics. Pinned blocks are excluded from the graph to ensure temporary strokes do not support other blocks. Saving retains the M4.7.1 input barrier, saved IDs, states, timers and alignment; support is not serialized. RNG restarts at seed 12345 after loading, so exact uninterrupted replay is not promised.

## Headless checks

`BlockSupportTest` covers both sizes, finite cantilevers, bridge distances, removal and restoration, floating cycles, diagonal/gap adjacency, no downward budget reset, falling by walls, save/load and two deterministic 10,000-tick runs with editing, water, sand, accounting and periodic reload. Existing pool, water, editing and persistence regressions remain enabled. The user performs GUI acceptance.

## Exploratory benchmark

Windows, OpenJDK 25.0.5+8-LTS, 800×401 cells, seed 12345, one JVM per version, 20 warmup and 100 sequential measured ticks; mean/p95/max in ms. Baseline is `a8844ef` with its `FixedBlockWorld` compiled separately into `build/tmp`, using the same benchmark and remaining classes. New analysis is measured within the tick; baseline has no analysis. The 4×4 equal-area scenario has 4000 blocks versus 1000 8×8 blocks. Removal occurs at the first measured tick; its single-tick peak is included in max. Water and falling/beam scenarios evolve during warmup, so these are sequential observations rather than independent fresh-fixture peaks. Timing includes JIT and OS noise; no claim of equivalent resulting scenes is made where support changes motion.

After `gradlew.bat classes`, the current run used `java -cp build/classes/java/main miningsim.benchmark.BlockSupportBenchmark`; the baseline used `java -cp "build/tmp;build/classes/java/main" miningsim.benchmark.BlockSupportBenchmark` after compiling the `a8844ef` version of `FixedBlockWorld.java` with JDK 25 into `build/tmp`.

| Scenario | Size | Baseline full mean/p95/max | New full mean/p95/max | New analysis mean/p95 |
|---|---:|---:|---:|---:|
| 100 static | 8 | .510/.543/.671 | .541/.589/.610 | .033/.051 |
| 1000 static | 8 | .676/.727/.767 | .802/.872/1.443 | .224/.278 |
| 5000 static | 8 | 1.453/1.585/1.640 | 1.878/2.140/2.159 | .954/1.135 |
| equal area | 8 | .595/.605/.742 | .680/.704/.852 | .177/.189 |
| beam | 8 | .414/.443/.466 | .418/.437/.495 | .009/.012 |
| bridges | 8 | .512/.542/.680 | .590/.690/.782 | .127/.221 |
| removal | 8 | .932/1.913/2.678 | .907/1.640/2.151 | .161/.246 |
| water | 8 | 1.140/1.496/1.549 | 1.182/1.535/1.597 | <.001/<.001 |
| 100 static | 4 | .631/.641/.667 | .674/.684/.868 | .003/.007 |
| 1000 static | 4 | .756/.776/.914 | .846/.872/.898 | .090/.102 |
| 5000 static | 4 | 1.315/1.335/2.235 | 1.765/1.800/2.028 | .636/.664 |
| equal area | 4 | 1.159/1.174/1.322 | 1.529/1.558/1.584 | .510/.531 |
| beam | 4 | .639/.651/.683 | .688/.699/.715 | .008/.010 |
| bridges | 4 | .757/.774/.802 | .867/.893/.931 | .100/.110 |
| removal | 4 | .736/.771/1.148 | .844/.946/1.518 | .061/.083 |
| water | 4 | .807/.895/.996 | .855/.943/.963 | <.001/<.001 |

These timings demonstrate overhead in dense static scenes; none of these observed maxima exceeds 16.67 ms. They do not establish a worst-case bound.

## Manual acceptance

For each size draw a wall and short/long overhang, a two-pillar bridge and an unsupported line; release the mouse and observe stability and fall delay. Remove and restore a pillar during the delay. Drop blocks beside a wall and into water, draw and erase while running and paused, inspect F3, save/load and check both sizes and existing controls. Verify material conservation and no UI lockup. GUI acceptance is pending.

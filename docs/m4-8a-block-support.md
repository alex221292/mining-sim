# M4.8a — fixed-block support

Blocks remain on the existing 4×4 or 8×8 grid. Only full orthogonal faces connect; diagonal corners and gaps do not. Off-grid positions and partial faces are not supported. MSIM v2 and its alignment validation are unchanged.

At each fixed tick, deterministic multi-source relaxation calculates minimum local horizontal cost. A stationary unpinned block directly obstructed by a world boundary or a non-AIR, non-WATER, non-STONE material is a source. Horizontal transitions add the block size, upward transitions from supported blocks reset cost to zero, downward transitions do not exist, and costs above 32 cells are rejected. Improved costs are reprocessed until stable. Pinned and FALLING blocks do not provide or relay support. The existing registry is used; there are no persistent bonds. Results are recalculated on the next tick after editing, material movement or loading. F3 reports the last tick's analysis, which may be stale while paused or after movement during the tick.

User testing found that an M4.8a step reduced the top overhang; M4.8a.1 resets cost on upward propagation. Straight or stepped columns support four 8×8 or eight 4×4 horizontal blocks; two columns support a free span of eight or sixteen blocks respectively. Successive steps can extend a construction indefinitely: this is an intentional game-model limitation, not a strength calculation.

A supported STATIC block resets its wait; unsupported blocks use the existing eight-tick delay and existing collision and water movement. Already FALLING blocks do not stop on a side contact. Physical obstacles still block motion even where a block has no structural path. Each tick recomputes the graph; this is a game rule, not structural mechanics. Pinned blocks are excluded from the graph to ensure temporary strokes do not support other blocks. Saving retains the M4.7.1 input barrier, saved IDs, states, timers and alignment; support is not serialized. RNG restarts at seed 12345 after loading, so exact uninterrupted replay is not promised.

## Headless checks

`BlockSupportTest` and `LocalSupportTest` cover both sizes, stepped columns, cantilevers, bridges and placement orders, removal and restoration, floating cycles, adjacency, budget reset, falling, save/load and deterministic 10,000-tick replay with editing, water and granular materials. Existing pool, water, editing and persistence regressions remain enabled. The user performs GUI acceptance.

## Exploratory benchmark

### M4.8a versus M4.8a.1

On the same JVM and 800×401 world, `BlockSupportBenchmark` used 20 warmup and 100 sequential ticks in separate runs. The baseline was measured before the code change; stepped fixtures were added to both runs. Full tick mean/p95/max and support analysis mean/p95 are in milliseconds. Dynamic scenes evolve during warmup, and different support rules can produce different states; results include JVM/OS noise.

| Scenario | Size | M4.8a full | M4.8a.1 full | M4.8a analysis | M4.8a.1 analysis |
|---|---:|---:|---:|---:|---:|
| 1000 static | 8 | .810/.917/1.334 | .798/.879/1.480 | .228/.299 | .222/.284 |
| 5000 static | 8 | 1.890/2.181/3.240 | 1.870/2.128/2.434 | .965/1.139 | .952/1.134 |
| stepped | 8 | .421/.441/.471 | .426/.446/.507 | .012/.016 | .018/.021 |
| bridges | 8 | .603/.801/.960 | .589/.693/.915 | .123/.215 | .128/.221 |
| removal | 8 | .909/1.920/2.152 | .883/1.733/2.160 | .133/.168 | .133/.170 |
| water | 8 | 1.100/1.324/1.344 | 1.178/1.554/1.601 | <.001/.001 | <.001/<.001 |
| 1000 static | 4 | .841/.871/.917 | .838/.862/.908 | .091/.102 | .090/.101 |
| 5000 static | 4 | 1.761/1.803/1.822 | 1.745/1.778/1.790 | .643/.671 | .632/.649 |
| stepped | 4 | .689/.702/.712 | .688/.701/.740 | .012/.015 | .013/.016 |
| bridges | 4 | .856/.874/.916 | .861/.886/.926 | .097/.109 | .102/.110 |
| removal | 4 | .828/.895/1.415 | .829/.876/1.389 | .059/.078 | .062/.082 |
| water | 4 | .797/.842/.854 | .837/.918/.929 | <.001/<.001 | <.001/<.001 |

### Historical M4.7.1 versus M4.8a

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

The user confirmed manual Swing acceptance of M4.8a and M4.8a.1: all six test cases passed. In an additional test, removing the lower part of a filled pool wall allowed water to flow through the opening during the block fall delay; the user accepted this behavior. These GUI checks were performed by the user, not the agent.

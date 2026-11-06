# M3.2.2 row-wise chunk traversal experiment

## Decision and implementation

Retain the candidate: in repeated exploratory runs it reduced the 1600×900 active mean by approximately 16–20% in the last two adjacent A/B pairs, with no observed empty/static regression. Results vary with system load; this is not a statistical guarantee. `FULL_SCAN` remains the default and `ACTIVE_REGIONS` experimental. No changes to storage layout, physics, scheduler or arbitrary-coordinate `World.get`/`set` were made.

`World.RowSegment` is a reusable, world-owned live read view; its chunk array is private and never exposed. `Simulation` allocates one view per simulation, splits each already-selected row range at 64-cell boundaries, binds once per segment, and reads each current cell from the live array. The outer row order remains bottom-to-top; segments and cells follow the same randomly chosen horizontal direction. Activity ranges remain 32-cell ranges, split if needed. The view reads the actual backing storage on each call, not a snapshot; all movement, neighbor reads and writes use the unchanged `World.get`/`set` APIs. Thus mutations earlier in the tick are visible. No material values are cached, writes deferred, cells batched, or per-cell objects allocated. `moved[]` and random calls remain in their existing order. The view validates binds and segment bounds; partial edge chunks are supported.

## Validation

`gradlew.bat test build` passed, including pre-M3.2 reference hash `0x616e60ae6b94de46L`, water, conservation, activity and camera tests. `RowSegmentTest` covers live mutation and clear, partial chunks, x=63/64 in both scan directions, y=63/64, and sand/water displacement in both modes. The existing seeded repeatability tests remain in place.

`RowEquivalenceProbe` uses isolated classloaders to compare **every cell after every tick**, not hashes, against separately compiled unchanged baseline classes. On a 130×66 seeded mixed-material world, with a clear and further edits at x=63/64, 100 complete states matched in each of FULL_SCAN and ACTIVE_REGIONS. This is strong fixture-specific evidence, not a proof for all states. Reproduce by compiling the baseline from `624cf84` into a separate directory as in `m3-storage-recovery.md` (its production sources match `b1ab38b`), building the candidate, then run `java -cp build/classes/java/main miningsim.benchmark.RowEquivalenceProbe <baseline-classes> build/classes/java/main` with JDK 25.

## Benchmarks

Windows 11 amd64, Ryzen 9 5900X (24 logical processors), Adoptium JDK 25.0.5, default JVM options. Unchanged `SimulationBenchmark`, seed 12345, FULL_SCAN, 50 warmup ticks and five batches of 40 measured ticks; fresh world per batch outside measurement. Baseline `b1ab38b` production classes and candidate classes were compiled separately with the same JDK. Separate JVMs were run candidate → baseline → candidate → baseline in the table; values are mean/median/p95 milliseconds/tick. Other order baseline → candidate → candidate → baseline was also run. No process isolation or random order; JIT, scheduling, frequency and run order can change results. These are exploratory, not JMH results.

| Size / scenario | Candidate 1 | Baseline 1 | Candidate 2 | Baseline 2 |
|---|---:|---:|---:|---:|
| 400×300 empty | .173/.169/.183 | .198/.192/.225 | .174/.170/.184 | .198/.191/.224 |
| 400×300 static | .150/.147/.169 | .178/.180/.214 | .150/.147/.169 | .183/.179/.213 |
| 400×300 active | 1.491/1.492/1.536 | 1.772/1.766/1.821 | 1.495/1.495/1.538 | 1.854/1.853/1.890 |
| 800×600 empty | .978/.972/1.001 | 2.217/2.157/2.390 | .974/.973/.992 | 2.457/2.470/2.505 |
| 800×600 static | .975/.974/.990 | 2.160/2.145/2.206 | .974/.973/.993 | 2.506/2.479/2.699 |
| 800×600 active | 5.898/5.882/6.008 | 7.244/6.992/8.288 | 5.884/5.864/6.085 | 7.664/7.376/8.753 |
| 1600×900 empty | 3.005/2.963/3.182 | 6.483/6.472/6.569 | 2.949/2.936/3.021 | 7.440/7.454/7.638 |
| 1600×900 static | 2.962/2.953/3.023 | 6.674/6.549/7.126 | 2.938/2.934/2.985 | 7.468/7.471/7.683 |
| 1600×900 active | 17.484/17.433/18.029 | 21.081/21.074/21.492 | 17.713/17.671/18.144 | 22.119/22.076/22.514 |

In the other run order the 1600×900 active means were baseline 24.274 and 21.235, candidate 17.689 and 18.294 ms. Prior flat storage runs on this machine were 15.214 and 15.359 ms (see `m3-storage-recovery.md`); these are historical, not simultaneous measurements, so remaining gap is approximate and cannot establish causality. Candidate active means above are roughly 14–16% slower than those flat means.

`worldAccessBenchmark` does not exercise this traversal: before candidate, READ/WRITE/NEIGHBORS means were 1.616/2.039/9.544 ms/sweep; after, 1.616/2.020/9.627. The unchanged general API is not expected to improve. One post-change `activityBenchmark` run at 1600×900 active measured FULL_SCAN 20.201/20.097/20.883 and ACTIVE_REGIONS 21.340/21.295/21.789 ms; timing is a separate process and differs from the table above. Rendering and initialization are excluded from simulation tick times.

Next step: repeat isolated, randomized-order profiles on representative interactive worlds before further optimization; focus on neighbor access and movement cost while retaining exact order and mutation semantics. This experiment does not implement M3.3.

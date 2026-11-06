# M3.2 chunk storage measurements

Exploratory single runs on Windows 11 10.0 amd64, AMD Ryzen 9 5900X (24 logical processors), Eclipse Adoptium JDK 25.0.5, Gradle 9.2.1. Same unchanged `gradlew.bat benchmark`, FULL_SCAN, seed 12345, 50 warmup ticks per scenario, five batches of 40 timed ticks, fresh identical initialization per batch outside timing. No GUI or rendering is included. Runs were not isolated; JIT, scheduling and run order affect results.

| Size | Scenario | Flat mean/median/p95 ms | Chunked mean/median/p95 ms |
|---|---|---:|---:|
| 400×300 | Empty | .088/.087/.099 | .202/.197/.230 |
| 400×300 | Static | .083/.084/.090 | .182/.183/.216 |
| 400×300 | Active | 1.262/1.261/1.311 | 1.788/1.785/1.837 |
| 800×600 | Empty | 1.186/1.185/1.226 | 2.183/2.179/2.251 |
| 800×600 | Static | 1.206/1.185/1.283 | 2.186/2.170/2.262 |
| 800×600 | Active | 5.049/5.051/5.162 | 7.083/7.079/7.199 |
| 1600×900 | Empty | 3.560/3.561/3.656 | 6.546/6.536/6.672 |
| 1600×900 | Static | 3.550/3.557/3.642 | 6.539/6.528/6.630 |
| 1600×900 | Active | 15.162/15.190/15.368 | 21.281/21.137/22.085 |

The active mean increased about 40–42% in these runs; the empty and static costs also increased substantially. This is a significant **unresolved regression**, not a speedup. `World.get`/`set` now require a chunk lookup and local indexing on top of bounds checking; both simulation and renderer use them, although rendering was not timed. Each edge chunk reserves a full 4096-byte cell array, so small and non-multiple sizes have padding overhead. No chunk references or arrays are exposed outside the world package.

Investigation: an initial chunk implementation using division/remainder and local get/set methods yielded active means 1.802/7.094/22.110 ms for the three sizes. Replacing coordinate arithmetic with shifts/masks yielded 1.852/7.209/22.055 ms; direct package-internal array access produced the table above. These separate single runs do not establish that either change caused the small differences, nor do they isolate individual `get`/`set` costs. A JFR `profile` recording of the first chunked benchmark (stored outside the repository) sampled mostly `Simulation.step`/`scanRange`/`move`; JIT inlining and line attribution preclude a reliable split between address calculations, bounds checks and chunk indirection. A controlled repeated A/B and scenario-specific profiling are needed before changing architecture or introducing caching. No cross-chunk scheduler or cache was introduced.

`gradlew.bat materialBenchmark` and `gradlew.bat activityBenchmark` also completed after the refactor. For 1600×900, mixed-material FULL_SCAN measured 20.251/20.170/21.040 ms and dense `activityBenchmark` FULL_SCAN 21.933/22.013/22.771 ms; these use distinct benchmark tasks and are not substituted for the paired table. ACTIVE_REGIONS remains experimental and opt-in.

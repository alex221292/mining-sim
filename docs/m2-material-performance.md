# M2 material performance

One exploratory, unisolated run on Windows 11 amd64, Ryzen 9 5900X (24 logical processors), Adoptium JDK 25.0.5, Gradle 9.2.1. `gradlew.bat benchmark` and `gradlew.bat materialBenchmark` use FULL_SCAN, seed 12345, 50 warmup ticks and five batches of 40 measured ticks; each batch is reinitialized outside timing. Values are mean/median/p95 milliseconds per tick. Rendering, GUI and initialization are excluded; runtime scheduling, JIT and GC affect results. These are not rigorous microbenchmarks.

| Size | Historical dense sand/water | Mixed | Local soil collapse | Settled terrain |
|---|---:|---:|---:|---:|
| 400×300 | 1.250/1.242/1.301 | 1.210/1.194/1.386 | .289/.287/.294 | .292/.288/.306 |
| 800×600 | 5.011/4.991/5.209 | 4.650/4.655/4.787 | 1.164/1.153/1.232 | 1.158/1.150/1.183 |
| 1600×900 | 15.254/15.245/15.635 | 13.909/13.842/14.357 | 3.475/3.453/3.641 | 3.476/3.445/3.748 |

Historical dense retains 30% sand/30% water/40% air as in M2.1. MIXED uses 15% each sand, water, gravel and soil, 40% air. SETTLED has stone in the lower half and a supported 20×20 block of soil above; COLLAPSE removes a 20×50 shaft beneath that block before timing. The collapse can finish during a batch; it is not a continuously active worst case. Worlds evolve during measurement, with no rendering or user edits.

For context, historical dense means in `m2-profiling.md` were 1.018/4.025/11.965 ms; the M2.2 `activityBenchmark` FULL_SCAN means were 1.197/5.040/15.211 ms. Current historical means are 1.250/5.011/15.254 ms. M2.1 and M2.2 differ in implementation and benchmark harness; this run is close to M2.2 at larger sizes but slower than M2.1. These individual unisolated runs do not identify a cause or establish a statistically reliable regression; further controlled profiling is warranted. Mixed, collapse and settled workloads differ from the historical workload and cannot be used to claim speedup or regression.

The existing `activityBenchmark` also ran successfully; its 1600×900 active scenario measured FULL_SCAN 17.096 ms and ACTIVE_REGIONS 19.237 ms in that separate run. Experimental activity tracking remains opt-in, with known scheduling differences and dense overhead (`m2-activity.md`).

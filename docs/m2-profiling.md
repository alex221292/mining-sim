# M2.1 baseline and profiling

Before optimization, `gradlew.bat test` succeeded and `gradlew.bat benchmark` produced the following unprofiled single-run results. Environment: Windows 11 10.0 amd64, AMD Ryzen 9 5900X, 24 logical processors, Adoptium JDK 25.0.5, Gradle 9.2.1. The existing benchmark uses seed 12345, 50 warmup ticks per scenario and five batches of 40 timed ticks, resetting each world outside timing. See `performance-baseline.md` for scenarios and limitations.

| World | Scenario | Mean ms | Median ms | p95 ms |
|---|---|---:|---:|---:|
| 400×300 | Empty | 0.065 | 0.065 | 0.068 |
| 400×300 | Static | 0.065 | 0.062 | 0.068 |
| 400×300 | Active | 1.018 | 1.009 | 1.071 |
| 800×600 | Empty | 1.164 | 1.168 | 1.197 |
| 800×600 | Static | 1.162 | 1.157 | 1.196 |
| 800×600 | Active | 4.025 | 3.989 | 4.241 |
| 1600×900 | Empty | 3.638 | 3.565 | 4.197 |
| 1600×900 | Static | 3.564 | 3.556 | 3.676 |
| 1600×900 | Active | 11.965 | 11.919 | 12.217 |

JFR profile: run the compiled benchmark directly with `java -XX:StartFlightRecording=filename=m2-baseline.jfr,settings=profile,dumponexit=true -cp build/classes/java/main miningsim.benchmark.SimulationBenchmark`, then `jfr print --events jdk.ExecutionSample --stack-depth 4 m2-baseline.jfr`. Recording includes initialization, JIT warmup and all sizes/scenarios, not isolated scenarios. The recording is stored outside the repository. Of stack frames reported in sampled stacks, `Simulation.step` line 24 appeared 600 times and `flowSideways` line 52 appeared 64 times; `Arrays.fill` call site appeared 4 times. These are sampled frame appearances, not exact CPU time or independent per-scenario attribution. JIT inlining and line attribution can aggregate material access, index/bounds checks and water search into the loop. They do not prove those operations are cheap individually.

Empty and static timings are nearly identical within each size, consistent with a mandatory full-grid traversal and mask clearing even without motion. Active worlds are substantially slower, consistent with movement, accesses and bounded water search. Coordinate calculations, enum conversion and repeated bounds checks are present in the source; this aggregate recording cannot reliably apportion their costs. A scenario-specific profile and controlled comparisons are needed before claiming a specific operation dominates. Rendering and Swing are not measured. No performance threshold or speedup is asserted.

# Exploratory simulation performance baseline

Measured on 2026-11-05 using `gradlew.bat benchmark`: Windows 11 10.0 amd64, AMD Ryzen 9 5900X (12 cores / 24 logical processors), Eclipse Adoptium OpenJDK 25.0.5, OpenJDK 64-Bit Server VM, Gradle 9.2.1. One run, no isolation from other processes. Times are wall-clock durations of `Simulation.step()` only, not rendering or input.

| World | Scenario | Average ms | Median ms | p95 ms | Approx. ticks/s |
|---|---|---:|---:|---:|---:|
| 400×300 | Empty | 0.065 | 0.065 | 0.070 | 15317.9 |
| 400×300 | Static | 0.064 | 0.063 | 0.067 | 15689.5 |
| 400×300 | Active | 1.079 | 1.076 | 1.125 | 926.8 |
| 800×600 | Empty | 1.049 | 1.052 | 1.074 | 953.1 |
| 800×600 | Static | 1.058 | 1.056 | 1.091 | 945.3 |
| 800×600 | Active | 4.413 | 4.419 | 4.494 | 226.6 |
| 1600×900 | Empty | 3.171 | 3.178 | 3.228 | 315.3 |
| 1600×900 | Static | 3.175 | 3.176 | 3.249 | 315.0 |
| 1600×900 | Active | 13.414 | 13.383 | 13.860 | 74.6 |

## Method

Run `./gradlew benchmark` (Windows: `gradlew.bat benchmark`) with JDK 25. The standalone headless process reports Java, VM, OS, architecture, logical processors, dimensions, scenario and seed. Each size/scenario warms up for 50 ticks; five batches each recreate the same seeded initial world outside measurement and time 40 individual ticks. The 200 samples give arithmetic mean, median, nearest-rank p95 and 1000/mean-ms approximate TPS. Initialization and rendering are excluded; timer calls and occasional runtime/OS interruptions remain included.

Initialization and simulation use seed 12345. Empty is all air; static is approximately 70% stone, 10% wood, 20% air; active is approximately 30% sand, 30% water, 40% air distributed randomly. Active worlds evolve during each batch and may partially settle: this is not a sustained worst-case workload. Warmup is a separate world, not the measured instances. Differences in JIT compilation and system scheduling can affect comparisons, particularly the unexpectedly low 400×300 empty/static times; repeat runs and profiling are needed before drawing optimization conclusions. This is an exploratory benchmark, not JMH or a rigorous microbenchmark, and TPS is not the application's achieved TPS.

## Hot-path review

- `Simulation.step()` clears a full-size `boolean[]`, scans every cell bottom-up, reads dimensions and computes indices repeatedly; moves check bounds and then `World.get`/`set` check them again. Static and empty worlds still incur a full scan. Sand/water add seeded random calls and multiple movement attempts. No objects are allocated per cell.
- `World.get` maps a byte ID through a cached enum array; `set` maps back via `ordinal`, checks bounds and null. Public checked access protects state but repeated conversion/checking may cost time; JIT inlining and bounds-check elimination must be measured, not assumed.
- `Java2DRenderer.render()` scans the whole world using checked enum access and writes a reusable pixel buffer, then scales the image. Each paint also scans the world for occupied count. These rendering/overlay costs are **not measured** above; painting may be coalesced and competes with simulation on the EDT.
- Active scenarios are substantially slower than static ones in this run, but no profiler or controlled A/B comparison attributes the difference to individual operations. No hot-path optimization was applied; profile with JFR and repeat measurements before considering small changes. Avoid exposing the mutable world array, chunking, dirty regions, GPU or parallel updates without a later design decision.

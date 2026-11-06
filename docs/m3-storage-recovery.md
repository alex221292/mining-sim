# M3.2.1 chunk storage investigation

## Decision

No production storage/physics optimization was retained. The exact chunked `b1ab38b` remains in place; restoring the flat `ad2fb02` storage is **recommended for consideration**, not performed, if this roughly 40%+ dense-world cost is unacceptable. Flat storage would give up the requested chunk ownership and future chunk-local representation; retaining chunks preserves that architectural groundwork, but carries measured costs today. A chunk-aware row traversal could reduce lookup costs, but requires a careful design and proof of identical global order, mutation visibility and random consumption. It was not implemented or benchmarked here. No new scheduler, cache or raw-array API was introduced.

## Method and reproducibility

Windows 11 10.0 amd64, Ryzen 9 5900X (24 logical processors), Eclipse Adoptium JDK 25.0.5, default JVM options, Gradle 9.2.1. Exact sources from `ad2fb02` (flat, parent of M3.2) and `b1ab38b` (chunked) were compiled with the same `javac` into separate temporary directories. Each run is a fresh JVM, executed in order flat, chunk, chunk, flat. No process isolation or randomized order; two runs per version are insufficient for statistical confidence. The unchanged historical `SimulationBenchmark` uses FULL_SCAN, seed 12345, 50 warmup ticks, five batches of 40 measured ticks, fresh initial worlds per batch. Each table entry is mean/median/p95 milliseconds per tick; no initialization, rendering or input is timed.

To reproduce, extract `src/main/java/miningsim/{world/World.java,world/Material.java,simulation/Simulation.java,benchmark/SimulationBenchmark.java}` from each revision with `git show`; also extract `world/Chunk.java` from `b1ab38b`. Compile each set separately using the same JDK 25 `javac -d <classes> ...` and run `java -cp <classes> miningsim.benchmark.SimulationBenchmark`. Copy the current `WorldAccessBenchmark.java` into each source set and compile/run it against each version to reproduce access measurements. Alternatively `./gradlew worldAccessBenchmark` runs it against the checked-out version. Use a temporary directory, not the working tree; JVM flags must match. No JMH dependency was added.

## Exact historical simulation workload

| Size/scenario | Flat run 1 | Chunk run 1 | Chunk run 2 | Flat run 2 |
|---|---:|---:|---:|---:|
| 400×300 empty | .088/.086/.101 | .198/.192/.225 | .198/.191/.225 | .087/.087/.098 |
| 400×300 static | .082/.082/.091 | .179/.179/.213 | .183/.183/.221 | .104/.091/.149 |
| 400×300 active | 1.252/1.248/1.286 | 1.775/1.764/1.831 | 2.012/2.007/2.047 | 1.273/1.271/1.313 |
| 800×600 empty | 1.175/1.179/1.198 | 2.160/2.149/2.202 | 2.668/2.637/2.769 | 1.158/1.158/1.193 |
| 800×600 static | 1.176/1.181/1.207 | 2.148/2.142/2.194 | 2.686/2.724/2.788 | 1.157/1.158/1.181 |
| 800×600 active | 5.014/5.011/5.121 | 7.257/6.999/8.267 | 8.355/8.028/9.788 | 5.139/5.129/5.368 |
| 1600×900 empty | 3.522/3.508/3.594 | 6.764/6.528/7.243 | 8.070/8.032/8.318 | 3.472/3.469/3.537 |
| 1600×900 static | 3.528/3.540/3.604 | 6.517/6.493/6.617 | 8.099/8.212/8.399 | 3.500/3.493/3.669 |
| 1600×900 active | 15.214/15.224/15.391 | 21.084/21.045/21.590 | 24.248/24.192/24.611 | 15.359/15.335/15.797 |

Chunk run 2 was notably slower; system load/frequency/JIT effects are not controlled. Both runs nevertheless show a substantial regression, including when no materials move. Historical `m3-chunk-performance.md` remains unchanged.

## Access isolation

`WorldAccessBenchmark` uses 1600×900 with an alternating stone/water pattern, 12 warmup sweeps, 20 timed sweeps and identical seeds/initialization. READ calls public `get` once per interior cell; WRITE alternates materials via public `set`, with an untimed full read after each timed sweep to observe writes; NEIGHBORS reads center and four orthogonal neighbors, including across chunk boundaries. Each sweep publishes a checksum through a volatile sink. Setup and write verification are outside timing; API validation, material enum conversion and activity-tracking-disabled branch remain included. These aggregate scans are not independent nanosecond `get`/`set` timings, and checksums do not provide JMH's rigorous dead-code or compiler controls.

| Pattern | Flat run 1 | Chunk run 1 | Chunk run 2 | Flat run 2 |
|---|---:|---:|---:|---:|
| READ | .701/.665/.685 | 1.619/1.618/1.631 | 1.620/1.616/1.640 | .667/.665/.680 |
| WRITE | .927/.924/.944 | 2.033/2.022/2.083 | 2.032/2.029/2.056 | .874/.860/.923 |
| NEIGHBORS | 3.901/3.897/3.954 | 9.709/9.675/9.877 | 9.658/9.642/9.735 | 3.902/3.883/4.026 |

Each entry is mean/median/p95 ms per sweep; checksums matched for both versions. Synthetic raw-byte controls inside the same harness compare row-major flat and `byte[][]` chunk addressing, with and without an explicit bounds conditional. One chunk-version run measured means .307/.263 ms (flat unchecked/checked) and 1.083/1.095 ms (chunk unchecked/checked), checksum 2870008. The raw controls omit `World`, enum conversion and actual `Chunk` objects, and the JIT may eliminate checks or specialize loops; anomalous mean/median differences show the warmup is not fully stable. They support investigating layout/addressing, but do not prove cache misses, quantify bounds-check cost or assign an exact cost to material conversion/indirection. Neither a CPU cache profiler nor generated machine-code analysis was performed.

## Profiling and optimization experiment

An additional JFR `profile` run of the exact chunked access harness produced 48 execution samples and six allocation samples, with five sampled `byte[]` allocations attributed to `Chunk` construction during setup and no recorded GC. Sampling is incomplete and does not prove absence of allocations in all paths. JIT `-XX:+PrintInlining` showed `World.get`, `chunkAt` and `Material.fromId` eventually inlined at hot sites, although some earlier call sites initially failed size limits. This does not prove that all bounds checks or indirections are eliminated. JFR recordings remain outside the repository.

We also tried moving the out-of-bounds exception into a separate helper and using `(x | y) < 0` for the negative check, retaining public semantics. The exploratory run measured READ 1.694/1.687/1.702, WRITE 2.214/2.210/2.241, NEIGHBORS 10.670/10.618/10.974 ms; the full simulation 1600×900 active measured 24.054/23.887/25.039 ms. These values are not a controlled proof of slowdown, but show no convincing recovery relative to the other runs; the experiment was removed. Prior M3.2 shift/mask and direct-access experiments are recorded in `m3-chunk-performance.md`. No production access change is claimed as an optimization.

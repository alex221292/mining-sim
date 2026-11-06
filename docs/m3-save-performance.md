# M3.4 persistence measurements

One exploratory, unisolated run on Windows 11 amd64, AMD Ryzen 9 5900X (24 logical processors), Eclipse Adoptium JDK 25.0.5 and Gradle 9.2.1. Run `./gradlew persistenceBenchmark` (Windows `gradlew.bat persistenceBenchmark`). Each scenario builds a fixed world outside timing, does three warmup save/load pairs and ten measured pairs using a file in Java's temporary directory. Times include buffered file I/O, writing a sibling temporary file and replacement on save; world initialization and the post-load occupied-count observation are outside timing. OS filesystem cache, JIT, antivirus, filesystem and scheduling affect results. These are not JMH or durable-storage latency measurements.

| World | Scenario | File bytes | Save mean/median/p95 ms | Load mean/median/p95 ms |
|---|---|---:|---:|---:|
| 400×300 | Empty | 120024 | 2.713/2.677/2.912 | 8.574/8.531/9.451 |
| 400×300 | Mixed | 120024 | 2.527/2.497/2.735 | 11.942/11.917/12.610 |
| 800×600 | Empty | 480024 | 6.640/6.561/7.403 | 12.197/12.115/13.098 |
| 800×600 | Mixed | 480024 | 6.506/6.504/6.638 | 16.471/16.383/16.904 |
| 1600×900 | Empty | 1440024 | 17.044/17.057/17.491 | 13.134/13.093/13.844 |
| 1600×900 | Mixed | 1440024 | 17.239/17.133/18.158 | 18.374/18.590/19.092 |

Mixed uses uniformly random IDs 0–6 with seed 12345. Sizes are exactly 24 bytes plus one byte per valid cell, without compression or partial-chunk padding. Temporary heap usage was **not measured**: the implementation uses buffered streams and creates a new chunked world during load, without a second full-world payload array; the desktop application also creates a renderer for the loaded dimensions and holds the old world until successful replacement. Saving first writes a sibling temporary file, then attempts atomic replacement with a non-atomic fallback where unsupported; this protects against a failed write truncating the original but is not a guarantee of durable writes after power loss.

The existing simulation `benchmark` also ran: FULL_SCAN 1600×900 ACTIVE mean/median/p95 was 17.378/17.340/17.980 ms/tick. Earlier M3.2.2 candidate runs were 17.484/17.433/18.029 and 17.713/17.671/18.144 ms/tick. These separate exploratory runs do not establish statistical equivalence, but show no obvious regression in this measurement; no simulation or world hot-loop code was changed.

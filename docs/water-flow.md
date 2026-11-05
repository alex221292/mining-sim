# M1.6 water flow

Each water particle attempts one downward move, then either downward diagonal, using a seeded tie break. If both fail, each side is searched through at most eight contiguous air cells for the first cell with air below; the nearer drop is preferred and ties use the seeded direction. The particle moves horizontally to that cell and can fall on a later tick. If no drop is visible and water supports the particle, it moves to the end of the longer open run, at most eight cells; ties use the seeded direction. Otherwise it attempts a one-cell sideways move. Every destination is air; walls, water and other materials cannot be crossed. A moved mask ensures at most one movement per particle per tick, including sand-water exchanges. Sand continues to displace unmoved water. No water-specific per-cell objects or persistent velocity/pressure state are allocated.

This is a bounded local approximation, not hydrostatic pressure. The open-run fallback helps a crest travel toward a more distant lower surface, but cannot detect a drop beyond its horizon and can still cause oscillations, especially on solid floors. It does not guarantee leveling in all geometries; overhangs, narrow openings, barriers and wide basins can behave differently.

## Deterministic U-basin fixture

`WaterFlowTest.basinLevelsAndRemainsLevelWithSeed42` constructs a 42×22 world: stone walls at x=0 and 41, stone floor y=21, water at x=1..20 and y=5..20 (320 cells), all else air. The measure is the maximum minus minimum number of water cells per interior column; it approximates surface height when columns are contiguous and does not detect internal holes. With seed 42, the initial difference is 16. After 100 ticks the test requires a difference of at most 1 and checks that this remains true through tick 300, with water count conserved. This threshold is for this fixture, not a claim of realistic fluids or general stability.

Measured on the same fixture, seed and machine, old rules gave differences 6, 2, 3 and 2 at ticks 100, 200, 300 and 600 respectively; new rules gave 0 at each of those ticks. Both runs used the same initial placement and the same seed, but consume randomness differently. Tests also cover falls, diagonal priority, spreading, bounded movement, narrow passages, sand and water conservation, and repeatability.

## Before/after performance

One run before and one after using unchanged `gradlew.bat benchmark`, Windows 11 amd64, Ryzen 9 5900X, Adoptium JDK 25.0.5, Gradle 9.2.1; each uses seed 12345, 50 warmup ticks, five batches of 40 timed ticks with world reset outside timing. These are headless, exploratory single-run wall-clock measurements; results vary with JIT, scheduling and workload evolution. No rendering or initialization is included. Changed rules yield different evolving states, so differences cannot be attributed solely to rule execution cost.

| Active world | Before mean ms/tick | After mean ms/tick | Change |
|---|---:|---:|---:|
| 400×300 | 1.083 | 1.008 | −6.9% |
| 800×600 | 4.399 | 4.008 | −8.9% |
| 1600×900 | 13.280 | 12.033 | −9.4% |

The observed decreases are not a guaranteed speedup. The benchmark's random dense active scenario is not the U-basin and is not a sustained worst-case for the bounded search. See `performance-baseline.md` for the original baseline and methodology.

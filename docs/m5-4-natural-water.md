# M5.4 — natural water bodies (visually accepted)

Generation runs after caves and only replaces AIR with ordinary WATER. No simulation tick, source, persistent water-body object, pressure, infiltration, UI change or MSIM change is introduced. F8 still opens paused; the ordinary startup remains an empty sandbox. The water table and diagnostic counters are generation-only and are not serialized. Coordinates are cells with y increasing downward; one cell represents 0.1 m.

The underground stage identifies orthogonally connected AIR below the terrain profile, ignoring components smaller than eight cells. A separately mixed seed channel controls selection and filling. The generation-only water-table reference is 43% of the distance from the preset base surface to the bottom, with a sinusoidal horizontal variation of at most 24 cells (or 6% of world height). Selection probability is clamped to 18–78% and depends on component depth, bottom and the reference level. A selected component can be completely flooded (12% branch), or receives a seeded fractional height adjusted by bottom depth; the fraction is clamped to 8–95%. At the chosen horizontal level a second flood fill separates pockets connected only by passages above that level. The first pocket of at least eight cells is filled; each further pocket is independently selected with 65% probability. This is not a hydrogeological model; coincident levels in separated pockets do not imply hydraulic connection.

The independent surface channel samples depressions in the existing first-solid-row profile. Prefix minima from both sides determine the natural spill row; only interior depressions bounded by higher terrain on both sides qualify. Candidate runs must be at least three cells wide; 55% are selected, with a horizontal level between the spill and roughly half the basin depth. No terrain is carved. Loose deposits may settle after resuming and change capacity or cause overflow. Water follows the existing finite-volume cellular rules; horizontal movement is bounded and there is no hydrostatic pressure. MSIM v2 stores WATER cells, not seed or water metadata, and does not preserve exact RNG continuation on load.

The stage uses bounded grid scans and flood fills with O(width × height) temporary primitive-array storage; selected cave cells are visited at most twice. Generation is synchronous and can stall the UI. Existing block-water displacement uses a bounded destination search and can be blocked if destinations or its work budget are insufficient; an attempt is not necessarily a successful displacement.

World Pre-Simulation and Dynamic World Activation remain future directions and are not part of this milestone.

## User visual acceptance

The user visually inspected and accepted M5.4, positively assessing the generation of underground and surface water bodies and the observed interaction of water, sand and the rock mass. The user did not specifically confirm checks of both block sizes, save/load, or other individual scenarios. The agent did not perform the visual inspection.

## Verification

`gradlew.bat test --tests miningsim.generation.NaturalWaterGeneratorTest` passed with Java 25. The focused cases compare against an independently constructed dry terrain/cave baseline and fixed-block ownership, check determinism, horizontal levels, elevated passages, spill boundaries and small worlds, open a generated reservoir into a lower chamber, exercise falling blocks of both sizes in generated water, and check paired replay and MSIM v2 round-trips before and after ticks. Existing cave, geology and world tests account for AIR replaced by WATER without relaxing conservation or ownership. Full regression/build: `gradlew.bat test build`.

## Fixed headless calibration

`gradlew.bat naturalWaterCalibration`, 800×401, seeds 0–15, 431039, 357355, 12345; 19 worlds per block size. Systems are orthogonally connected original cave AIR/WATER; water-body sizes are orthogonally connected WATER cells. Lake counts are selected profile basins, not a guarantee of visually distinct pools.

| Block | Dry / partial / full systems | Surface basins | Worlds without surface / underground water | Underground / surface cells | Largest underground / surface body | Underground cells by world-y third |
|---|---:|---:|---:|---:|---:|---:|
| 8 | 39 / 28 / 2 | 24 | 4/19 / 3/19 | 57,888 / 81,600 | 6,400 / 16,288 | 0 / 10,096 / 47,792 |
| 4 | 26 / 37 / 3 | 24 | 4/19 / 1/19 | 110,112 / 92,872 | 13,756 / 24,276 | 0 / 17,320 / 92,792 |

Selected seeds 5, 8, 431039, 357355 and 12345 were advanced 600 ticks for each block size; total material counts, block IDs and restored ownership validated. This does not require water to be stationary and is not exhaustive. Seed 8 includes a large 4×4 underground reservoir; 431039 has a large surface lake. For further manual inspection try seed 0 for full and dry systems, 4 and 8 for large underground bodies, 431039 for surface lakes, and 357355 for known collapses. Opening a reservoir into a lower excavation and checking save/load in both block modes remain separately unconfirmed manual scenarios.

## Exploratory timing

Windows, Ryzen 9 5900X, Corretto 25.0.5+10, 800×401; `gradlew.bat worldGenerationBenchmark`, two generation warmups, ten measurements, one first tick, 120 early ticks, 20 warmups and 100 late ticks. Mean/p95/max ms; first tick is one observation. Baseline is the previously documented M5.3.1 run on OpenJDK 25.0.5+8, not a same-JVM controlled comparison.

| Size / seed | Generation | Water stage | First | Early | Late |
|---|---:|---:|---:|---:|---:|
| 8 / 12345 | 12.956/27.651/27.651 | 2.489/4.531/4.531 | 22.120 | 2.627/6.248/11.440 | 1.875/1.912/1.957 |
| 4 / 12345 | 11.259/17.232/17.232 | 1.228/2.718/2.718 | 6.435 | 4.050/5.487/6.317 | 3.763/3.815/3.999 |
| 8 / 8 | 14.954/25.679/25.679 | 3.053/3.844/3.844 | 24.018 | 2.645/6.990/12.219 | 1.768/1.800/2.047 |
| 4 / 8 | 11.919/17.882/17.882 | 1.096/1.315/1.315 | 4.516 | 4.535/5.506/16.610 | 4.178/4.215/6.171 |

M5.3.1 seed 12345 generation mean/p95/max was 11.771/21.941/21.941 (8), 9.656/11.172/11.172 (4); first tick 24.895 / 3.790, early 2.500/4.126/12.726 / 3.216/3.362/3.579 and late 1.770/1.839/1.924 / 3.238/3.316/3.357. Seed 8 includes 7,544 / 18,436 total water cells; 1 / 63 blocks moved and the existing block diagnostics counted 3 / 1,562 water-displacement attempts in the first 121 ticks. The 8×8 first observations exceed the 16.67 ms tick budget; an earlier 4×4 seed-8 run reached 16.937 ms. JIT, OS noise, different JDK builds and evolving scenes limit comparisons; these samples do not establish worst-case bounds.

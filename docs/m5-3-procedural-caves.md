# M5.3 — procedural caves

F8 now adds dry, closed cavities inside the M5.2.1 rock mass. Terrain and deposit channels are unchanged. An independent `java.util.Random` seeded with `seed XOR 0x62a9d4b6e3815f07` selects 20 candidate centres and ellipses. Cell-space radii are selected from 12–48 horizontally and 8–24 vertically, converted to block units. Ellipses contain whole blocks; candidates with fewer than two blocks, inadequate 16-cell roof, bedrock or side margins, non-rock cells, or orthogonal contact with an earlier cave are rejected. No special cave materials or persistent cave objects are used. Adjacent cavities are deliberately rejected rather than merged; diagonal contact is not a passage. Small worlds without sufficient room can have no candidates.

For each geometrically valid candidate, `FixedBlockWorld.supportedAfterRemoving` runs the same support relaxation as the physical tick against a hypothetical removal set, restoring live diagnostic caches afterward. It advances no physical time and modifies no cells or IDs. Unsupported candidates are rejected without application; supported candidates remove blocks through the existing ownership API. A final support check is mandatory. Up to 20 candidates and 21 support checks bound the work; each check is bounded by the finite block registry. Cave AIR, remaining IDs and next ID persist in unchanged MSIM v2. F8, pause, RNG-on-load, water and material physics are unchanged.

## Headless calibration

`CaveCalibrationBenchmark`: 800×401, seeds 0–31 plus 357355 and 12345, both sizes; component areas are cell counts, depths are world y coordinates, bounding-box dimensions are cells. All generated worlds passed the final support check. The 32-seed aggregate:

| Size | Candidates | Accepted/components | Geometry rejects | Support rejects | Worlds without caves | Mean removed stone | Total area | Component area min/mean/max | Width | Height | World y |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 8 | 640 | 179 | 430 | 31 | 0/32 | 3.085% | 165440 | 320/924.2/3520 | 24–104 | 24–56 | 160–376 |
| 4 | 640 | 182 | 450 | 8 | 0/32 | 4.105% | 219296 | 304/1204.9/3312 | 28–100 | 20–52 | 160–380 |

For seed 357355: 8×8 has 8 components, area 8960, boxes 24–88×24–56, depth 176–328; 4×4 has 9, area 11312, boxes 28–100×28–52, depth 192–360. For seed 12345: 8×8 has 8 components, area 7680, boxes 24–88×24–40, depth 176–360; 4×4 has 7, area 11408, boxes 28–100×28–44, depth 224–364. Geometry rejects dominate; support rejects are 31/640 and 8/640. These ellipses do not create winding connected cave networks, and counts/coverage are observations, not guarantees.

## Performance

Windows, OpenJDK 25.0.5+8-LTS, 800×401 seed 12345; `WorldGenerationBenchmark` before and after, separate JVMs, generation two warmups/ten samples, early 120 and late 100 sequential ticks, in-memory persistence. Mean/p95/max milliseconds; first tick is one observation. Baseline generation is the M5.2.1 base generation; candidate and support times are new-stage portions of total generation, not independent runs.

| Size/version | Generation | Candidate formation | Support checks | First tick | Early 120 | Late tick |
|---|---:|---:|---:|---:|---:|---:|
| 8 baseline | 12.446/25.881/25.881 | — | — | 21.538 | 2.162/3.311/10.677 | 1.771/1.865/1.953 |
| 8 caves | 23.439/43.695/43.695 | .360/.542/.542 | 12.808/19.036/19.036 | 17.545 | 2.184/3.282/6.093 | 1.818/1.866/3.026 |
| 4 baseline | 9.945/18.903/18.903 | — | — | 5.226 | 3.716/4.715/5.778 | 3.673/4.175/5.499 |
| 4 caves | 34.294/37.995/37.995 | .907/1.529/1.529 | 25.082/28.100/28.100 | 3.841 | 3.620/3.790/5.330 | 4.195/4.318/4.731 |

Generation became substantially slower, mainly due to repeated full support checks. The first 8×8 observation exceeded 16.67 ms both before and after; this sample does not establish a worst-case bound. Generation is synchronous and may stall the UI. There is no separate per-tick cave phase, but the shared support traversal now checks an empty hypothetical-removal set during normal ticks.

## Checks and manual acceptance

Headless tests cover deterministic generation, hypothetical support rollback, geometry, small worlds, 600-tick stability, water entry, MSIM and paired 10,000-tick replay for both sizes. Existing geology assertions were changed only to allow documented whole-block cave AIR beneath the rock boundary. Run `gradlew.bat test build` and `git diff --check`. User manual acceptance remains pending: inspect seeds 357355, 12345 and 3 in both sizes; resume and observe stability; excavate a passage, introduce water, remove supports and save/load the altered world. The agent did not launch Swing.

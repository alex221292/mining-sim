# M5.3 — procedural caves

## M5.3.1 — branching morphology (manual acceptance pending)

The user rejected M5.3 visually: its separate, symmetric ellipses, grid quantization and rejection of orthogonal contact produced isolated, sometimes cross-shaped holes. Support filtering also rejected some shapes, but was not the sole cause; M5.3 did not shrink or correct rejected candidates. M5.3.1 uses 3–6 seeded walks with smoothly changing headings, radii 8–17 cells and an occasional 7-cell radius expansion. Each main walk has at most 48 steps and up to three side walks anchored along its path; each step advances 0.7 block sizes. Whole-block masks may overlap and connect; clipped components below four blocks are discarded. The independent channel is mixed before seeding. The 16-cell rock roof, bedrock and side margins remain. Unsupported candidates are no longer rejected. F8 starts paused with no hidden ticks; existing physics can cause local collapses after resume. All rock is ordinary STONE. Work is bounded by six systems, three branches, 48 steps per walk, finite brush and world-grid scans. No new per-tick physics or MSIM changes are introduced. Visual quality requires user acceptance.

### Calibration and settling

For 800×401, seeds 0–31, 8×8 / 4×4 respectively: 142 / 142 accepted walks, 97 / 101 walks expanded by a side branch (not necessarily distinct final branched components), 120 / 117 connected components and no cave-free worlds. Mean carved fraction of original stone was 8.187% / 8.065% versus M5.3's 3.085% / 4.105%. Component areas min/mean/max were 384/3676/13504 and 80/3656/14784 cells; bounding widths 16–208 / 12–200, heights 16–144 / 4–208, world y 136–376 / 148–380. Clipping may still produce narrow remnants; the four-block filter is not a guarantee of visual quality.

At ticks 60/300/600 mean retained original AIR cells were 95.48/95.11/95.07% (8×8) and 96.92/96.86/96.86% (4×4). At 600, minimum retention was 80.15% / 88.99%, mean moved blocks 10.06 / 29.19, maximum 66 / 141, and active and unsupported block counts were zero across the sample. Initially unsupported maxima were 66 / 141. Mean connected components of retained original AIR changed from 3.75 to 4.13 / 3.66 to 3.97; this measures intersection with original voids, not connectivity through newly opened spaces. Initial largest component maxima were 13504 / 14784 cells, retained maxima 10432 / 11968. M5.3 had no rock movement over 600 ticks in the same 35-seed sample. These are observations, not a stability guarantee.

For seeds 431039, 357355, 12345 respectively, initial components: 5, 2, 1 (8×8) and 4, 2, 3 (4×4); retained original AIR at 600: 13888/14400, 7104/7680, 9600/10112 and 15184/15600, 9952/11344, 14288/14544 cells. Moved blocks: 8, 9, 8 and 26, 91, 16. No active or unsupported blocks remained at 600 in these cases.

### Performance and acceptance

On Windows, OpenJDK 25.0.5+8-LTS, 800×401 seed 12345, `WorldGenerationBenchmark` in separate JVMs, generation warmup 2 and 10 measurements; early 120 ticks and late 100 ticks: M5.3.1 mean/p95/max ms for generation 11.771/21.941/21.941 (8×8), 9.656/11.172/11.172 (4×4); candidate formation 1.076/1.260/1.260 and .925/.984/.984; support checks 0; first tick 24.895 and 3.790; early ticks 2.500/4.126/12.726 and 3.216/3.362/3.579; late ticks 1.770/1.839/1.924 and 3.238/3.316/3.357. The historical M5.3 comparison appears below. The 8×8 first tick exceeded the 16.67 ms budget; single-run timings do not establish a worst-case guarantee. Early 12345 involves 8 / 16 moving blocks, not the largest collapse in the sample.

Manual acceptance remains mandatory: inspect 431039 and 357355 in both sizes, plus 12345 and seeds 8, 12, 21, 25 for connected or branched candidates and contrasting settling (e.g. 8×8 seed 21 and 4×4 seed 25). These are headless-selected candidates, not visually verified examples. Observe the paused geometry, resume and observe settling; excavate, flood, save and load. No Swing session was launched by the agent. MSIM v2 does not preserve RNG/seed for exact continuation after load.

## Historical M5.3 design and measurements

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

## Historical M5.3 checks

M5.3 headless tests covered deterministic generation, hypothetical support rollback, geometry, small worlds, 600-tick stability, water entry, MSIM and paired 10,000-tick replay for both sizes. M5.3.1 replaces assertions of immobility with ID, material and ownership conservation while allowing physical movement. Existing support and delay tests remain unchanged. The agent did not launch Swing.

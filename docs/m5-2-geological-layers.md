# M5.2 — geological layers

F8 uses the seeded surface profile and selected block size. Functional M5.2 manual cases passed, but the user observed insufficient geological diversity, thin deposits and visually dominant rock. M5.2.1 increases the preset surface amplitude to 48 cells and the maximum SOIL/SAND/GRAVEL thicknesses to 24/64/48 cells. Independent seed-salted channels use smoothstep value noise at twice the terrain scale and threshold -0.2. For each aligned column, loose boundaries have one-cell precision; the sum is rounded down to align the STONE boundary, removing the remainder from gravel, then sand, then soil. Available depth is clamped in soil/sand/gravel order to retain two rock rows above BEDROCK. Zero total produces an outcrop. Soil maximum is three times `soilDepth`, capped at 24. All distances are cells; profiles remain constant across each block-width column. These are gameplay values, not geological constants. Visual acceptance of M5.2.1 is pending.

STONE is created only by the existing API in row-major order down to BEDROCK; no caves or deep granular inclusions are generated. Material noise channels are independent of one another and of the surface seed channel. Same configuration and seed reproduce cells, IDs, next ID and states; 4×4 and 8×8 have similar world-space profiles but may differ due to quantization. Initial rock remains supported; loose materials may settle naturally after the paused scene starts. No hidden simulation ticks, physics changes or new format are introduced. MSIM v2 stores the resulting materials and blocks, not the seed; MSIM v1 remains LEGACY and RNG restarts after loading under the M5.1 contract.

## Tests

`GeologicalLayersTest` checks fixed seeds, bounds, outcrops, all layers, continuous cells and ownership for both sizes, 600-tick compact-world stability and conservation, excavation and sediment/water, and MSIM round-trips before and after edits. The existing `WorldGeneratorTest` includes paired 10,000-tick replay for both sizes with edits, water, persistence and material accounting. Its original constant-soil assertions were updated to check the new documented layer geometry and preserve conservation checks. Full suite: `gradlew.bat test build`. Manual Swing acceptance is pending.

For M5.2.1 the profile-thickness bounds were updated to 24/64/48 and the adjacent-column smoothness limit to twice the block size, reflecting the larger noise amplitudes; geometry, ownership and conservation checks remain. A fixed 32-seed test checks thickness thresholds, long outcrops and one-cell layer resolution. Existing 600-tick and 10,000-tick scenarios remain enabled.

## Exploratory performance

### M5.2.1 calibration

`GeologyDiversityBenchmark` measures 800×401 profiles for seeds 0–31 and separately seed 357355; totals are sums across seeds, means are thickness on present columns, coverage is fraction of columns, runs are contiguous horizontal presence in cells. Baseline is M5.2 `b549da0`; both sizes are measured. AIR and STONE are continuous in every column; BEDROCK is one cell in every column. Total counts for AIR/STONE/BEDROCK in 32 worlds: 8×8 baseline 4101760/5852928/25600, new 4099520/5375360/25600; 4×4 baseline 4099312/5849808/25600, new 4101568/5327488/25600.

| Size/seeds/material | Baseline total; mean/max; coverage; mean/max run | M5.2.1 total; mean/max; coverage; mean/max run |
|---|---|---|
| 8/32 soil | 83072; 8.99/16; 36.09%; 214.88/712 | 126432; 9.19/24; 53.75%; 259.62/800 |
| 8/32 sand | 153920; 14.38/32; 41.81%; 223/680 | 415656; 26.99/61; 60.16%; 290.57/800 |
| 8/32 gravel | 48320; 9.10/16; 20.75%; 171.35/488 | 223032; 17.93/44; 48.59%; 191.38/800 |
| 4/32 soil | 81600; 7.14/16; 44.67%; 259.91/800 | 130576; 9.15/24; 55.77%; 269.36/800 |
| 4/32 sand | 157328; 13.46/32; 45.66%; 238.53/800 | 431080; 27.21/61; 61.89%; 304.69/800 |
| 4/32 gravel | 51952; 7.33/16; 27.69%; 191.57/500 | 249288; 18.79/44; 51.81%; 204.06/800 |
| 8/357355 soil | 256; 8/8; 4%; 32/32 | 376; 2.94/7; 16%; 42.67/56 |
| 8/357355 sand | 6144; 13.71/32; 56%; 224/328 | 15880; 23.92/61; 83%; 332/488 |
| 8/357355 gravel | 0; 0/0; 0%; 0/0 | 320; 3.08/11; 13%; 34.67/48 |
| 4/357355 soil | 416; 4/4; 13%; 104/104 | 756; 2.78/7; 34%; 136/156 |
| 4/357355 sand | 5952; 12.20/32; 61%; 244/364 | 16944; 23.02/61; 92%; 245.33/556 |
| 4/357355 gravel | 0; 0/0; 0%; 0/0 | 508; 4.38/11; 14.5%; 38.67/64 |

Outcrop coverage/mean run/longest: 8×8 baseline 32 seeds 30.34%/172.62/504, new 17.66%/173.85/368; seed 357355 baseline 40%/106.67/136, new 17%/68/72. For 4×4: baseline 24.25%/155.20/484, new 13.89%/148.17/348; seed 357355 baseline 26%/69.33/116, new 0%. Seed 357355 AIR/STONE/BEDROCK totals: 8×8 124032/189568/800 → 122048/181376/800; 4×4 124176/189456/800 → 122384/179408/800. In 32 seeds, soil ≥16/sand ≥32/gravel ≥24 occur in 16/26/22 worlds for both sizes (baseline 8/6/0 for 8×8 and 6/3/0 for 4×4). Seed 357355 remains sand-dominated; no individual seed is guaranteed to contain all regions.

AIR/STONE mean/max depth across 32 seeds: 8×8 baseline 160.23/192 and 228.63/272, new 160.14/208 and 209.98/280; 4×4 baseline 160.13/192 and 228.51/272, new 160.22/208 and 208.11/280. For seed 357355: 8×8 155.04/192 and 236.96/264 → 152.56/208 and 226.72/264; 4×4 155.22/192 and 236.82/260 → 152.98/208 and 224.26/256. AIR, STONE and BEDROCK coverage is 100%, mean and longest run both 800 cells; BEDROCK mean/max depth is 1/1 in all cases.

Same JVM and methodology as the historical table below, measured separately before and after; mean/p95/max ms, first tick one observation:

| Size/version | Generate | First | Next 120 | Late | Edited | Write | Read |
|---|---:|---:|---:|---:|---:|---:|---:|
| 8 M5.2 | 12.780/25.967/25.967 | 20.428 | 2.421/7.547/26.451 | 1.570/1.597/1.645 | 1.687/1.905/3.447 | 6.773/7.887/7.887 | 6.612/7.588/7.588 |
| 8 M5.2.1 | 11.141/27.248/27.248 | 21.569 | 2.590/3.837/27.800 | 1.577/1.656/1.745 | 1.728/2.061/4.151 | 5.181/7.881/7.881 | 20.454/26.647/26.647 |
| 4 M5.2 | 9.898/20.684/20.684 | 3.650 | 2.789/3.325/3.691 | 2.787/2.875/4.588 | 2.718/2.794/3.142 | 6.064/6.189/6.189 | 6.127/7.288/7.288 |
| 4 M5.2.1 | 10.051/17.539/17.539 | 3.470 | 3.092/3.164/4.331 | 3.155/3.268/4.970 | 2.982/3.067/3.155 | 5.540/5.653/5.653 | 4.551/4.602/4.602 |

The 4×4 late p95 increased from 2.875 to 3.268 ms; increased loose material and settling are plausible contributors, but timings do not isolate causality. 8×8 initial and early maxima exceeded 16.67 ms. Synchronous I/O and generation may stall UI; no worst-case guarantee is claimed.

### Historical M5.1/M5.2

Windows, OpenJDK 25.0.5+8-LTS, 800×401, seed 12345; same `WorldGenerationBenchmark` and JVM configuration before and after the change, separate runs, `java -cp build/classes/java/main miningsim.benchmark.WorldGenerationBenchmark`. Two warmups and ten generation/serialization runs; one first tick; the following 120 ticks, then 20 warmup and 100 late ticks; 100 more ticks after removing a bottom rock block. Mean/p95/max ms; save/load use in-memory streams. Material counts below are before any tick, order AIR, STONE, SAND, WATER, WOOD, GRAVEL, SOIL, BEDROCK. Scenes evolve during measurement; warmup/JIT and OS noise can affect results.

| Version/size | Blocks | Material counts | Generation | First | Next 120 | Late | Edited | Save | Load |
|---|---:|---|---:|---:|---:|---:|---:|---:|---:|
| M5.1/8 | 3169 | 110784,202816,0,0,0,0,6400,800 | 14.293/29.421/29.421 | 20.312 | 1.958/2.340/12.614 | 1.422/1.643/1.812 | 1.534/1.786/3.654 | 5.894/8.621/8.621 | 21.972/28.304/28.304 |
| M5.2/8 | 3083 | 117184,197312,1600,0,0,1984,1920,800 | 12.288/25.463/25.463 | 20.614 | 2.215/2.599/17.851 | 1.603/1.749/2.253 | 1.713/1.907/3.865 | 5.822/7.690/7.690 | 17.286/27.081/27.081 |
| M5.1/4 | 12670 | 110880,202720,0,0,0,0,6400,800 | 9.956/20.408/20.408 | 3.600 | 2.790/3.248/3.700 | 2.633/2.843/4.640 | 2.467/2.553/3.117 | 6.170/6.669/6.669 | 4.951/5.265/5.265 |
| M5.2/4 | 12311 | 117280,196976,1728,0,0,1936,2080,800 | 8.849/10.683/10.683 | 3.748 | 2.909/3.257/3.335 | 2.874/3.029/4.674 | 2.719/2.744/3.280 | 6.231/6.403/6.403 | 5.030/5.252/5.252 |

The first 8×8 tick and one early-tick maximum exceeded 16.67 ms; late and edited mean/p95 increased in this sample. Different deposits and settling add cellular work, but these observations alone do not isolate the exact contribution or establish a worst-case bound. No 60 TPS guarantee is made.

## Manual acceptance

Generate multiple seeds via F8 for 8×8 and 4×4. Inspect soil, sand, gravel and rock outcrops; resume and watch loose material settle. Excavate rock below deposits, add water, observe collapse and drainage, then save/load the modified world. Check cancellation, F3/F4/F7, pause, step and editing. GUI checks are performed only by the user.

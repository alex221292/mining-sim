# M5.2 — geological layers

F8 uses the existing seeded surface profile and selected block size. For each block-aligned column the M5.1 surface is the first potential deposit row. Independent seed-salted value-noise channels, sampled at twice the terrain scale and interpolated with smoothstep, determine SOIL, SAND and GRAVEL thickness. Positive parts of the channels use thresholds -0.15, 0.05 and 0.15 respectively. Preset maximum thicknesses are 16, 32 and 16 cells; soil maximum is twice the existing `soilDepth`, capped at 16. Thicknesses are rounded to the nearest block-size multiple and clamped in soil/sand/gravel order to retain at least two rock rows above BEDROCK. The aligned layers are filled without gaps, in that order, above the aligned STONE boundary; a column with three zero thicknesses is a natural outcrop. All values are world cells, not block counts. Deposits have block-width horizontal steps and vertical quantization: this deliberately favors a simple continuous owned rock grid over sub-block geology.

STONE is created only by the existing API in row-major order down to BEDROCK; no caves or deep granular inclusions are generated. Material noise channels are independent of one another and of the surface seed channel. Same configuration and seed reproduce cells, IDs, next ID and states; 4×4 and 8×8 have similar world-space profiles but may differ due to quantization. Initial rock remains supported; loose materials may settle naturally after the paused scene starts. No hidden simulation ticks, physics changes or new format are introduced. MSIM v2 stores the resulting materials and blocks, not the seed; MSIM v1 remains LEGACY and RNG restarts after loading under the M5.1 contract.

## Tests

`GeologicalLayersTest` checks fixed seeds, bounds, outcrops, all layers, continuous cells and ownership for both sizes, 600-tick compact-world stability and conservation, excavation and sediment/water, and MSIM round-trips before and after edits. The existing `WorldGeneratorTest` includes paired 10,000-tick replay for both sizes with edits, water, persistence and material accounting. Its original constant-soil assertions were updated to check the new documented layer geometry and preserve conservation checks. Full suite: `gradlew.bat test build`. Manual Swing acceptance is pending.

## Exploratory performance

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

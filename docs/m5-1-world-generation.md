# M5.1 — deterministic world generation

F8 asks for confirmation and a signed 64-bit seed; an empty seed chooses a random seed, Cancel leaves the scene in place. The selected 4×4 or 8×8 size is used. Generation builds and validates an independent session before publishing it, starts paused, resets input state and positions the camera near the surface. The initial empty sandbox remains the default. F3/F4/F7, editing, pause, step and save/load remain available. Seed is displayed while the generated scene remains active; reset, reference scene and load clear it. MSIM v2 stores the actual world, not the generation seed; v1 remains LEGACY. Cellular RNG still restarts at seed 12345 after load, so uninterrupted replay is not guaranteed.

`WorldGenerationConfig` validates dimensions before allocating World: width and height-1 are multiples of block size, dimensions obey MSIM limits, the surface and amplitude leave air, soil and at least two block rows. `WorldGenerator` is headless. The preset is 800×401, base first-stone row 160, amplitude 32 cells, scale 96 cells and soil depth 8 cells. For each aligned block column, two deterministic hashed seed/index samples are blended by smoothstep value noise at the column centre; the first-stone row is rounded to the block grid and clamped to safe limits. Values are not independent per cell. Same seed and parameters yield the same materials, records and IDs; similar world-space sampling gives comparable macro terrain across sizes, not byte-identical terrain.

STONE blocks are placed through the authoritative fixed-block API in ascending row then column order, making IDs reproducible. Every column extends to the BEDROCK row, with no caves or unsupported initial blocks. SOIL occupies the configured number of cells directly above each column's rock surface. A fresh registry validates the resulting ownership before the session is returned. Soil can move naturally on subsequent ticks. Invalid configuration or generation failure cannot publish a partial scene. Generation is synchronous and may temporarily stall the UI.

## Verification

`WorldGeneratorTest` exercises both sizes, deterministic and differing seeds, grid and ownership, 600-tick rock stability, MSIM round-trip, editing, invalid parameters, cancellation preparation and 10,000-tick seeded paired replay with edits, water, sand, periodic reload and material/ownership accounting. GUI acceptance is pending; the agent did not launch Swing.

## Exploratory performance

Windows, OpenJDK 25.0.5+8-LTS, world 800×401, seed 12345; `java -cp build/classes/java/main miningsim.benchmark.WorldGenerationBenchmark`. Generation uses two warmups and ten measurements; full ticks use 20 warmups and 100 sequential samples; persistence is in memory with two warmups and ten measurements. Mean/p95/max ms; first tick is one observation, not a distribution. The edit scenario removes one lowest block before the measured sequence. File sizes are after that removal. Dynamic states evolve during measurement.

| Size | Blocks | Bytes | Generation | First tick | Steady tick | Edited tick | Write | Read |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 8 | 3169 | 384200 | 9.041/21.768/21.768 | 20.064 | 1.488/1.758/2.168 | 1.517/1.702/3.380 | 5.102/7.064/7.064 | 14.504/26.434/26.434 |
| 4 | 12670 | 574220 | 9.612/17.354/17.354 | 3.453 | 2.522/2.814/2.956 | 2.562/2.654/2.830 | 5.324/5.471/5.471 | 4.813/4.917/4.917 |

The single 8×8 first-tick observation exceeded 16.67 ms; warmup/JIT and run order may affect it, and the sample does not establish the cause or a worst-case bound. Synchronous generation and save/load can exceed a frame budget; no performance guarantee is claimed.

## Manual acceptance

1. F8 generate 8×8, enter a seed and inspect the paused terrain and F3.
2. Regenerate with the same seed, then a different seed; compare the surface.
3. Use F7 and generate 4×4 with the same seed; inspect terrain and controls.
4. Remove rock, add sand and water, resume and single-step; check material movement and support.
5. Save and load the generated scene; verify blocks, editing and that the prior seed is not displayed.
6. Cancel F8 and verify the old scene, then use C and F4 to check ordinary sandbox/reference controls.

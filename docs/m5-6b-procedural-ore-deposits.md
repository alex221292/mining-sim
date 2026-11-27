# M5.6b — procedural ore deposits (pending manual acceptance)

Seeded independent channels create warped elliptical masses and elongated veins, sampled at block centres in world cells (one cell is 0.1 m). The initial six iron, eight copper, five silver and two gold candidates per 800×401 are distributed over the whole world; candidate count scales with world area and caves and terrain can remove all candidates of a component. Each component uses `seed ^ (0x73a89d25e4c61b0f * stableId)` with a separate `SplittableRandom`. Iron half-length is 70–170 cells and half-width 35–70; other half-lengths are 45–145 and half-widths 5–17. Orientation and sinusoidal bend vary per candidate. Peaks are respectively 400000, 80000, 500 and 50 ppm. A radial gradient and correlated sinusoidal modulation create concentration variation; overlapping candidates use the maximum per component. Different ore channels may overlap; their maximum possible total is 480550 ppm, below the composition limit. The immutable, canonical M5.6a composition validates the result, and empty blocks share the singleton.

Ores are assigned at original block placement rather than after caves: this preserves the original immutable block records and IDs without an assignment API or recreation. Removed cave blocks remove their compositions with them; remaining ore does not affect carving, water or physics. No random numbers are drawn from other generation stages. No host preference is implemented. The field is not evaluated by the physics tick. MSIM v4 and v1–v3 reading are unchanged.

F9 cycles OFF, IRON, COPPER, SILVER and GOLD. The overlay reads current block compositions and moves with blocks; it is a sandbox diagnostic, not a future prospecting mechanic. MSIM v4 stores compositions, not deposit geometry. F3 remains available.

## Headless calibration

`gradlew.bat oreDepositCalibration`: 800×401, seeds 0–15, 431039, 357355, 12345. The task prints each world's surviving block counts, ppm min/mean/max, orthogonal component count and largest size and host counts in STONE/GRANITE/LIMESTONE/MARBLE order. Components are not necessarily individual geological deposits. Aggregate shares below are weighted by the number of blocks; because all blocks have the same area within a world, block and rock-area shares are identical.

| Size | Ore | Share | Worlds | ppm min/mean/max | Components total | Largest |
|---|---|---:|---:|---|---:|---:|
| 4 | IRON | 27.475% | 19 | 3/142650/399411 | 88 | 2466 |
| 4 | COPPER | 6.884% | 19 | 4/27097/79624 | 113 | 846 |
| 4 | SILVER | 4.530% | 19 | 1/168/498 | 76 | 498 |
| 4 | GOLD | 1.528% | 16 | 1/16.57/48 | 33 | 278 |
| 8 | IRON | 27.444% | 19 | 13/142886/397932 | 89 | 633 |
| 8 | COPPER | 6.862% | 19 | 1/27148/78980 | 138 | 173 |
| 8 | SILVER | 4.520% | 19 | 1/168/489 | 92 | 122 |
| 8 | GOLD | 1.564% | 16 | 1/16.42/48 | 39 | 72 |

Empty/multicomponent shares: 63.295%/3.554% at size 4 and 63.304%/3.546% at size 8. Ore-bearing host counts for size 4: IRON 21433/18438/8784/1422, COPPER 4624/5540/2132/252, SILVER 3071/3428/1532/226, GOLD 1082/1141/556/6. Size 8: IRON 5509/4630/2161/354, COPPER 1212/1390/507/55, SILVER 758/886/389/51, GOLD 278/307/135/1. Counts reflect host geology and do not imply preference. Parameter calibration reduced iron candidates from nine to six after observing excessive coverage; this is not a visual evaluation.

## Verification and timing

The actual M5.6a baseline `generationParityProbe` and current implementation agree for six 128×129 cell maps and block counts (seeds 1, 71, 12345; both sizes). The recorded pre-change 38-world v3 fingerprint also matches after discarding only the v4 ore section, covering cells, block IDs, positions, host materials and initial physical records. Headless tests compare generated ore-bearing and otherwise identical ore-free worlds after 600 ticks for seeds 8 and 12345, both sizes, check per-ID composition retention, ownership, v4 round-trip, water, deterministic sampling and connected variable ore. This is representative, not exhaustive.

`gradlew.bat test --tests 'miningsim.block.OreContentTest' --tests 'miningsim.generation.OreDepositsTest' --tests 'miningsim.generation.NaturalWaterGeneratorTest'` and `gradlew.bat test build` passed on Corretto 25.0.5.

`gradlew.bat worldGenerationBenchmark`, Windows Corretto 25.0.5+10, 800×401 seed 12345: generation warmup 2, measurements 10; early 120 ticks and late 20 warmup/100 measured ticks; ms mean/p95/max:

| Size | Generation | First tick | Early | Late |
|---|---|---:|---|---|
| 8 | 15.685/33.482/33.482 | 22.391 | 2.674/6.660/11.218 | 1.941/1.998/2.517 |
| 4 | 21.776/24.248/24.248 | 7.386 | 4.530/6.181/7.279 | 4.091/4.167/4.657 |

Ore sampling is inline and not timed separately. This uncontrolled run does not establish a regression or speedup. The first 8×8 tick exceeds the 16.67 ms 60 TPS budget; maxima are not worst-case bounds.

## User-only manual acceptance

In each block size generate seed 357355 with F8. Cycle F9 through all four ores and OFF, inspecting varied concentrations, shapes and overlap; seed 12345 is another example for all four. Seed 1 has no gold. Use F3 to inspect ppm; resume and verify falling highlighted blocks move with their composition, including near water. Save, reload, and inspect the same blocks and overlay. Check that caves, water and block borders remain legible. The agent did not run the GUI. No discovery mechanic, mining, processing, fog or ore textures are implemented.

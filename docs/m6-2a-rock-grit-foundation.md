# M6.2a — Rock grit foundation

Status: accepted by the user. M6.2b, atomic block-to-grit extraction, is not implemented. Mining at 100% still leaves the block unchanged. Generation and support rules are unchanged.

## Identity, storage and conservation

Material IDs 0–10 are unchanged; IDs 11–14 represent STONE, GRANITE, LIMESTONE and MARBLE in `GRIT` form. `rockSubstance()` and `rockForm()` distinguish independent substance and form. Grit is granular, not fixed rock, has no block ID, structural strength or support contribution. Existing gravel has no implied rock identity.

Each chunk retains its byte material array. Only a chunk with nonempty grit ore has a lazily allocated array of composition references; empty entries are null. References point to immutable `OreComposition` values, which may be shared among cells. Removing the last nonempty entry releases that chunk's reference array; replacing and clearing cells discard their metadata. There is no global interning table or unbounded palette lifetime. A populated chunk uses 4096 references (typically about 16 KiB with compressed references, excluding array header; JVM-dependent), regardless of how many of its cells have ore. This is an estimate, not a heap measurement. Ordinary chunks allocate no ore array. Composition lookup and movement do not calculate concentrations.

Granular movement uses sand rules: fall, diagonal fall, swap with water; existing gravel and soil rules are unchanged. A grit move swaps both material and composition before notifying observers, including across chunk boundaries. The historical rigid recovery path refuses worlds containing grit instead of transferring incomplete metadata; it is not part of fixed-block gameplay. Different compositions never mix. Brush replacement creates ore-free grit. Each component is a mass fraction in integer ppm (1 ppm = 0.0001%); the existing validated immutable composition enforces unique stable Ore IDs, positive fractions and a total at most 1,000,000 ppm.

The active cellular model does not assign physical mass to cells. Its contract is conservation of cell count per substance and the exact sum of each component's ppm across equal-volume cells. A future 4×4 or 8×8 block conversion must create 16 or 64 cells with the same substance and **unchanged** ppm per cell, not divide ppm by cell count. This discrete volume/composition invariant is not a physical mass simulation; no new densities or packing rules are introduced.

## Controls and diagnostics

Normal digits: 2 WATER, 3 STONE fixed block, 4 WOOD, 7 GRANITE fixed block, 8 LIMESTONE fixed block, 9 MARBLE fixed block. Shift+1 SAND, Shift+3 STONE GRIT, Shift+5 GRAVEL, Shift+6 SOIL, Shift+7 GRANITE GRIT, Shift+8 LIMESTONE GRIT, Shift+9 MARBLE GRIT. Normal 1/5/6 and Shift+2/4 are unassigned. Selection uses key codes plus Shift, not layout-dependent characters. F3 reports grit form, substance and ore in ppm and percent; F9 colors cells from their current composition. The F4 cycle retains prior scenes and adds a paused grit scene with four rocks, water, granular materials, blocks, and adjacent empty / IRON 120000 ppm / IRON 120000 + COPPER 25000 + SILVER 80 + GOLD 5 ppm cells. It supports both block sizes.

## Persistence

MSIM v5 retains the v4 header, material cells, block records and independent block ore section. It appends a deterministic composition palette, then sparse grit-cell records before the CRC32. All numbers are big-endian: signed int palette count, each entry's unsigned byte component count and repeated unsigned byte Ore ID / signed int ppm; signed int cell count, then row-major signed int cell index and 1-based signed int palette reference. Empty composition has no record. Palette order is first occurrence in row-major order. CRC and atomic fixed-session file replacement remain in force. Bounds and material, ore, composition, ownership, duplicate and reference validation precede publication. Readers retain v1–v4 semantics; old versions reject new material IDs. V1 cannot write grit. Mining work and RNG are not saved.

## Headless benchmark

Windows, Corretto 25.0.5+10, `gradlew.bat gritBenchmark worldGenerationBenchmark`; single uncontrolled sequential run, no concurrent build. Grit benchmark: 400×401, seed 12345; fixed order sand, empty grit, one component, four components, 256 distinct four-component values; identical counts and initial geometry with water and stepped obstacles. Active warmup 10 / measured 40 ticks; then 400 warmup / 40 measured ticks as an approximate settled phase, not a proof that every particle is settled. Tick milliseconds mean/p95/max:

| cells | sand | empty | one | four | 256 compositions |
|---|---|---|---|---|---|
| 10k active | 1.374/2.867/2.986 | 1.553/1.744/3.040 | 1.619/1.903/2.981 | 1.556/1.573/1.671 | 1.992/2.743/2.888 |
| 10k late | 1.652/1.674/1.735 | 1.548/1.562/1.581 | 1.547/1.567/1.573 | 1.633/1.683/1.710 | 1.689/1.931/2.398 |
| 50k active | 3.186/3.863/4.270 | 2.278/2.397/2.975 | 2.361/2.413/2.417 | 3.887/4.343/4.456 | 2.386/2.408/2.418 |
| 50k late | 2.469/2.509/2.539 | 2.597/3.483/3.719 | 2.490/2.557/2.666 | 2.502/2.554/2.822 | 2.493/2.549/2.627 |

The later `gradlew.bat gritBenchmark` additionally measures an independently initialized, fully packed, stationary fixture: full-width rows resting on bedrock, identical counts and composition scenarios, 20 warmup / 40 measured ticks. It contains no water; the active fixture above covers water and obstacles. Settled mean/p95/max milliseconds:

| cells | sand | empty | one | four | 256 compositions |
|---|---|---|---|---|---|
| 10k settled | .588/.592/.593 | .570/.575/.575 | .577/.595/.597 | .570/.576/.577 | .573/.581/.588 |
| 50k settled | 1.109/1.118/1.132 | 1.116/1.143/1.352 | 1.121/1.131/1.132 | 1.106/1.116/1.135 | 1.151/1.228/1.337 |

World generation 800×401 seed 12345: generation warmup 2 / 10 runs, early 120 ticks, steady warmup 20 / 100 ticks. Size 8 generation 16.110/34.318/34.318, early 2.808/6.660/11.678, steady 1.932/1.977/2.032 ms; size 4 generation 22.409/25.145/25.145, early 4.492/5.989/6.727, steady 4.123/4.158/4.346 ms. Fixed ordering, JIT, GC, changing activity and OS noise limit comparisons; these numbers do not establish absence of regression, measure rendering or bound worst-case times.

After the v5 reader validation change, `gradlew.bat worldGenerationBenchmark` was repeated under the same configuration: size 8 generation 16.430/36.708/36.708, early 2.764/6.298/11.761, steady 1.963/1.976/3.082, save 10.100/28.146/28.146, load 27.928/35.936/35.936 ms; size 4 generation 21.396/23.649/23.649, early 4.432/6.237/6.372, steady 3.985/4.038/4.290, save 13.915/27.163/27.163, load 11.227/12.426/12.426 ms. Gradle reported another busy daemon on startup; external activity cannot be excluded. In particular the size-8 load mean differs substantially from the earlier 7.222 ms and warrants controlled follow-up rather than a no-regression claim.

## Verification

On the same JDK, `gradlew.bat test --tests 'miningsim.block.GritFoundationTest' --tests 'miningsim.app.BrushKeysTest'` and `gradlew.bat test build` passed. The v4 fixture was produced with `gradlew.bat m61aV4Fixture`, compiling the actual baseline writer over the current unchanged fixture setup. Tests cover replay and integer concentration totals, cross-chunk movement, water displacement, diagonal motion, both block sizes, mixed block/grit persistence and malformed references. Historical tests that explicitly assumed the writer emitted v4 were adjusted to v5 and retain the old-format checks. GUI was not run.

## User manual acceptance

The user confirmed that all seven proposed scenarios passed and that the visual quality and physical behavior met expectations. The agent did not launch the GUI. The accepted checks covered:

1. Normal and Shift+digit keys, saving, loading and camera controls.
2. All four grit types, falling and pile formation.
3. Interaction with water, sand, gravel, soil, wood, bedrock and fixed blocks.
4. The GRIT diagnostic scene and neighboring cells with different compositions.
5. Composition preservation during movement, and F3 and F9 diagnostics.
6. Save/load of a mixed scene with composition preserved.
7. Both block sizes, world generation, existing mining and crack visualization. A block at 100% mining progress remains intact.

The next planned stage is **M6.2b — Physical Block Extraction & Grit Conversion**: destroy a fully mined fixed block, atomically transfer its full volume and all ore components to same-rock GRIT, let the grit settle using existing cellular physics, and update surrounding block support. It has not been implemented.

### Detailed checks

1. Check Shift+digits, unassigned keys and Ctrl+S/O, camera and existing controls.
2. Paint all four grit rocks and compare them with fixed blocks.
3. Observe falling, diagonal movement and piles.
4. Check water, sand, gravel, soil, wood, bedrock and block interactions.
5. Cycle F4 to the grit scene and inspect adjacent different compositions.
6. Check F3 before and after movement, including ppm and percentages.
7. Cycle F9 and verify moving cells retain their ore overlay.
8. Save/load a mixed block/grit scene and repeat diagnostics.
9. Repeat for 4×4 and 8×8 blocks.
10. Verify normal F8 generation.
11. Verify existing mining progression and crack visualization; 100% must not destroy a block.

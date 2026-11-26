# M5.5a — fixed-block rock identity

Four fixed-block rocks share exactly the same support, delay, falling and water rules. Material byte IDs remain stable: STONE 1, GRANITE 8, LIMESTONE 9, MARBLE 10. A block entry owns its immutable material; every cell in its area must match. The material maps to the existing M4.3d `SubstanceId`, independently of its fixed BLOCK representation and static physical category. No per-cell object is allocated. Existing historical fragment, arbitrary rigid-body, inventory and mass abstractions are not integrated. Future GRIT means granular cells of the same substance and ITEM means an inventory representation; neither is implemented here. Historical densities are not applied to block physics.

Keys 3, 7, 8, 9 select STONE, GRANITE, LIMESTONE, MARBLE; F7 changes block size, F8 generates the existing STONE-only terrain. F3 identifies the hovered block. Overlapping strokes do not recolor blocks. Individual strength, mining and procedural rock diversity are not implemented.

## Persistence

MSIM v3 keeps the v2 layout, but extends the previously STONE-only substance byte: 0 STONE, 1 GRANITE, 2 LIMESTONE, 3 MARBLE. Cells independently store the IDs above; load and save reject mismatch, unowned rock, invalid IDs and other pre-existing invariant violations. The version changes because v2 specified only substance 0. CRC, atomic save and RNG restart at seed 12345 are unchanged.

| File | New application | M5.4 application |
|---|---|---|
| MSIM v1 | LEGACY, no migration | LEGACY |
| MSIM v2 | fixed blocks, STONE | fixed blocks, STONE |
| MSIM v3 | fixed blocks, four rocks | rejected as unknown version |

## Measurements

`gradlew.bat blockSupportBenchmark`, Corretto 25.0.5+10, 800×401, seed 12345, 20 warmup and 100 sequential measured ticks, milliseconds mean/p95/max. The water scene evolves during warmup; these single-run observations include JVM and OS noise, not worst-case guarantees.

| Scene | 8×8 | 4×4 |
|---|---|---|
| 1000 STONE | 0.829/1.020/1.592 | 0.866/0.903/0.992 |
| 1000 mixed | 1.290/1.664/1.769 | 0.858/0.869/0.884 |
| STONE falling/water | 1.188/1.553/1.615 | 0.841/0.926/0.942 |
| mixed falling/water | 1.184/1.548/1.599 | 0.844/0.935/0.950 |

The 8×8 mixed static result is higher in this run; no controlled attribution or general performance guarantee is implied.

## Verification

`gradlew.bat test build` passes with Corretto 25. Focused tests cover mixed support and support removal/restoration, paired replay, granular obstruction, each rock displacing water with material conservation, mixed-state persistence and rejection of invalid data. Existing MSIM v1 LEGACY regressions remain enabled; a fixture emitted by the actual M5.4 v2 writer is also read. The baseline comparison uses `gradlew.bat generationParityProbe -Pm54Baseline` and `gradlew.bat generationParityProbe`: only the baseline fixed-block and serializer classes are compiled from Git; generation and other dependencies are unchanged. SHA-256 of row-major cell IDs matched for 128×129, seeds 1, 71 and 12345, both sizes. The same fingerprints are regression-tested; generation takes no brush parameter. This finite sample is not an exhaustive proof for all seeds.

## Manual acceptance (user only)

For each block size draw all four rocks, inspect colors and outlines, build a mixed construction and remove its support, drop rocks into water, inspect F3, and save/load the scene. Check F8 still generates the usual STONE world. The agent did not launch the GUI.

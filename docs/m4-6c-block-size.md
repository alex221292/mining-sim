# M4.6c — configurable fixed-block size

Each fixed-block session has one immutable `rockBlockSizeCells`: **8** by default or experimental **4**. Sizes cannot be mixed within a session. F7 asks for confirmation, then replaces the current scene with an empty session of the other size; no conversion is performed. Reset and compatible loads retain the selected size. The HUD displays the size and marks 4×4 experimental. Existing pause, single-step, camera, F3, painting and erasing controls remain available. The `.msim` format is unchanged and fixed-block saving remains disabled.

Both sizes use the same owner-thread phases, grid snapping, bottom-anchored `yOffset=(height-1) mod size`, atomic World batches, eight-tick fall delay and water displacement planner. Movement is one block size per tick. BFS remains radius 32, 8192 visits and 32768 checks; the vertical vent remains bounded to 128 cells per column. A 4×4 move displaces at most 16 WATER cells rather than 64; the work limits are unchanged, not scaled. SAND, GRAVEL, SOIL and other non-water material remain obstacles. Partially fitting edge strips cannot hold a block.

Parameterized headless tests cover both sizes, strokes, pan/zoom coordinate conversion, deletion, support loss and restoration, five blocks in deep water, overflow, closed and reopened basins, local rollback, granular materials, chunk and world boundaries, replay and 10,000 ticks per size. Existing 8×8 tests are retained and the default/explicit 8×8 replay is compared. M4.6b.2 manual acceptance was reported as 4/4 by the user. **M4.6c has not yet been manually accepted.**

## Measurements

Windows, OpenJDK 25.0.5+8-LTS OpenJDK 64-Bit Server VM. `java -cp build/classes/java/main miningsim.block.BlockSizeBenchmark SIZE 10`: fresh 800×401 worlds, seed 12345, two warmups and 10 independent one-tick samples, separate JVMs; mean/p95 milliseconds, block phase and full tick (including cellular, excluding repaint). Moving scenes prime the same eight delay ticks without cellular movement. The baseline is commit `d803743` with the same benchmark source and JVM. Small samples include significant JVM/OS noise.

| Scene | Baseline 8 block / full | New 8 block / full | New 4 block / full |
|---|---|---|---|
| 100 static | 0.079/0.111; 0.512/1.533 | 0.078/0.120; 0.534/1.969 | 0.062/0.097; 0.829/4.093 |
| 1000 static | 0.488/0.528; 0.812/0.857 | 0.463/0.493; 0.779/0.829 | 0.386/0.511; 0.693/0.813 |
| 5000 static | 1.506/1.885; 1.814/2.210 | 1.493/1.786; 1.819/2.115 | 0.692/0.790; 0.997/1.100 |
| 10 falling | 0.206/0.321; 0.514/0.630 | 0.252/0.789; 0.575/1.120 | 0.143/0.227; 0.454/0.529 |
| 10 entering water | 1.164/1.458; 2.830/5.666 | 1.276/1.407; 3.035/6.418 | 0.332/0.386; 1.652/4.751 |

Equal area, 64,000 STONE cells: new 1000 blocks 8×8 **0.463/0.493 ms block, 0.779/0.829 ms full** versus 4000 blocks 4×4 **1.022/1.118 ms block, 1.339/1.450 ms full**. The 4×4 scene is more expensive at equal area. Non-monotonic sample results and 8×8 water/full-tick p95 regression are disclosed; they are not proof of a stable speedup.

## Manual Swing check

Press F7 and confirm the scene replacement; verify HUD size and camera/pause/single-step. In each mode draw a fast horizontal and diagonal stroke, erase one middle block, remove and restore stack support, then drop five blocks into an open pool and test overflow and sand. Check F3, water conservation visually and continued input; repeat after switching back to 8×8. Saving should remain unavailable.

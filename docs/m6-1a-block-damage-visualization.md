# M6.1a — Block damage visualization

Mining work remains the sole source of mining damage, keyed by stable block ID. The renderer reads it on the Swing EDT, which also runs the timer, ticks, input and painting. Stopping, switching, moving, removal and session replacement therefore follow M6.1; save/load resets temporary work. A ready block remains intact. No geometry is generated in the physical tick.

`BlockDamage` contains graphics-independent immutable line segments in normalized 0–100 coordinates, each tagged with its first visible stage. A stage includes all earlier segments. The common pattern is independent of rock type and may also be consumed by a future GPU texture atlas or structural-fatigue visualization; structural damage is not implemented or combined with mining. Work 0 maps to stage 0; otherwise stage is max(1, floor(10 × work / required)), using long arithmetic; 100% is stage 10.

Java2D `BlockDamageMasks` rasterizes eleven transparent 128×128 images once per sandbox and reuses them for every block and zoom. World pixels are followed by F9 ore overlay, cracks, block outlines, other diagnostic overlays and HUD/progress bar. The existing visible-block traversal is reused; no separate world scan or per-block image is created. Images scale with the camera; very small blocks may lose detail naturally, without an explicit cutoff. The pattern is fixed, without rotations or variants. Visual appearance, F9 readability and FPS await user acceptance; no GPU backend or structural effects were added.

## Verification

Windows Corretto 25.0.5+10: focused `gradlew.bat test --tests 'miningsim.render.BlockDamageTest' --tests 'miningsim.block.MiningTest'` and `gradlew.bat test build` passed; full test task executed, compilation was UP-TO-DATE. GUI not launched. One earlier full run timed out at 120 seconds; the repeat completed in 3m28s.

`gradlew.bat worldGenerationBenchmark`: 800×401, seed 12345, generation warmup 2 / 10 runs, 120 early ticks, steady warmup 20 / 100 runs; milliseconds mean/p95/max:

| block | generation | first tick | early | steady |
|---|---|---|---|---|
| 8 | 19.315/41.160/41.160 | 23.859 | 2.767/7.640/14.572 | 1.954/2.016/2.340 |
| 4 | 22.482/24.633/24.633 | 12.093 | 4.987/7.460/12.295 | 4.130/4.292/5.604 |

Single uncontrolled headless run without active mining; concurrent build may affect results. It does not measure rendering, mask preparation or FPS and is not a regression proof.

## Manual acceptance (pending)

1. Mine all four rocks and observe progressive cracks.
2. Stop and resume; partially damage multiple blocks and switch targets.
3. Reach 100% and confirm the cracked block remains.
4. Pause and single-step; test 4×4 and 8×8.
5. Pan and zoom; check EDIT/MINING, F3, F4 and F9 readability.
6. Remove and recreate a block at the same coordinates.
7. Save/load, clear, F7 size switch and F8 generation: temporary cracks reset.
8. Check cracks and visual performance in a generated world.

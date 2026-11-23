# M4.6b.2 — deterministic fall delay

Each STATIC, unpinned block waits eight fixed ticks after losing solid support; it may move on the ninth tick. Solid support resets that block's counter. Once FALLING, it continues without another delay until it meets a solid obstacle. An unsuccessful water plan keeps the elapsed wait and is retried on a subsequent tick; other blocks, input and cellular simulation continue. Each block has its own counter, so upper blocks may start later than a lower block. A waiting block reports `WAITING_FOR_FALL` in cached diagnostics. The water search and limits are unchanged.

The user's manual M4.6b.1 checks confirmed drawing, erasing, falling after support removal, water displacement and overflow. A completely closed basin cannot normally be constructed with horizontally unconnected blocks; headless fixtures exercise that conservative local obstruction. M4.6b.2 itself still needs manual Swing acceptance.

Tests cover exact delay, cancellation on restored support, independent timers, continued falling, water and input during waiting, open and closed pools, reopening, overflow and water conservation. `StabilizationStressTest` runs two fixed-seed 96×81 sessions for 10,000 ticks with open and sealed pools, repeated block changes, multiple falling blocks, sand, repeated local obstructions, matter accounting and deterministic replay. Tests which previously assumed movement on the first tick were updated for the new explicit delay, retaining their final position and conservation assertions.

## Comparable measurements

Windows, OpenJDK 25.0.5+8-LTS, OpenJDK 64-Bit Server VM; `java -cp build/classes/java/main miningsim.block.StabilizationBenchmark 20`, one JVM per version, seed 12345, three warmup and 20 measured fresh fixtures. Baseline `0c654fd` and M4.6b.2 use identical scenes: 1000 static blocks in 800×161; 10 falling blocks or 10 blocks entering water in 160×161. The eight wait ticks are applied to the block system alone before measuring the first actual movement, so the measured World and water inputs match the baseline. Full tick includes cellular simulation, excludes repaint. Wall-time observations include JVM/OS noise.

| Scene | Baseline block mean/p95 ms | New block mean/p95 ms | Baseline full tick mean/p95 ms | New full tick mean/p95 ms |
|---|---:|---:|---:|---:|
| Static | 0.476 / 0.650 | 0.478 / 0.640 | 0.615 / 0.803 | 0.609 / 0.771 |
| Falling | 0.712 / 0.788 | 0.686 / 0.758 | 0.748 / 0.827 | 0.719 / 0.791 |
| Water | 1.089 / 1.962 | 1.467 / 1.943 | 1.393 / 2.282 | 1.760 / 2.269 |

The water mean increased in this small sample; these results do not establish a speedup or absence of regression.

## Manual Swing check

Create a supported stack, remove its bottom block and check the short delay and subsequent fall. Restore support during the delay. Draw and erase while water and sand move; drop at least five blocks into a deep pool and overfill it to check overflow. Confirm F3 diagnostics and that the sandbox remains responsive.

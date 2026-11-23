# M4.6b.1 — open-pool obstruction

For the later fall-delay contract, see [M4.6b.2](m4-6b2-stabilization.md).

An 8-cell-wide water shaft between fixed block walls reproduced a false obstruction: the old search could not cross the falling block's currently occupied source volume, even though that volume is vacated by the same batch. With 32-cell search radius, a deep column can also put the free surface farther away. Increasing the existing BFS budgets alone cannot cross the source block.

The deterministic planner now treats source cells as virtual vacancies and merges their STONE-to-WATER changes with the source batch; it never emits duplicate cell changes. Virtual vacancies are used only when AIR is reachable in the search or by a bounded vertical vent scan through WATER and aligned unpinned blocks. The vent scans at most 128 cells per column and shares the existing 32768-check budget. BFS retains its radius 32, 8192-visit and 32768-check limits. This is a discrete gameplay rule, not a hydrodynamic or pressure model; a sealed, completely filled container still obstructs the move. Limits and geometry can conservatively reject other open configurations. Bottom-first processing allows an unsupported stack to follow a moving lower block. SAND, GRAVEL and SOIL remain obstacles.

Each block caches its last reason and search work: `SUPPORTED_BY_SOLID`, `BLOCKED_BY_STONE`, `WATER_DISPLACEMENT_BLOCKED`, `WATER_SEARCH_BUDGET_EXCEEDED`, `INVALID_OR_STALE_PLAN`, `PINNED`, or `NONE`. F3 shows one blocked block and the block under the cursor; no World scan is performed for these diagnostics. Invalid ownership remains an error, not a local water obstruction.

Headless regressions cover a narrow open shaft, five sequential blocks, a moving stack, sealed volume and reopening, granular materials, and 10,000 deterministic seeded ticks. Verification: `gradlew.bat test build` and `git diff --check`.

## Benchmark

Windows, OpenJDK 25.0.5+8-LTS, one JVM per benchmark. Wall time measures the block update only, not cellular simulation or repaint. Static 800×401, 20 warmups, 100 ticks: baseline/new mean/p95 ms for 100 **0.038/0.073 → 0.039/0.070**, 1000 **0.218/0.287 → 0.438/0.673**, 5000 **0.926/1.039 → 1.107/1.647**. New last-tick counts: zero water attempts/moves and all blocks `SUPPORTED_BY_SOLID`.

Fresh one-step water scenes, height 65, 2 warmups and 10 measured runs: baseline/new mean/p95 ms: 10 open blocks **2.055/3.109 → 2.083/2.997**, 50 open **2.379/2.568 → 2.538/2.778**, sealed 10 **2.150/3.709 → 4.426/12.393**. New totals over ten runs: open 10: 100 attempts/100 moves, open 50: 500/500; each max 123 visits and 492 checks. Sealed: 100 attempts, zero moves, 100 `WATER_DISPLACEMENT_BLOCKED`, max 2024 visits and 7840 checks. Other refusal counts were zero. Baseline per-reason counts and search work were not instrumented; original aggregate obstruction counts were 0, 0, 100. Timings are noisy and the sealed and static regressions are disclosed.

## Manual Swing check

1. Build two vertical block walls and a solid bottom; fill with WATER while leaving an open surface.
2. Drop at least five blocks in sequence; check submersion and stacking, then remove lower support and check subsequent movement.
3. Enable F3 and hover stopped blocks to inspect their reasons. A fully sealed, water-filled container may report `WATER_DISPLACEMENT_BLOCKED`; after an opening, it should retry.
4. Add SAND, GRAVEL and SOIL and check their existing behavior, painting, erasing and continued cellular ticks.

This manual check has not been performed by the agent.

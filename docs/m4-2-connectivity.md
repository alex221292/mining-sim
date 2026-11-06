# M4.2 rock connectivity reference

`RockConnectivity(World)` attaches the world's single `ChangeSink`, replacing any previous sink. World remains the authority for cells. This subsystem is headless and is not automatically attached to `Simulation`; the owner explicitly advances it on the same thread as world mutations. Replacing or detaching the sink makes all analysis APIs fail with `IllegalStateException`; reattaching it through `World.setChangeSink` invalidates the result and discards all broken bonds, because changes may have been missed. Existing sink consumers must be coordinated by the owner; there is no event bus.

An orthogonal STONE–STONE adjacency is implicitly intact unless its canonical bit is set in a `BitSet`: `2 * min(rowMajorIndex) + orientation`, with orientation 0 for horizontal and 1 for vertical. `bondKey` validates coordinates and is independent of endpoint order. `breakBond` requires two adjacent stone cells and never modifies materials; breaking an already broken bond returns false. A stone occupancy change clears all four incident broken bits in constant time, so replacement never inherits an old break. Bulk invalidation discards all broken bits. Breaks are not persisted.

Components contain only stone, following intact bonds. The ID is the smallest row-major cell index, and component ordinals are increasing IDs. A direct orthogonal STONE–BEDROCK contact connects the component to the foundation, propagated through intact stone bonds; bedrock is not a component member. Legacy worlds have no bedrock. **Foundation connectivity is not mechanical stability**, strength or load capacity.

`advance(budget)` accepts a positive maximum number of logical work units and returns units actually used. One unit scans one cell in row-major order or expands one queued stone cell, examining at most four neighbors in left/right/up/down order. The iterative flood fill never recurses. `pending()` reports incomplete work; component queries throw while pending. `revision()`, `restarts()` and `totalWork()` expose diagnostics. Relevant edits during a partial analysis discard its progress; unrelated edits, no-ops and rejected writes do not restart it. Repeated edits are coalesced into one dirty state, while every stone edit immediately clears incident broken bonds. There is no unbounded event queue and no stale result is published. A continually edited world may starve the full rebuild; increasing `restarts()` exposes this, but does not guarantee fairness. Pausing edits eventually permits completion. There is no wall-clock budget or dependency on activity tiles or camera.

The reference uses five `int[]` and one `boolean[]` sized to world cells (approximately 21 bytes per cell plus array headers), plus a dynamically sized broken-bond bitset (up to two bits per cell) and the authoritative world. Arrays are allocated on construction; no per-bond or per-cell traversal objects are allocated. Generation wrap requires a full marker-array reset after 2³² analyses. Each structural edit causes a full scan and flood fill, not a local update; future incremental regions are an optimization boundary and must preserve reference results. No stress, damage, mechanics, saving of breaks or GUI integration is provided.

## Measurement

`gradlew.bat rockConnectivityBenchmark`, Windows 11, Java 25.0.5, one exploratory run, 800×600 legacy world, initial 480,000 stone cells, budget 10,000 units/call. Times are secondary diagnostics, include JIT effects, and are not microbenchmark results:

| Scenario | Work units | Advance calls | Time |
|---|---:|---:|---:|
| Initial dense analysis | 960,000 | 96 | 20.674 ms |
| One stone removal (479,999 stone) | 959,999 | 96 | 17.446 ms |
| 1,000 concentrated toggles, final 479,999 stone | 959,999 | 96 | 12.299 ms |
| Non-structural AIR→WATER | 0 | 0 | 0.022 ms |
| 100,000 idle advances | 0 | 100,000 | 0.642 ms |

Edit times are outside the timed analysis. The arrays above are estimated, not measured allocations. Tests include cross-chunk bonds, budget-one completion, restart and stale-result rejection, replacement of broken bonds, large iterative components, and a seeded test-only flood-fill comparison.

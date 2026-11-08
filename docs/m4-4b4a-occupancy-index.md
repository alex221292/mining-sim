# M4.4b.4a — rigid-body occupancy index

`RigidBodyOccupancyIndex` is a headless derived cache. `World` owns cellular materials, `FragmentRegistry` owns fragment material and `RigidBodySystem` owns motion; the index reads world dimensions and body transforms only. It neither installs a change sink nor changes World, registry or bodies. Sleeping bodies are indexed like active ones. There is no cellular blocking, brush, rendering or save integration in this milestone.

The caller explicitly invokes `rebuild(system, generation)` after a successful physics tick or registry synchronization, on the same simulation owner thread and without concurrent mutation. A velocity assignment alone changes no geometry. A failed physics tick leaves the existing snapshot untouched; the caller must not publish transient states during a tick. Rebuild also accepts an ordered or reordered body list and rejects duplicate IDs. No automatic staleness detection is claimed: `generation` is caller-supplied, not a physics clock. Every successful publication increments the checked monotonic revision; a failed build leaves the previously published snapshot and diagnostics unchanged. Initial revision/generation are zero and initial occupancy is empty. Old snapshots remain valid after subsequent builds.

World cells are `[x,x+1] × [y,y+1]` in cell coordinates, Y down. For each occupied descriptor square the index uses `FragmentGeometry.transform` for all corners, enumerates its integer AABB, and clips the actual polygon against each candidate unit square. Shoelace intersection area must be **strictly greater than 1e-10 square cells**. Edge/corner-only touching and overlaps at or below that tolerance do not count. This area threshold is not a penetration-depth threshold. Floating-point error, especially near the threshold, can affect results; transformed coordinates beyond ±1,000,000,000 cells or non-finite coordinates fail explicitly. Candidates outside world dimensions are counted as clipped and not stored; body geometry itself is not clamped.

The temporary sparse ordered map contains only occupied cells, with compact growable primitive ID arrays. Bodies are sorted by ID so each cell's IDs are ascending and deduplicated even if several fragment squares cover the same cell. Publication compacts this into sorted row-major `long[]` cell keys, `int[]` offsets and `long[]` IDs; queries binary-search keys and return copies of ID ranges. `forEachCell` traverses in row-major order. Temporary memory is O(occupied cells + memberships + bodies), published memory O(occupied cells + memberships); there is no full-world-sized array or hash set per cell. Rebuild work is O(fragment cells + candidate clipping/checks + memberships × log(occupied cells)). Polygon clipping allocates temporary arrays per in-bounds check; this reference implementation has not been allocation-profiled.

Validated `Limits` bound body count, transformed fragment cells, all AABB candidates (including clipped), in-bounds polygon area checks, positive intersections, unique occupied cells and unique body-cell memberships. Zero is a valid limit. Checked arithmetic and descriptive exceptions prevent silent truncation; diagnostics report the most recent successful publication and include all those counts, multiple-occupant cells, clipped candidates, generation and revision. Positive intersections count fragment-square/cell intersections before body-ID deduplication. `areaChecks` is the narrow-phase polygon check count, not a SAT count. Work proportional to the bounded AABB candidate count is never performed outside the clipping loop.

The current `FragmentDescriptor` enforces one connected component; disconnected descriptors are rejected before the index sees them. The index iterates its actual supplied cell list and does not fill cavities or depend on rectangle shape. Supporting disconnected descriptors would require a separately approved change to that existing contract.

## Tests and benchmark

`gradlew.bat test build` covers integer/fractional positions, 90°/45°/arbitrary rotation, tiny positive/edge overlaps, concavity, multi-occupancy, four clipped edges, extreme and non-finite transforms, every budget, immutable old snapshots, ordering, world/mass preservation and explicit physics/registry lifecycle, in addition to physics and structural regressions.

`gradlew.bat rigidOccupancyBenchmark`: Windows AMD64 Family 25 Model 33, OpenJDK 25.0.5+8, 400×300 world, 60 warmup and 200 measured rebuilds; transforms remain fixed and no physics ticks are included:

| Scenario | Mean/p95 ms | Candidates | Area checks | Occupied cells | Memberships |
|---|---:|---:|---:|---:|---:|
| one cell | 0.0150/0.0270 | 1 | 1 | 1 | 1 |
| 16 cells | 0.0163/0.0261 | 16 | 16 | 16 | 16 |
| 64 cells | 0.0420/0.0509 | 64 | 64 | 64 | 64 |
| ten scattered | 0.0062/0.0096 | 10 | 10 | 10 | 10 |
| 32 scattered | 0.0166/0.0258 | 32 | 32 | 32 | 32 |
| 100 scattered | 0.0503/0.0534 | 100 | 100 | 100 | 100 |
| ten rotated irregular 16-cell | 0.1806/0.2019 | 910 | 910 | 310 | 310 |
| 100 overlapping | 0.0395/0.0469 | 100 | 100 | 1 | 100 |

This is one exploratory run, not a performance guarantee; warmup/JIT and GC can distort small timings. Heap usage and allocations were not measured. No GUI behavior was verified.

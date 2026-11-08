# M4.4b.4b — cellular coupling (experimental)

World owns cellular IDs, the fragment registry owns fragment substance and mass, physics owns transforms, and occupancy is a derived immutable snapshot. The coordinator is single-owner, non-reentrant and headless. It does not change structural connectivity or install a World sink. Coupled static collision treats STONE, BEDROCK and WOOD as solid; the legacy collision policy is unchanged. Small solver penetrations into static solids are physical contacts, not permission to displace them.

AIR is empty; SAND, GRAVEL, SOIL and WATER are displaceable; STONE, BEDROCK and WOOD are static. The exhaustive material switch requires future materials to be classified explicitly. Sources are processed in occupancy row-major order. Breadth-first search uses down, left, right, up for granular materials and left, right, down, up for water. Bounds, visited cells, reservations and limits are checked deterministically. Only AIR or an already planned vacancy is traversable; occupied AIR and planned vacancies can be traversed, but cannot be destinations if occupied by a body. Static solids and unvacated material are barriers. Failure produces no plan or writes.

Physics is checkpointed before the tentative tick, including bodies, sleep, solver counters and successful diagnostics. The registry remains externally owned. Occupancy is prepared without publication. World validates the entire expected-value batch, uniqueness, revision arithmetic and prepared snapshot before writing; after writes it publishes occupancy before the synchronous batch callback. World callbacks must not throw or mutate World, as required by the existing contract. No external code may mutate physics, World or occupancy concurrently or reentrantly. On a pre-commit failure physics is restored and the old snapshot and World remain committed; the failure is exposed and retry is possible. A callback that violates the contract cannot be rolled back. Ordinary cellular stepping is a separate, non-transactional phase after this boundary, not an atomic frame.

All cellular destinations, gravel side clearance and water scanning honor occupancy; no snapshot preserves legacy behavior. Changing a snapshot wakes every ACTIVE_REGIONS tile conservatively. Snapshot identity is checked before the separate cellular phase. Determinism assumes identical initial state, configuration and ordered inputs; timings are optional and are not physical inputs.

Limits cover sources, moves, candidate checks, reachability checks, total Manhattan distance and search radius. Sweep polygon candidates use the candidate bound and the reachability limit bounds total sampled poses. Diagnostics retain the last successful tick, per-material counts, distance, radius, search work, occupancy revision and phase outcome; `lastFailure` and `lastFailureStage` identify the latest failure. Optional phase timings cover physics, occupancy, planning, commit and cellular movement.

The trace includes the initial pose, integrated poses and solver corrections. Between traced poses, translation and rotation are interpolated at a maximum estimated point travel of 0.125 cell. Each actual fragment cell is transformed separately; its AABB enumerates candidates and polygon clipping uses the same positive-area tolerance as occupancy. Thus concave empty space is not treated as solid. An intermediate intersection with displaceable material absent from final occupancy rejects the tick; only final occupancy produces displacement requests. Static collision is still handled by the bounded discrete physics solver. This is sampled path safety, not continuous collision detection: sufficiently brief grazing intersections between samples can be missed, and non-linear solver paths between recorded poses are approximated. Work is explicitly bounded and failures are atomic within the stated boundary.

## Measured exploratory benchmark

`gradlew.bat cellularCouplingBenchmark`, Windows, AMD Ryzen 9 5900X, OpenJDK 25.0.5+8-LTS; 64×64 world, fresh setup outside timing, 10 warmups and 40 single-tick samples per scenario, including cellular movement. Times are milliseconds, mean/p95. These are exploratory microbenchmarks, not sustained frame rates.

| Scenario | mean | p95 | occupancy mean | plan mean | commit mean | cells mean | displaced / search candidates |
|---|---:|---:|---:|---:|---:|---:|---:|
| no bodies | .077 | .117 | .005 | .004 | .008 | .050 | 0 / 0 |
| one, air | .176 | .243 | .026 | .046 | .009 | .008 | 0 / 0 |
| sand | .127 | .186 | .016 | .022 | .012 | .047 | 1 / 1 |
| water | .109 | .140 | .012 | .016 | .010 | .043 | 1 / 1 |
| ten bodies | .171 | .242 | .020 | .042 | .005 | .005 | 0 / 0 |
| 32 bodies | .340 | .393 | .039 | .119 | .006 | .005 | 0 / 0 |
| dense sand | .050 | .077 | .002 | .014 | .012 | .014 | 1 / 5 |

All listed scenarios succeeded 40/40. Sweep candidates/poses per sample were 0/0 for no bodies, 25/13 for each one-body scene, 250/130 for ten and 800/416 for 32, against default limits of 1,000,000 each. Near-full world was rejected 40/40 at planning because there was no reachable destination; rejected mean/p95 were .031/.039 ms. The previous dense failure was an expanded-AABB false positive, not a static collision or work-budget failure: the source has a reachable destination through occupied air. Dense configurations without a reachable path still fail. No rendering, UI, buoyancy, fragmentation or general pressure model is included.

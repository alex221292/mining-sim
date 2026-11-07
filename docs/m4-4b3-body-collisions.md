# M4.4b.3 — body contacts and sleeping

Body-body interaction is opt-in: provide both `BodyCollisionQuery` and `BodyContactSolver` to `RigidBodySystem`. Existing constructors keep their previous free or static-only execution. The interacting path advances all awake bodies over a shared interval before querying pairs. Four nominal substeps remain; the most restrictive awake body's bounded point travel determines the next shared microstep, with re-evaluation after every solve. Each awake body is charged one unit of total motion work per shared microstep; sleeping bodies are not. Shared steps are bounded by the per-body motion limit and total awake work by the total limit. No wall clock or rendering participates.

Pairs are sorted by stable ID; A is the lower ID. AABB filtering precedes occupied square-pair SAT, preserving concave gaps. Normals point from A toward B; static normals point from body toward obstacle. Overlap-polygon representative points are approximate. Similar-normal contacts for each pair are reduced to tangent extremes; this simple reduction can merge separate surfaces of the same pair and is not an exact manifold. With `r` in metres, relative velocity `vB + omegaB × rB - vA - omegaA × rA` approaches when its normal projection is negative. Effective inverse mass is `1/mA + 1/mB + cross(rA,n)²/IA + cross(rB,n)²/IB`; opposite impulses conserve linear momentum. Default restitution 0.05 is suppressed below 0.5 m/s; accumulated normal and Coulomb tangent impulses use friction 0.7 static/0.5 dynamic.

Static and body constraints are built once per microstep and processed in deterministic interleaved velocity iterations, retaining accumulated impulses and the original restitution targets. Position correction is separate, mass-weighted and bounded, with repeated queries and 0.01-cell slop. It is translation-only and approximate; correction can affect other contacts. Static-only behavior is unchanged.

Supported bodies below 0.02 m/s and 0.02 rad/s for 60 ticks sleep with zero velocities; `SleepConfig` makes these thresholds configurable. Unsupported bodies cannot sleep. Static support and body support are propagated through contact normals using bounded iteration. Before integration, a deterministic AABB wake search expands awake bodies by their conservative remaining travel and wakes nearby sleeping bodies transitively. If a correction reaches an unanticipated sleeping body after integration, the tick fails and rolls back rather than mixing timelines. Assigning nonzero velocity wakes the body. `World.mutationRevision()` increments on mutations independently of the existing structural `ChangeSink`; any world revision or registered body removal conservatively wakes all bodies. This can cause unnecessary waking after distant edits. The system assumes single-owner world mutation between ticks.

`BodyCollisionQuery.Limits` bounds per-query pairs, cell checks and raw/reduced contacts. `InteractionConfig` bounds their aggregate per tick, support/wake propagation work and position iterations; motion and static-query limits also apply. Exceeding a limit throws; no contacts are silently truncated. `interactionDiagnostics()` reports shared microsteps, aggregate candidate/overlapping pairs, cell checks, raw/reduced contacts, velocity iterations, sleeping/woken counts and maximum observed penetration in cells. Counts include repeated correction and support queries; waking from a direct velocity assignment before the tick is not included in the tick's woken count. Failed ticks restore prior body membership, motion, sleep state and published diagnostics; `lastFailure()` reports the exception. Registry and World are not modified by stepping. Determinism assumes identical operations and floating-point runtime behavior.

## Tests and exploratory benchmark

`gradlew.bat test build` runs static/bounded-motion/structural regressions and body-query, head-on, unequal-mass momentum, off-center rotation, 600-tick three-body stack, sleep, support-removal, world-listener and rollback tests. `gradlew.bat bodyCollisionBenchmark`, Windows AMD64 Family 25 Model 33, OpenJDK 25.0.5+8, 128×80 world, 60 warmup and 120 measured ticks, four nominal steps, default solver and budgets:

| Scenario | Mean/p95 ms | Shared steps | Pairs | Cell checks | Reduced contacts | Iterations | Sleeping |
|---|---:|---:|---:|---:|---:|---:|---:|
| one | 0.065/0.139 | 4 | 0 | 0 | 0 | 0 | 0.9 |
| head-on | 0.043/0.131 | 4 | 17 | 17 | 0 | 0 | 1.9 |
| two stack | 0.038/0.056 | 4 | 17 | 17 | 17 | 0 | 2.0 |
| ten rest | 0.221/0.434 | 4 | 765 | 34 | 34 | 32 | 5.7 |
| ten moving | 0.350/0.596 | 4 | 765 | 85.1 | 65.4 | 32 | 5.6 |
| sparse 32 | 0.944/1.274 | 4 | 8432 | 408 | 408 | 6.1 | 26.1 |
| dense 32 | 0.880/1.068 | 4 | 8432 | 408 | 408 | 32 | 0.5 |
| rotating irregular | 0.380/0.693 | 4 | 476 | 0 | 0 | 0 | 5.3 |

Counts are averages per measured tick; initial impacts may occur during warmup, particularly head-on. One exploratory run, not a production guarantee. Allocations and GC were not measured. Full simulation budget at 60 TPS is 16.67 ms, not just this solver.

## Limitations

No exact CCD or universal anti-tunnelling guarantee; grazing, high rotation, overlap and numerical degeneracies remain difficult. Contact reduction, sleeping support and position correction are approximations, not engineering-grade mechanics. Revision invalidation is global, not spatial. No body occupancy, cellular displacement, buoyancy, impact breakage, Swing or persistence integration is provided. Sleeping/wake tests cover reference cases, not every possible pile geometry.

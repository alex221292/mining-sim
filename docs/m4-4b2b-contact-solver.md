# M4.4b.2b — static contact impulse solver

This opt-in headless solver connects a `StaticCollisionQuery` to `RigidBodySystem`; the constructors without a solver retain free motion. World and fragment material ownership are unchanged. No Swing or cellular integration is provided. Normals point from the body toward the obstacle; positive contact normal velocity means approach and the impulse on the body points against the normal.

Positions and contact points are in cells (0.1 m each), velocities in m/s, mass in kg, angular velocity in rad/s and inertia in kg·m². With lever arm `r = (contact - center) * 0.1` in metres, contact velocity is `(vx - omega*ry, vy + omega*rx)`. The normal effective inverse mass is `1/m + cross(r,n)^2/I`. A positive approach is opposed by an impulse along `-n`. Restitution defaults to 0.05 and is disabled at speeds at or below 0.5 m/s. Constraints accumulate normal impulses across eight deterministic velocity iterations, with the restitution target computed once. Tangential impulses use their own effective mass and accumulated Coulomb limits: static 0.7, dynamic 0.5 by default. Configurable coefficients and iteration counts are validated.

Each 1/60 s tick has four default substeps. Each substep integrates gravity semi-implicitly, queries contacts, solves velocities and then re-queries up to three times for translation-only correction of the deepest contact: 20% of depth beyond 0.01 cell, capped at 0.2 cell per iteration. Correction does not directly change velocity or orientation. This is an iterative approximation, not a guarantee of recovery from arbitrary deep intersections. The query's overlap-centroid contact points and seam approximation are described in `m4-4b2a-contact-detection.md`; neither is exact continuous contact geometry. Sleeping is not implemented.

Before a tick, motion states and the body map are saved. On a runtime exception, including a query work-limit failure or invalid numeric result, all pre-tick body motion and membership and the last published diagnostics are restored and the exception propagates. The registry and world are never modified. This is a single-owner contract, not protection against concurrent edits or VM failure. Iteration and contact order are deterministic for identical inputs and runtime floating-point behavior. Diagnostics include candidates, checks, contacts (including correction queries), velocity iterations and maximum predicted center displacement and angle change per substep. These metrics do not enforce a safety limit.

## Verification and measurements

`gradlew.bat test build` includes 900-tick floor rest, boundary impulse directions, off-center rotation, frictionless sliding, low-speed rest, rotated L-shape, bedrock, deterministic replay and multi-body rollback tests, plus prior regressions. A `staticContactBenchmark` exploratory run on Windows, AMD64 Family 25 Model 33, OpenJDK 25.0.5+8, 128×48 world, 4 substeps, 120 warmup and 300 measured ticks, 8 velocity/3 position iterations:

| Scenario | Mean ms | p95 ms | Candidates/tick | Checks/tick | Contacts/tick | Velocity iterations/tick |
|---|---:|---:|---:|---:|---:|---:|
| 1 cell | 0.0435 | 0.0682 | 32 | 80 | 16 | 32 |
| irregular 16 cells | 0.4404 | 0.4964 | 800 | 3584 | 32 | 32 |
| 64 cells | 0.7727 | 0.8512 | 1440 | 14336 | 32 | 32 |
| six independent cells | 0.0450 | 0.0469 | 192 | 480 | 96 | 192 |

Counts include repeated position queries and depend on this particular scene. Timings are one exploratory run, not a guarantee; allocations were not measured. The 60 TPS budget is 16.67 ms per full simulation tick, not just physics.

## Limitations

Discrete overlaps only: **no CCD or high-speed tunnelling protection**. Large translation, angular displacement or long fragments can miss thin obstacles, and diagnostics do not prevent this. Contacts near corners, seams and deep overlaps are approximate; there is no body-body collision, sleeping, occupancy, granular displacement, water forces, saving or UI integration. Independent bodies can overlap one another. M4.4b.2c remains future work.

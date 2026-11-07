# M4.4b.2c — bounded adaptive collision stepping

This document describes M4.4b.2c. The optional shared-timeline body interaction added later is documented in `m4-4b3-body-collisions.md`.

Collision-aware `RigidBodySystem` uses four 1/240 s base substeps per 1/60 s tick by default. For each body, each base substep is split into microsteps. The maximum distance from its center of mass to any occupied cell corner is computed once in `FragmentGeometry`. With `v` in m/s, gravity `g = 9.81 m/s²`, interval `h`, radius `R` in cells and angular velocity `omega` in rad/s, the bound in cells is:

`(|v| + g*h)*h/0.1 + R*|omega|*h`.

This uses `g*h²`, not `0.5*g*h²`, because the existing semi-implicit Euler integrator moves using the end velocity. The angular arc length bounds the chord. The default limit is 0.25 cell per microstep; smaller positive values are configurable. For the remaining part of each base substep, `ceil(bound / limit)` determines a deterministic subdivision; after each executed microstep and contact solve, the remaining interval is re-evaluated using the new velocities. Thus impulses can increase subsequent work. Each microstep integrates its actual interval and invokes the existing discrete query, velocity solver and position correction. The full base time is advanced for each body in ID order. No wall clock is involved.

The defaults allow 128 executed microsteps per body and 4096 across the tick; both are configurable positive integers. Candidate/check limits remain those of `StaticCollisionQuery`. A `MotionBudgetException` reports the body, requested remaining work and available body/total budgets; work is never silently clamped. On any runtime failure, including query limits, pre-tick body states and membership and the previous successful diagnostics are restored. `lastFailure()` retains the error until a successful tick. Registry and world are unchanged. Single-owner operation is assumed; VM failures and concurrent changes are outside the contract.

`motionDiagnostics()` reports base substeps, total executed microsteps, maximum per body, maximum predicted point-travel bound, and maximum integrated center displacement and angular change. Center displacement excludes position correction. Existing solver diagnostics count candidates, checks and contacts across all microsteps and correction queries and count velocity iterations separately. Failed ticks do not publish partial counts; the explicit exception and `lastFailure()` report the reason. Free-motion systems without a solver retain fixed stepping and do not enforce motion budgets.

## Verification and measurement

`gradlew.bat test build` covers high-speed floor, ceiling, walls from both sides, world boundaries, bedrock, isolated cell, thin platform, diagonal corner, narrow opening, concave and rotating long shapes, deterministic replay, work limits and multi-body rollback, as well as previous 900-tick resting and structural regressions. Maximum travel is checked in reference tests. Work budgets are not a mathematical continuous collision guarantee.

`gradlew.bat boundedMotionBenchmark`: Windows, AMD64 Family 25 Model 33, OpenJDK 25.0.5+8, 128×48 world, one-tick identical initial conditions, 60 warmup and 200 samples per case, four base substeps, defaults 8 velocity / 3 position iterations. The baseline reproduces the previous fixed four-step algorithm in the benchmark only; it is unsafe for fast motion and is not a production mode. Counts include correction queries. Timings are one exploratory run, with setup excluded; minor benchmark harness differences and JIT/GC noise prevent interpreting them as rigorous speedups.

| Scenario | Mode | Mean/p95 ms | Microsteps | Candidates | Checks | Contacts | Solver iterations |
|---|---|---:|---:|---:|---:|---:|---:|
| resting contact | baseline / adaptive | 0.0301/0.0464 / 0.0259/0.0380 | 4 / 4 | 16 / 16 | 40 / 40 | 8 / 8 | 32 / 32 |
| normal fall | baseline / adaptive | 0.0062/0.0096 / 0.0062/0.0088 | 4 / 4 | 16 / 16 | 32 / 32 | 0 / 0 | 0 / 0 |
| fast fall | baseline / adaptive | 0.0114/0.0177 / 0.0352/0.0575 | 4 / 21 | 20 / 88 | 44 / 182 | 4 / 6 | 8 / 16 |
| rotating 16 | baseline / adaptive | 0.0723/0.1948 / 0.1347/0.1530 | 4 / 12 | 376 / 1176 | 512 / 1536 | 0 / 0 | 0 / 0 |
| rotating 64 | baseline / adaptive | 0.1513/0.1648 / 0.3269/0.4124 | 4 / 8 | 800 / 1600 | 2048 / 4096 | 0 / 0 | 0 / 0 |
| six independent | baseline / adaptive | 0.0604/0.0737 / 0.0401/0.0586 | 24 / 24 | 96 / 96 | 240 / 240 | 48 / 48 | 192 / 192 |

Counts are per tick, averaged over measured samples. The 60 TPS budget is 16.67 ms for the entire simulation; these are not end-to-end timings. Allocation/GC was not measured.

## Limitations

This is bounded discrete SAT, **not exact CCD or a time-of-impact solver**. Grazing, corners, very concave shapes, numerical degeneracy, initial overlap, and approximate translation-only position correction do not have a universal no-tunnelling guarantee. Correction movements are not included in the velocity-based travel bound. Bodies do not collide with each other. No sleeping, occupancy, cellular coupling, buoyancy, fragmentation, UI or save changes are implemented. The system remains headless and is not connected to Swing.

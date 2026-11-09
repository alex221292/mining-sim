# M4.5a.2 — large compound contacts

The reported interactive failure involved three large irregular STONE fragments, two on BEDROCK and one across them, with approximately 100 ms ticks and 1 FPS. Headless reproduction uses a 64×64 BEDROCK_ENABLED world and 134/134/159-cell or 134/134/526-cell connected, notched, elongated shapes. `gradlew.bat largeCompoundBenchmark` runs coupled physics, occupancy, displacement and cellular updates for resting, elevated impact, large impact and a 40-tick separated control. The large fixture starts unrotated; the actual cell geometry rotates during the simulation. These generated fragments are not a replacement for testing interactive extraction.

## Diagnosis and implementation

Before acceleration, the 526-cell impact fixture spent roughly 162 ms per active tick in body queries, testing roughly 2.4 million cell pairs per tick. Three body-level AABBs are not the bottleneck: exhaustive cell-pair SAT was. Immutable geometry now indexes descriptor rows once. For each square, a conservative range in the other body's local frame finds candidates by binary search; static-cell queries use the same index. Bounds include `1e-9 + 32 ulp` at the largest relevant coordinate magnitude; candidates still pass unchanged square SAT. Descriptor row-major order is preserved. Boundary checks, contact reduction, mass and inertia remain unchanged. Test-only brute-force paths compare final ordered contacts and raw counts. Narrow-phase limits now count actual candidates, not rejected combinations.

Only when every recorded swept pose equals the final pose is intermediate material testing omitted; the already prepared final occupancy covers those same polygon intersections. Changed poses retain the bounded sweep and failure/rollback path. World-to-fragment ownership and transactional publication are unchanged.

## Sleep policy

This is an explicit game approximation, not exact rigid-body physics: **microscopic supported sliding may stop**. A supported contact island must stay quiet for 60 fixed ticks (one second): horizontal speed below 0.02 m/s, vertical speed below 0.04 m/s, angular speed below 0.02 rad/s; each tick also has displacement and angular limits derived from the 0.02 limits. Over the quiet window displacement must remain below 0.05 cells (5 mm) and rotation below 0.002 rad (0.115 degrees). The doubled vertical limit accommodates measured contact impulse jitter; displacement and support remain mandatory. Sleep fixes pose and zeros velocities. Unsupported or falling bodies cannot sleep; world edits, removal and incoming bodies trigger existing wake logic. Snapshot/rollback includes the window anchor. Gravity, friction and restitution were not changed. These limits necessarily allow some slow real sliding to stop; they do not prove static mechanical stability for every geometry.

## Measurements

OpenJDK 25.0.5, Gradle 9.2.1, 64×64, fixed 60 Hz simulation, instrumented `InteractiveSession.tick`, not Swing rendering. Each run uses identical 360 ticks, fixed windows (0–9, 10–29, 30–89, 90–239, 240–359), same benchmark source and profiling on baseline `d03c3d8` and modified code. `gradlew.bat largeCompoundBenchmark --args="large large"` starts two fixtures in one JVM: first cold, second warmed. Measurements are wall time, mean/p95/max ms; the first baseline run partially overlapped another benchmark, so its cold numbers are indicative rather than a controlled isolated comparison. The warmed baseline ran after that overlap; the after run was repeated separately.

| 526-cell scene | baseline cold | after cold | baseline warm | after warm |
|---|---:|---:|---:|---:|
| ticks 0–9 | 39.722/118.308/118.308 | 28.143/97.353/97.353 | 9.725/10.467/10.467 | 5.738/6.222/6.222 |
| ticks 30–89, settling | 177.355/178.715/186.064 | 13.842/15.086/15.642 | 177.132/177.865/178.184 | 13.237/13.894/14.637 |
| ticks 90–239, mixed | 177.090/178.232/182.236 | 12.246/13.616/14.777 | 176.979/177.834/179.399 | 12.177/13.822/14.749 |
| ticks 240–359 | 177.209/178.597/180.676 | 0.709/0.771/1.168 | 177.091/178.153/181.198 | 0.673/0.691/0.803 |

The modified fixture first slept at tick 229; baseline did not sleep within 360 ticks, so the last row compares an active baseline against sleeping optimized bodies, not equivalent sleep states. The mixed window includes 11 sleeping ticks. In the warm settling window, body checks fell from 2,396,456 to 714 per tick; displacement planning remained about 7.8 ms/tick. The cold impact window still spiked to 37.508 ms in the separate after run. Timings are machine- and JVM-dependent; no GUI FPS improvement is asserted.

## Verification and limitations

`gradlew.bat test build` exercises seeded brute-force comparisons, large stack and impact replay, sleep/wake, rollback and existing extraction, coupling and legacy tests. Exact contact ordering is tested for randomized rotation, grazing and boundaries; it is not a formal floating-point proof for every representable coordinate. Broad-phase bounds and discrete collision limitations remain. Traces revealed near-horizontal body normals alongside vertical support; whether some are internal-cell artifacts has not been established. Do not discard them without a conservative union-boundary algorithm and oracle. Instrumentation does not separately attribute every requested solver phase or individual impulse; benchmark maxima and timing are not a rendering profile.

F3 enables timing on the next simulation tick and displays physics, static/body query, SAT, microsteps, active/sleeping counts and occupancy; it does not run physics during paint. F4 cycles existing reference scenes, including the small resting stack, but has no dedicated 526-cell scene. Manual GUI check: run `gradlew.bat run`, enable F3, draw a large irregular stone structure attached to BEDROCK, erase three sections, watch the resulting stack settle, remove a support and check wake-up and F3. Check pause, single-step, reset and LEGACY separately. This manual GUI check has not been performed.

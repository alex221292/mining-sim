# M4.4b.4d — integration hardening

## Ownership and ordering

On the Swing EDT, one `InteractiveSession` owns a World and cellular Simulation; the structural controller owns its connectivity, damage and fragment registry, physics owns body transforms, and occupancy owns an immutable derived snapshot. `CoupledSimulationStep` coordinates tentative motion and displacement; Java2D only reads committed geometry. Fragments remain registry-owned, never rasterized into World. The World sink remains the authoritative connectivity sink. Renderer and diagnostics do not rebuild occupancy or advance time.

Each 60 Hz fixed tick consumes queued commands, advances bounded structural analysis/damage, performs coupled physics, prepares occupancy and displacement, commits them, then runs ordinary cellular movement separately. Structural breaks become visible during the structural phase; extraction commands run before that phase and therefore use analysis published by an earlier tick. World edits are observed by physics in the same tick; cellular changes after physics can wake bodies on the next tick. Occupancy changes wake active cellular regions. Rendering on the same EDT observes only committed states. There is no automatic detachment.

F4, C and successful load retire the old session, clear its queued commands and prevent further ticks or enqueues on it. The new session owns fresh World and subsystem references. Pause and single-step retain their existing meaning. A retired session is not reusable. F5 preflights registry reservation, a new physics membership and occupancy before World mutation. The World batch validates all preconditions, writes source cells, then publishes registry, occupancy and membership before its batch callback. Failed preflight or validation leaves all owners unchanged. Callbacks remain synchronous, non-mutating and non-throwing; a callback violating that existing contract cannot be rolled back. Extraction remains limited to 64 cells, and oversized requests report `TOO_LARGE`.

Brush commands execute at the next tick boundary and reject placement in published occupancy; AIR only erases World. Structural bond incarnation and invalidation remain owned by connectivity. A support edit wakes sleeping bodies on the next physics step. The atomic coupling boundary excludes ordinary cellular movement. Expected budget, reachability and swept-path rejections preserve prior physics, World and occupancy; cellular movement is skipped. Three identical failures block coupling; F6 retries, and F3 indicates running, retrying, blocked or recovered. Unexpected `IllegalStateException`s are rethrown instead of silently treated as normal budget failures.

## Verification and limitations

`IntegrationReplaySoakTest` runs two identical sequences of three fresh 24×20 sessions, each exceeding 400 ticks, with queued sand/water painting, extraction, a terrain edit and retirement: over 2,400 coupled ticks in total. It compares World cells, fragment identity, geometry and mass, body state, occupancy, structural bonds and deterministic diagnostics. Separate tests cover occupancy-preparation failure before extraction, irregular and boundary extraction, adjacent fragments, stale topology, oversized extraction, retirement, support removal and sleeping wake-up. Existing structural, physics, active-region, v1 persistence and headless image tests remain in the suite. The soak is bounded, not a proof of indefinite stability; no visible desktop GUI was exercised. Sampled swept motion is not full CCD; dense configurations may correctly fail if no reachable destination exists. MSIM v1 cannot save rigid fragments or bedrock worlds.

## Measurements

`gradlew.bat integrationHardeningBenchmark`, Windows, AMD Ryzen 9 5900X, OpenJDK 25.0.5+8-LTS. 64×64 worlds except 17×6 structural reference, 50 consecutive ticks without warmup, default budgets; times are ms. Render measures the body overlay into a 640×480 BufferedImage, not complete Swing repaint. No allocation/GC or real-time frame-stability measurement was made.

| scenario | success | tick mean/p95 | structural mean | physics mean | occupancy mean | planning mean | commit mean | cells mean | render mean/p95 |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| empty | 50 | .465/.477 | .092 | .169 | .004 | .048 | .052 | .076 | .011/.004 |
| sand/water, no bodies | 50 | .204/.328 | .037 | .061 | .003 | .004 | .005 | .087 | .003/.005 |
| 1 body | 50 | .417/.916 | .013 | .236 | .023 | .060 | .006 | .066 | .021/.044 |
| 10 bodies | 50 | .603/1.011 | .012 | .431 | .047 | .092 | .006 | .009 | .043/.098 |
| 32 bodies | 50 | 1.559/3.016 | .012 | 1.211 | .036 | .276 | .006 | .009 | .036/.072 |
| 100 bodies | 50 | 7.593/18.389 | .013 | 6.948 | .105 | .493 | .006 | .010 | .068/.099 |
| dense sand | 41 | .113/.148 | .012 | .029 | .010 | .032 | .005 | .022 | .004/.026 |
| 10 sleeping | 50 | .129/.204 | .005 | .096 | .010 | .006 | .001 | .010 | .004/.006 |
| structural + body | 50 | .202/.245 | .164 | .028 | .004 | .004 | .001 | .001 | .003/.005 |

Dense sand then rejected a tick at planning: no reachable destination at (25,61); only successful ticks are timed. The 100-body p95 exceeds the 16.67 ms target and is dominated by existing pairwise physics; no unmeasured optimization or broad phase was introduced. Cold-start outliers explain cases where mean exceeds p95. These exploratory measurements are not FPS claims. The recommended next milestone is a separately approved bounded broad-phase/performance investigation and further GUI verification before automatic detachment.

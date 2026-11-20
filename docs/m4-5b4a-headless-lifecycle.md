# M4.5b.4a — headless rock lifecycle

`InteractiveSession.headlessLifecycle(...)` opts into lifecycle integration; existing desktop constructors do not. The owner thread executes queued changes, structural/automatic detachment, bounded support coordination and any reactivation, coupled physics and occupancy publication, cellular movement, then group settling. Settling must follow cellular movement: cellular uses the coupled tick's published occupancy. Successful settling publishes World, rigid membership, occupancy and settled ownership atomically. The next physics tick uses the resulting membership and occupancy. A reactivation tick cannot also settle a group. No second physics tick is introduced.

Sleep eligibility requires 120 successful quiet observations. Contact components include moving bodies and exclude any component containing one. At most one group preparation is attempted per tick; existing group limits are three bodies, 2,048 cells and 2,000,000 work units. Contact discovery scans at most 100,000 occupied cells and 2,000,000 contact iterations. Support analysis uses the coordinator's configurable limits and may span ticks. Failed preparations are cached against relevant state and revisions; an unchanged failure is not retried each tick. Diagnostics expose counts, rejection categories, pending support, retries and instrumented phase times; they are not a Swing feature.

The existing conservative group planner requires a group support path to BEDROCK. A body resting on ordinary STONE, even STONE directly supported by BEDROCK, is not automatically certified by this planner; `ordinaryStoneFoundationCannotCurrentlySettleRigidRock` records the specific source-support rejection. The repeated lifecycle test removes a lower settled cell as an explicit world edit, accounting for that removed matter. The planner remains incomplete and no general STONE stability model is claimed. Existing SWEEP failures remain possible; this integration does not modify CCD or the solver. World save/load remains world-only and does not preserve settled provenance. GUI behavior was not verified.

## Measurements

Run after compiling with Java 25: `java -cp build/classes/java/main miningsim.benchmark.HeadlessLifecycleBenchmark 10`. Each scenario has two warmup runs and ten measured runs, one JVM, profiling enabled and default limits. Wall-clock times include normal JVM/OS noise; p95 uses nearest rank. The 20×20 scenario completes detachment, settling, support removal, reactivation and resettling; the 64×64 134/134/526 fixture measures only the initial settling, not a full lifecycle. Ordinary tick samples include motion and sleep ticks without settling preparation or reactivation. The first reactivation tick is the whole tick including support work and physics, not physics in isolation. Support-after-removal includes the coordinator call that commits reactivation. Phase timings are nested, not additive.

Measured on OpenJDK 25.0.5+8-LTS, OpenJDK 64-Bit Server VM, Windows, 10 runs (milliseconds):

| Scenario / phase | n | mean | p95 | max |
| --- | ---: | ---: | ---: | ---: |
| Small full lifecycle | 10 | 9.169 | 16.483 | 16.483 |
| Small ordinary tick | 3970 | 0.020 | 0.048 | 0.825 |
| Small settling preparation | 20 | 0.196 | 0.491 | 0.518 |
| Small settling commit | 20 | 0.019 | 0.029 | 0.030 |
| Small support after removal | 10 | 0.137 | 0.213 | 0.213 |
| Small reactivation preparation | 10 | 0.087 | 0.123 | 0.123 |
| Small reactivation commit | 10 | 0.009 | 0.018 | 0.018 |
| Small reactivation and first physics tick | 10 | 0.200 | 0.277 | 0.277 |
| Large ordinary tick | 3470 | 9.740 | 17.634 | 30.690 |
| Large settling preparation | 10 | 6.535 | 16.138 | 16.138 |
| Large settling commit | 10 | 0.707 | 0.729 | 0.729 |
| Large time until settling | 10 | 3389.918 | 3628.572 | 3628.572 |

These measurements are not FPS and are not a performance guarantee. With ten transition observations p95 is the maximum.

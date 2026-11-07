# M4.4b.3.1 — physics tick rollback

`RigidBodySystem.step()` starts its transaction before registry synchronization. It snapshots the ordered body membership and the state of each existing body, without copying `World`, descriptors or geometry. A successful single-owner tick publishes the resulting membership, motion and sleep state, world-revision and body-ID observations, and successful diagnostics. There is no externally observable intermediate publication under the existing non-reentrant single-owner contract.

On a `RuntimeException`, the same body objects are restored to the pre-tick membership and their exact position, angle, linear/angular velocities, sleep flag and counter are restored. All three successful diagnostic records, the observed world revision and last successful body IDs are restored; the original exception is rethrown. `lastFailure()` alone records its type and message, and clears only after a successful tick. `World` and `FragmentRegistry` are external owners: edits made before the tick remain intact, and a retry sees them again. In particular, a failed wake after an edit does not consume its world revision; a failed registry synchronization does not consume a pending addition or removal. Public `synchronize()` called independently is outside a tick transaction.

## Mutable state classification

- **Committed:** body membership and object identities; each body's transform, velocities, sleep flag/counter; `observedWorldRevision` and `lastSuccessfulBodyIds`.
- **Last successful publication:** `diagnostics`, `motionDiagnostics`, `interactionDiagnostics`; all fields are zero before the first successful tick.
- **Scratch:** `interactionWork`, per-body step counts, contact constraints and query-local lists/counters. `interactionWork` is discarded in `finally`, including after failure. `StaticContactSolver` counters are snapshotted at the boundary, reset at tick start, restored on failure and reset again on the next tick. Pair query and pair solver hold only immutable configuration across calls; constraints and accumulated impulses are local to a microstep.
- **Failure reporting:** `lastFailure`. The package-private checkpoint callback exists only as a deterministic test seam; it is null in normal operation and is not simulation state.

Work accumulation and query counters use checked arithmetic. Overflow is an explicit runtime failure and follows the same rollback path. Bounds and memory limits still constrain practical body counts. This is not a general transaction manager: VM errors such as out-of-memory, process termination, callback contract violations and concurrent world/registry mutation are outside the guarantee.

## Verification and measurement

`gradlew.bat test build` includes deterministic failure/retry comparisons, including failure after world-edit wake and after diagnostics/revision publication, registry addition/removal, object identity, sleep state, static counters, broad/narrow/reduced-contact limits, static query failure and checkpoints after synchronization, contact preparation, velocity, position and before support evaluation. The package-private seam tests rollback boundaries that cannot reliably fail with valid finite geometry; real query-limit tests exercise the actual failure paths. Position/velocity solvers also reject non-finite results; ordinary immutable, validated descriptors provide no natural deterministic synchronization failure. Empty-body and pre-first-tick diagnostics are tested. Existing static, bounded-motion, stacking, extraction and structural tests remain enabled.

`gradlew.bat physicsRollbackBenchmark`: Windows AMD64 Family 25 Model 33, OpenJDK 25.0.5+8, 256×256 empty world, four base substeps, default interaction budgets, 30 warmup and 60 measured ticks, single-cell fragments in a sparse grid:

| Bodies | Mean tick | p95 tick | Pair candidates on final tick |
|---:|---:|---:|---:|
| 1 | 0.1868 ms | 0.2598 ms | 0 |
| 10 | 0.2691 ms | 0.3445 ms | 2,205 |
| 32 | 2.0147 ms | 7.1572 ms | 24,304 |
| 100 | 9.0159 ms | 12.3424 ms | 242,550 |

These are full ticks including broad-phase, contact, support and snapshot work, not isolated snapshot overhead; one exploratory run with JIT/GC noise does not establish a regression or speedup. No allocations or GC were measured. The snapshot is O(body count) at tick entry and is not repeated per microstep; the existing collision work may be quadratic. The 60 TPS budget is 16.67 ms for the full simulation, not just physics.

## Limitations

Physics remains deterministic only for identical inputs and floating-point runtime behavior. Existing approximate collision/sleep limitations, including the absence of exact CCD, remain. No UI, save, body occupancy or material coupling was added; GUI behavior was not verified.

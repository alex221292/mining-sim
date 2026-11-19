# M4.5b.3a — atomic settled-rock reactivation

`SettledRockReactivation.prepareReactivation` reads the live World cells owned by one settled ID, verifies STONE, cardinal connectivity, count and exact integer mass, and builds a new descriptor from those cells using the original substance. A new monotonic fragment/body ID is allocated; the retained settled history records `reactivatedBodyId` (zero until reactivation), preserving the settled-to-body provenance. Removed ownership has zero remaining mass and cannot be reactivated again. Damaged but still connected ownership may be reactivated using its remaining cells; disconnected or inconsistent ownership is rejected. The maximum is 2,048 cells.

The initial rigid pose is unrotated at the lattice origin with zero velocities; its center of mass and inertia are recalculated from the actual placed shape. Prepared occupancy must match exactly the owned cells and must not overlap another rigid body. Source cells are the only static cells covered by the new body. All existing rigid poses, occupancy, fragment and settled revisions, generation, World and topology revisions are rechecked at commit. A dedicated owner-thread publication transfers ownership, World STONE to AIR, fragment membership, body membership and occupancy between the existing `allowBatch` and `changedBatch` notifications. Callbacks retain the existing non-reentrant, non-throwing contract. No automatic support analysis, cascade, sandbox integration, settled mining or save-format change is introduced. One cell = 0.1 m.

## Verification

`gradlew.bat test build` covers the 134/134/526 stack, sequential reactivation of 794 cells, per-rock mass and substance, provenance, deterministic replay, callback consistency, veto and precommit exception, stale World/generation/body state, occupancy budget and conflict, distinct IDs, rectangles, long thin and concave shapes, disconnected rejection, foreign bodies, BEDROCK and falling physics. A rotated MARBLE two-cell rigid body is settled and reactivated as a different lattice shape, demonstrating reconstruction from the actual placed cells rather than the original descriptor. These are headless tests; no GUI verification was performed.

## Measurements

OpenJDK 25.0.5, default JVM, headless 300×80 world, five fresh fixtures per scenario. `ReactivationBenchmark` creates static records before timing, then sequentially prepares/commits one rock and executes one coupled tick. Each number below is mean/p95 ms; samples include JIT and are exploratory, not a performance guarantee. Geometry measures `RigidBody` construction (including center and inertia), while occupancy measures both old-state verification and the new prepared index; descriptor reconstruction and post-build validation are included in total preparation, not these individual subphase numbers.

| Bodies | Cells/body | Prepare | Commit | Geometry | Occupancy | First coupled tick | Physics phase |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 32 | 2.701 / 10.920 | 0.117 / 0.388 | 0.357 / 1.536 | 0.308 / 0.767 | 6.021 / 15.639 | 3.535 / 8.783 |
| 3 | 32 | 1.512 / 11.706 | 0.068 / 0.363 | 0.160 / 1.802 | 0.427 / 0.929 | 3.904 / 15.546 | 2.131 / 8.796 |
| 10 | 32 | 0.963 / 2.066 | 0.051 / 0.076 | 0.064 / 0.052 | 0.449 / 1.477 | 3.305 / 8.759 | 2.035 / 5.067 |
| 1 | 2048 | 13.269 / 31.893 | 1.689 / 1.985 | 1.388 / 3.438 | 3.852 / 8.971 | 32.401 / 81.302 | 16.708 / 45.624 |

M4.5b.2 reported 17.003 / 35.771 ms group preparation and 0.664 / 0.822 ms commit for the different 64×64, 794-cell stack. These are different scenes and operation types and are not a like-for-like speed comparison; no acceleration is claimed. Allocation was not measured. To reproduce, run `gradlew.bat classes`, then `java -cp build/classes/java/main miningsim.structural.ReactivationBenchmark N W H 5` with `1 8 4`, `3 8 4`, `10 8 4`, and `1 64 32`.

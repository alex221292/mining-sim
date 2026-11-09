# M4.4b.4c — interactive rigid fragments

For the subsequent extraction-publication and session-retirement hardening, see `m4-4b4d-integration-hardening.md`.

The Swing event-dispatch thread owns input, the fixed 60 TPS accumulator, simulation, registry, occupancy and painting. No physics or occupancy rebuild occurs during painting. Each session owns one World, Simulation, optional structural controller/registry, RigidBodySystem, occupancy index and CoupledSimulationStep. BEDROCK_ENABLED sessions use coupling; MSIM v1 always loads LEGACY and retains cellular-only behavior. MSIM v1 cannot save bedrock worlds or fragments. Replacing a world with F4, loading or clearing with C creates fresh owners; C discards the current scene and its fragments. Pause is preserved on replacement.

Input edits and F5 selection/extraction commands are queued on the EDT and applied before the next tick; while paused use N to process them. The order is commands, bounded structural tick, tentative coupled physics/occupancy/displacement commit, then separate cellular movement. This differs from the previous structural-reference order (cells before structure). Rendering reads only committed state on the same thread. An expected coupled `IllegalStateException` skips the cellular phase and is shown in F3; three identical failures pause coupling only, without stopping camera/input. F6 retries; Space pauses the whole tick and N steps while paused. A cellular-phase exception remains unexpected and is not disguised as a rolled-back coupled tick.

F4 confirms replacement and cycles the five existing calibration scenes, then falling rock, rock/sand and rock/water. The island calibration scene is the extraction demonstration. In the three interactive fixtures a one-cell unsupported component is extracted at scene creation; the falling fixture settles on bedrock. Sand and water fixtures have a small bounded region with accessible displacement space. F5 extracts the previously analyzed unsupported component selected by middle click; connectivity must have completed. Components above 64 cells are rejected. The structural controller uses the existing extraction plan/atomic commit; after success the physics membership and occupancy are synchronized before subsequent brush input. Failures report status in F3, with no extraction on plan failure. Bedrock cannot be extracted.

The brush skips body-occupied cells for every non-AIR material, including stone and wood. Right-button AIR erases only World material and never a fragment. The same positive-area occupancy policy applies to rotated bodies. Camera zoom remains cursor-anchored, arrows pan and R resets; material keys 1–6 retain their meaning. F3 toggles structural and coupled diagnostics, F4 scenes, F5 extraction and F6 retry. The F3 display includes body/sleep counts, occupancy revision and cells, coupled tick, per-material displacements, cellular outcome, microsteps and latest failure. Failure text is truncated for display.

Each body is drawn by Java2D in stable ID order: translate to its fractional center in screen space, scale by camera zoom, rotate by body angle and translate by the negative `FragmentGeometry` local center. Individual descriptor cells are filled in the normal STONE color; holes are not filled. World cells removed by extraction are not drawn twice. No rendering snapshot is copied because all access is serialized on the EDT; repaint does not change authoritative state.

The planner permits an already vacated source as a search passage but only original AIR can be a destination. Thus batch expected values refer to the original World, no source/destination duplicate is emitted, and chains are conservatively excluded. Existing per-material conservation and coupled rollback contracts remain in force. The sampled swept-path limitation described in M4.4b.4b remains; this milestone does not change physics coefficients or add CCD.

## Headless measurements

`gradlew.bat interactiveBenchmark`, Windows, Ryzen 9 5900X, OpenJDK 25.0.5+8-LTS. BufferedImage 640×480, one-cell shapes, 20 warmups and 100 samples; mean/p95 ms. Overlay-on uses the actual coupled F3 diagnostics renderer with a separate one-body session; the world image, structural marks and other HUD lines are excluded. Allocations and actual FPS/TPS were not measured.

| bodies | overlay off | representative overlay on |
|---:|---:|---:|
| 1 | .079/.087 | .123/.178 |
| 10 | .081/.105 | .096/.114 |
| 32 | .087/.098 | .097/.113 |
| 100 | .100/.108 | .116/.139 |

Fresh sand and water scenes, 100 consecutive full session ticks, no warmup: sand .361/.637 ms, water .152/.215 ms, zero rejected ticks. This includes structural, coupling and cellular work; it is not a sustained real-time stability guarantee. No GUI interaction or platform-specific visual verification was performed; headless image and controller tests do not replace manual desktop testing.

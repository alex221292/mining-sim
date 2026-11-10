# M4.5a.3-lite — rock settling

## Policy and implementation

This is a gameplay sleep policy, not a friction or contact-geometry correction. The existing contact island, support observation, 60 fixed-tick quiet window and per-body reference pose remain in place. At the default configuration the final horizontal speed is below 0.02 m/s, vertical below 0.04 m/s, angular below 0.02 rad/s, and per-tick motion is bounded by the configured quiet speeds. Over the window, displacement is below 0.05 cells (5 mm; one cell is 0.1 m) and rotation below 0.002 rad. Microscopic motion may be settled; sustained large motion, lost support and an unstable island prevent sleep. This is not a proof of mechanical equilibrium or an exact friction model.

Previously only the final tick state was checked: a significant velocity could be cancelled by contact impulses before that observation. A headless 0.12 m/s downward impact on a supported rock advanced its quiet timer from 40 to 41 on baseline `1253e68`. Now, per fixed tick, the island window is reset if a member has a sampled translational speed of at least five times the configured quiet linear limit (0.1 m/s by default), angular speed at least ten times the angular limit (0.2 rad/s), or relative contact-point velocity/angular velocity reaches the same limits. Samples occur at tick start, after integration, before the contact solver, and after velocity solving; they are not a continuous time-of-impact measurement. Gravity, friction, restitution, narrow-phase, contact response and geometry are unchanged. A high transient prevents sleep; it never forces a body to sleep.

On sleep, the current pose is retained and velocities become zero; there is no snapping, registry transfer, material conversion or mass change. World edits, support removal and incoming bodies use the existing wake mechanisms. The disturbance set is local to one attempted physics tick, so rollback discards it automatically; body state, quiet timers, reference poses, occupancy and diagnostics remain covered by existing checkpoints. Contact-island support is still a gameplay test based on contacts, not a structural stability proof.

## Reproduction and measurements

OpenJDK 25.0.5, Gradle 9.2.1, Windows, 64×64 world, coupled fixed 60 Hz ticks. `gradlew.bat largeCompoundBenchmark --args="large large"` uses the M4.5a.2 134/134/526-cell fixture twice in the same JVM; the second run is warm. Baseline and modified run use identical profiling. Mean/p95 full tick in ms:

| Window | Baseline | Modified |
|---|---:|---:|
| ticks 30–89, settling | 13.422/14.391 | 13.151/13.588 |
| ticks 90–239, mixed active/sleep | 12.330/13.678 | 12.351/14.116 |
| ticks 240–359, sleeping | 0.687/0.724 | 0.687/0.729 |

The sleeping fixture first sleeps on tick 229 before and after. In the modified run the signed horizontal displacements from initial pose until sleep are −0.0191, +0.0669 and +0.0883 cells; absolute horizontal paths are 0.0304, 0.0672 and 0.1021 cells. The 60 subsequent sleeping ticks have zero pose change. Those paths include falling and impacts, not only quiet drift.

`SettlingWedgeTest` also rotates the two lower bodies by +0.02 and −0.02 radians and the upper body by +0.01. It slept on tick 285 both before and after. Signed X changes were −0.0999, +0.1492 and +0.0537 cells; total absolute paths were 0.1085, 0.1525 and 0.1581 cells. Two runs replayed identically and the next 60 sleeping ticks did not move. This generated fixture approximates, but does not reproduce, the user's particular drawn collapse. Existing `largeWood` and small-stack tests exercise support removal and impact wake. Times are headless measurements, not GUI FPS.

## Verification and limitations

`gradlew.bat test build` covers the wedge, single and multi-body sleep, slow and significant sliding, free fall, unsupported and rotating bodies, an initially moving body on a stepped slope, impact and support wake, replay, automatic detachment, cellular coupling and rollback. The stepped slope is not a smooth incline test. The contact solver can still create internal-cell artefacts and lateral creep before sleep; this milestone intentionally does not change it. A sleeping result must not be interpreted as proof that such artefacts are fixed. GUI behavior and the user's exact drawn scene remain unverified; manually draw and cut a rock pile, observe settling, remove a support and strike it again.

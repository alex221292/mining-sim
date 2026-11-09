# M4.5a.1 — resting contact stability

## Reproduction and diagnosis

`gradlew.bat restingStackBenchmark` runs 1,000 fixed 60 Hz ticks in a 64×40 BEDROCK_ENABLED World using the same `InteractiveSession` collision/coupling configuration. The two lower generated stone fragments each contain seven irregular cells; a 14-cell fragment spans them. The initial origins are (20,32), (25,32), (20,25). F4 also includes the same generated-fragment fixture; it is not an extraction of additional World cells. `--args=wood` adds a removable wooden support above bedrock. Physics, occupancy, displacement and cellular simulation are enabled. The benchmark uses a fixed 1,000-sample buffer, optional per-tick profiling, and a short bounded timeline; wall-clock timings never influence physics.

Before the fix, the bedrock fixture eventually slept, but even when all three bodies were asleep the shared solver repeatedly performed 17 identical body-pair queries per tick: 51 candidate-pair visits and 3,332 narrow SAT checks, with no integration or pose changes. Body queries dominated resting physics time. The removable-support variant exposed a separate sleep/wake cycle: during ticks 400–999 there were 18 wake events, no bodies asleep at tick 999, maximum tick pose change 0.001627 cell/radian and 3,332 SAT checks/tick. This is evidence of redundant work and asynchronous sleeping in these fixtures, not proof that every user's FPS drop or flicker has the same cause. Profiling did not establish a rendering artifact or incorrect collision normal as the dominant cause.

The first static impact occurs around ticks 16–20; the falling top body produces a secondary impact around tick 25. In the impact window the maximum observed reduced body-body penetration was 0.10915 cell, reduced static penetration 0.11305 cell, and one correction was at most 0.020609 cell. By ticks 400–999 after the fix, maximum reduced body-body/static penetration was 0.01368/0.01512 cell, tick-to-tick pose change and velocity were zero, and no bodies woke. These maxima are measured after contact reduction, not over discarded raw contacts. The 0.01-cell positional slop and solver coefficients were not changed.

## Change and safety

Contact-connected bodies now share a deterministic sleep island. All members must be supported, below the existing linear/angular speed thresholds, and move less than the corresponding per-tick linear/angular distance thresholds, for the existing 60 consecutive ticks. Their counters advance together from the minimum of the island; one body cannot sleep ahead of a still-moving contact neighbour. Island work consumes the existing bounded wake-work budget and fails atomically if exhausted. No mass, contact impulse, friction, restitution, collision geometry or fixed timestep changes. World edits/removals still wake sleeping bodies before integration, and a newly approaching awake body still triggers bounded wake propagation. Sleep remains a numerical approximation, not a guarantee for arbitrary stacks.

When every body is already asleep and unchanged, the tick retains one bounded static-support observation and one body-contact query, but does not repeat integration, impulses and identical contact queries for every substep. New bodies start awake; World revision changes and removed registry members wake the affected set before this check. Occupancy and cellular coupling still run. Physics, sleep counters and profiling's published record participate in rollback. The existing non-throwing World callback and sampled swept-motion limitations remain.

## Measurements

Windows, AMD Ryzen 9 5900X, OpenJDK 25.0.5+8-LTS; one cold 1,000-tick run each, no warmup, 64×40 World. Before measurements use the same benchmark and instrumentation with independent body sleep and the sleeping fast path disabled. After uses island sleep and the fast path. Phase boundaries are fixed, not independently classified events. Mean/p95/worst are complete session tick milliseconds; timers include profiling overhead. The short free-fall sample contains cold-start outliers; no real GUI FPS, CPU saturation or fan measurement was performed.

| phase, ticks | before mean/p95/worst | after mean/p95/worst |
|---|---:|---:|
| free fall 0–15 | 3.417/20.859/20.859 | 3.207/18.886/18.886 |
| impacts 16–25 | 1.988/5.152/5.152 | 2.170/5.064/5.064 |
| stacking 26–399 | 1.255/2.282/4.737 | .621/2.324/3.815 |
| rest 400–999 | .386/.422/1.597 | .085/.110/.569 |

After, the rest-window per-tick means were physics .040 ms, static query .015 ms, body query .021 ms, static/body solver below .001 ms each, occupancy .029 ms and displacement planning .005 ms. The corresponding before body-query mean was .318 ms; checks fell from 3,332 to 196/tick. Rest mean fell about 78% and p95 about 74% in this run, below the 4/8 ms resting targets. For the wood variant, rest mean/p95 changed from .698/.748 to .083/.110 ms and wakes fell from 18 to zero; all three were asleep by tick 249 instead of none at tick 999. Cold free-fall p95 exceeds 8 ms and should not be interpreted as an established warmed impact or GUI frame rate.

## Verification and limitations

`RestingStackTest` runs deterministic 1,000-tick replays, checks unchanged sleeping transforms, wake-up after support removal and after an incoming body, floor boundaries, bounded penetration, mass and velocity. Existing physics rollback, automatic detachment and sand/water coupling tests remain enabled. See the benchmark's bounded per-phase output for microsteps, pairs, checks, raw/reduced contacts, solver iterations, correction, penetration, velocities and sampled transforms. GUI interaction, long-term behaviour beyond the tested fixtures, arbitrary piles and CPU/fan behaviour remain unverified.

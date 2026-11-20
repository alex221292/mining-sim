# M4.5b.4b — Swing lifecycle and manual acceptance

The ordinary BEDROCK_ENABLED sandbox uses the M4.5b.4a lifecycle through the same `InteractiveSession.tick()`. F4 reference scenes retain their previous configurations; LEGACY stays cellular-only. The Swing timer, input, simulation and rendering remain on the EDT, without a second simulation loop or background thread. Settling still runs only after cellular movement. World cells draw settled rocks; the existing fragment renderer draws only current rigid bodies. F3 shows lifecycle counts, last rejection and last instrumented phase times. Enabling profiling takes effect on the next tick. Clear and load create a new session; F4 creates a separate reference session. Pause/step and existing F5/F6 behavior are unchanged.

`.msim` remains World-only: it does not store rigid bodies or settled provenance. Saving with either present requires an explicit warning confirmation; loading over such a session also asks for confirmation, and cannot reconstruct omitted state. The conservative planner still requires a group path to BEDROCK, not arbitrary ordinary STONE. Existing SWEEP failures can still require F6 and obstruction removal. Work is bounded, but a transition is synchronous on the EDT and may cause a visible pause; no frame-rate guarantee is made.

## Manual acceptance — pending user confirmation

Run `gradlew.bat run`. Use 3 for STONE, 1 for SAND, right mouse to erase, wheel to zoom, arrows to pan and F3 for diagnostics. The erase brush has radius three cells, so leave enough vertical separation to avoid erasing the upper rock. BEDROCK forms the bottom boundary.

1. **Collapse:** draw a STONE structure connected to BEDROCK, sever portions, and wait. Verify falling bodies, then fewer `Rigid bodies` and more `Settled`, without F5/F6.
2. **Repeat:** form a supported stack of separate settled rocks, erase enough of a lower settled rock to remove the upper rock's support without erasing the upper rock, and observe `Reactivated`, renewed falling, then settling. An ordinary STONE platform is not an accepted settling foundation in this experimental planner.
3. **Sand:** after several rocks have settled, draw three successive portions of SAND, waiting between them. Confirm continued movement and nonzero TPS.
4. **Load:** create dozens of small fragments and observe FPS/TPS, tick time, fan noise, RIGID/Settled counts and whether motion eventually stops.

Also exercise Space, N, C, R, F3–F6, zoom/pan, painting and save/load. Check that STATIC is not additionally drawn as RIGID and no ghost contours appear. These visual and interactive scenarios have **not** been manually verified for this milestone.

## EDT simulation measurements

`java -cp build/classes/java/main miningsim.benchmark.SwingLifecycleBenchmark` runs simulation on the EDT without a window, rendering, or timer scheduling. OpenJDK 25.0.5+8-LTS, Windows, 400×300 cells, profiling enabled; one run, 120 warmup ticks for the empty scene. Times in milliseconds; nearest-rank p95. Three/ten-rock scenarios use single-cell rocks; sand batches contain ten cells each. Settling transitions are whole ticks, not isolated commits.

| Scenario | n | mean | p95 | max |
| --- | ---: | ---: | ---: | ---: |
| Empty | 120 | 0.179 | 0.233 | 0.393 |
| Three awake bodies | 54 | 0.480 | 0.686 | 1.177 |
| Three settled | 120 | 0.153 | 0.282 | 0.357 |
| Ten settled | 120 | 0.140 | 0.167 | 0.241 |
| Settling transition ticks | 10 | 0.566 | 0.691 | 0.691 |
| Reactivation transition tick | 1 | 7.004 | 7.004 | 7.004 |
| Sand batches 1 / 2 / 3 | 60 each | 0.427 / 0.358 / 0.236 | 1.397 / 1.415 / 0.249 | 1.571 / 2.006 / 0.268 |

One reactivation sample is not a reliable percentile. These timings exclude painting and are not FPS; larger or denser configurations can be substantially more expensive. Manual acceptance and actual GUI frame times remain unverified.

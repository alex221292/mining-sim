# Mining Sim — Project Specification

## Vision

Mining Sim is a 2D side-view mining game inspired visually by Terraria and mechanically by falling-sand simulations. The long-term goal is an underground world in which excavation has physical consequences: sand collapses, water floods tunnels, different rocks behave differently, and wooden supports help stabilize excavations.

**This is a learning-driven, incremental project.** The first deliverable is not a game: it is a small interactive materials sandbox that teaches us how to build a deterministic, testable simulation and a replaceable renderer.

## Design principles

1. **Simulation first.** A world can advance without a window or GPU.
2. **CPU first.** Java2D is a temporary renderer; do not make the simulation depend on Java2D types.
3. **Data-oriented where useful.** Store cells in primitive arrays, not one Java object per cell.
4. **Small, measurable steps.** Each milestone should produce something runnable and testable.
5. **No premature framework.** No Spring, dependency injection container, generic ECS, plugin framework, or complex event bus.
6. **Explicit trade-offs.** Prefer simple, correct rules before sophisticated approximations.
7. **Deterministic tests.** The simulation must accept a seed for any randomness.

## Initial technology stack

- Java 25 (JDK 25)
- Gradle with Gradle Wrapper; use a Gradle release compatible with JDK 25
- Java2D / AWT / Swing for the prototype
- JUnit Jupiter for automated tests
- No third-party physics engine
- Later, optionally: LWJGL + OpenGL for rendering

## Initial scope: Milestone 1 — Falling Sand Sandbox

A runnable desktop application with:

- A window with a drawable world, initially **400 × 300 cells**; scale to fit the window (for example, 2 screen pixels per cell).
- A world backed by a flat primitive array, with a small `Material` enum or ID mapping.
- Materials: `AIR`, `STONE`, `SAND`, `WATER`, `WOOD`.
- Mouse painting with selectable brush material; right mouse button erases to air.
- Sand falls down, then diagonally if blocked.
- Water falls down, then flows sideways if blocked.
- Stone and wood are static in this milestone.
- Fixed-timestep simulation (target **60 simulation ticks per second**), independent of rendering.
- Pause/resume, single-step, clear/reset controls.
- Basic overlay: FPS, simulation ticks per second, active cell count or other useful diagnostic.
- Unit tests for world indexing, material behavior, bounds, and deterministic updates.
- README with build/run/test instructions.

### Acceptance criteria

1. `./gradlew test` succeeds.
2. `./gradlew run` opens the sandbox on a supported desktop environment.
3. Painting sand above air makes it fall; painting water makes it flow; stone and wood remain static.
4. Painting or simulating at the boundaries never causes out-of-bounds access.
5. Pausing stops simulation updates; single-step advances exactly one tick.
6. Simulation code imports no `java.awt.*`, `javax.swing.*`, or OpenGL classes.
7. The renderer can be replaced without rewriting material update rules.
8. A fixed initial world and random seed produce reproducible simulation states.

### Non-goals for Milestone 1

No player character, mining tools, inventory, crafting, procedural terrain, infinite world, chunks, lighting, save system, networking, structural stability, GPU physics, realistic fluid dynamics, or large-scale multithreading. These are future possibilities, not first-milestone requirements.

## Proposed minimal architecture

Suggested packages (adjust if a simpler arrangement is clearer):

```text
src/main/java/.../miningsim/
  Main.java                 # composition root
  app/                      # window, input, game loop
  world/                    # World, Material
  simulation/               # Simulation, update rules
  render/                   # Renderer abstraction, Java2D implementation
src/test/java/.../miningsim/
```

### World model

For Milestone 1, use a fixed-size world:

```java
final class World {
    private final int width;
    private final int height;
    private final byte[] materials; // y * width + x
}
```

Define safe access semantics for out-of-bounds coordinates explicitly. Avoid exposing mutable internal arrays outside the simulation package. Add separate property arrays only when justified by a feature.

### Simulation model

- Provide a headless `step()` method that advances one fixed simulation tick.
- Prefer bottom-up processing for gravity-driven materials; take care to prevent cells moving multiple times in one tick.
- Alternate horizontal scan direction or use deterministic seeded tie-breaking to reduce directional bias.
- Define material movement rules clearly and test them individually.
- Start with discrete, game-oriented fluid rules, **not** SPH/Navier–Stokes.
- Avoid per-cell allocations in the hot loop.
- Track dirty/active regions only after a correct full-grid implementation exists and profiling justifies it.

### Rendering model

The renderer is responsible for visualizing world state, not updating physics. For the Java2D implementation, prefer writing colors to a reusable `BufferedImage` pixel buffer and scaling the image, rather than calling `fillRect` once per cell. Keep any AWT/Swing types inside app/render code.

The render abstraction should be small. A future OpenGL implementation may upload a pixel/ID texture and colorize it with a shader. **Moving rendering to GPU does not automatically move physics to GPU**; that is a separate, much larger redesign.

### Timing and input

Use a fixed simulation timestep with an accumulator and a maximum number of catch-up steps per rendered frame. Keep Swing/AWT event handling and simulation mutation coordinated to avoid data races. The initial implementation may be single-threaded except for AWT event dispatch, provided input is safely transferred to the simulation loop.

## Roadmap

### M1 — Interactive materials sandbox

Fixed-size world, sand/water/stone/wood, Java2D, painting, pause/step, diagnostics, tests.

### M1.5 — Simulation hardening and baseline

Deterministic invariant tests, bounded tick timing in the overlay, a headless seeded benchmark, and architecture/performance notes are implemented. On the documented Ryzen 9 5900X / JDK 25 run, active-world mean step times were 1.079 ms (400×300), 4.413 ms (800×600), and 13.414 ms (1600×900). These are exploratory, headless, single-run measurements, not frame-time guarantees; see `docs/performance-baseline.md` for methodology, other scenarios and limitations. GUI verification of the new overlay remains outstanding.

### M1.6 — Water flow

Water prioritizes downward, diagonal and bounded lateral movement, with deterministic basin and conservation tests. A water particle may travel across up to eight contiguous air cells in one horizontal move, still at most once per tick. This is a local discrete approximation rather than a pressure or velocity model. The basin fixture and before/after benchmark are documented in `docs/water-flow.md`; graphical behavior after the change has not been verified in this environment.

### M2 — Performance and richer materials

Profiling with Java Flight Recorder, explicit material IDs/categories, gravel and locally cohesive soil, interaction tests and larger-world benchmarks are implemented. FULL_SCAN remains the default; ACTIVE_REGIONS is experimental due to dense-world overhead and scheduling differences. Material rules and limitations: `docs/materials.md`; measurements: `docs/m2-material-performance.md`. No chunk storage or global structural mechanics is introduced.

### M3 — Chunked world and camera

Chunk storage, camera/viewport, world generation, sleeping/inactive chunks, persistence format.

M3.1 camera and M3.2 bounded 64×64 chunk storage are implemented; generation, chunk streaming and persistence remain future work. Full-grid simulation benchmark results and the substantial storage overhead observed in this environment are documented in `docs/m3-chunk-performance.md`.

M3.3 adds cross-boundary and activity lifecycle validation, an optional headless propagation benchmark and diagnostics; the existing physics and experimental activity scheduling are unchanged. See `docs/m3-activity-propagation.md` for limitations and results.

M3.4 adds bounded world-only versioned MSIM save/load with CRC32 and desktop controls. It does not save an exact simulation snapshot or implement streaming chunks. See `docs/m3-save-format.md`.

### M4 — Mining and structural stability

M4.0 is design only: the confirmed structural physics and construction decisions, proposed ownership, M4.1–M4.10 roadmap and reference scenes are in `docs/m4-structural-design.md`. Connectivity alone is not a stress solver. Numerical strengths and several implementation choices remain uncalibrated; no M4 physics or construction is implemented by M4.0. Do **not** claim physical realism without validation.

M4.3c connects the small selected-component rock reference scenes to Swing diagnostics; limitations and controls are in `docs/m4-3c-swing.md`. Rock fragments do not move.

M4.4b.4c adds fixed-tick interactive rigid fragments, occupancy-aware painting and coupled demonstration scenes. See `docs/m4-4b4c-rendering-controls.md` for ownership, controls, measured headless performance and limitations; desktop GUI verification remains outstanding.

### M5 — Better graphics

LWJGL/OpenGL renderer, GPU texture updates, lighting, visual effects. Preserve CPU simulation initially.

### M6 — Gameplay

Progression, equipment, hazards, water management, material-specific mining, inventory and goals.

## Open design questions

- One cell represents 10 cm; adaptive structural elements remain a future optimization, not a different cell scale.
- Detached rock becomes coherent rigid fragments; impact fracture and collision details remain open.
- How should water volume and pressure be approximated at different scales?
- What performance target is appropriate for active simulation cells on the target hardware?

## Definition of done for changes

A change is done when it compiles, tests pass, behavior can be demonstrated, architecture boundaries remain intact, and the README or this specification is updated when the behavior or design changes.

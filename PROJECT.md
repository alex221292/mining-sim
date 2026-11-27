# Mining Sim — Project Specification

Headless settled-rock lifecycle integration and limitations: [M4.5b.4a](docs/m4-5b4a-headless-lifecycle.md).
Swing lifecycle integration and pending manual acceptance: [M4.5b.4b](docs/m4-5b4b-swing-lifecycle.md).
Prospective settled-rock assembly support: [M4.5b.4c](docs/m4-5b4c-prospective-support.md).
Stack reactivation and support-load validation: [M4.5b.4c.1](docs/m4-5b4c1-stack-fix.md).
Disconnected settled-component reactivation: [M4.5b.4c.2](docs/m4-5b4c2-component-reactivation.md).
Fixed-block sandbox: [M4.6a](docs/m4-6a-fixed-block-rock.md).
Fixed blocks displacing water: [M4.6b](docs/m4-6b-water-displacement.md).
Open-pool obstruction fix: [M4.6b.1](docs/m4-6b1-open-pool.md).
Fixed-block stabilization and fall delay: [M4.6b.2](docs/m4-6b2-stabilization.md).
Configurable fixed-block size: [M4.6c](docs/m4-6c-block-size.md).
Fixed-block session persistence: [M4.7](docs/m4-7-fixed-block-persistence.md).
Save input synchronization and user acceptance of M4.7/M4.7.1: [M4.7.1](docs/m4-7-fixed-block-persistence.md).
Fixed-block structural support, accepted by the user: [M4.8a](docs/m4-8a-block-support.md).
Local horizontal support reset, accepted by the user: [M4.8a.1](docs/m4-8a-block-support.md).
Deterministic procedural world generation, accepted by the user: [M5.1](docs/m5-1-world-generation.md).
Geological layers, accepted by the user: [M5.2](docs/m5-2-geological-layers.md).
Geological diversity calibration, accepted by the user: [M5.2.1](docs/m5-2-geological-layers.md).
Procedural caves with M5.3.1 branching morphology, accepted by the user: [M5.3 / M5.3.1](docs/m5-3-procedural-caves.md).
Natural underground and surface water generation, visually accepted by the user: [M5.4](docs/m5-4-natural-water.md). The user positively assessed the water bodies and observed interaction of water, sand and the rock mass.

M5.5a fixed-block rock identities, accepted by the user: [M5.5a](docs/m5-5a-rock-material-identity.md). All manual cases passed; equal horizontal support distance for all rocks is expected at this stage. Individual strengths are planned separately for M5.5c.

Procedural rock geology, visually accepted by the user: [M5.5b](docs/m5-5b-procedural-rock-geology.md). Seeded correlated fields assign fixed-block rock identity without changing terrain geometry or MSIM v3. Individual rock strength remains a task for M5.5c.

M5.5c material-weighted support was manually accepted by the user: [M5.5c](docs/m5-5c-rock-strength.md). The accepted limits, mixed beams, stepped structures, generated worlds, water excavation and save/load are documented there. This game model does not account for total supported mass, bending moments or stress concentration at the base of a cantilever.

M5.6a multi-component ore content was manually accepted by the user: [M5.6a](docs/m5-6a-multi-component-ore-content.md). The user confirmed F3 composition and concentrations, falling into water with composition retained, ore-free generated worlds, and save/load.

M5.6b procedural ore deposits and the F9 diagnostic overlay were manually accepted by the user: [M5.6b](docs/m5-6b-procedural-ore-deposits.md). The user checked generated worlds at both block sizes, positively assessed the deposits and procedural generation, and found the 4×4 deposits even more interesting.

M6.1 fixed-block mining interaction was manually accepted by the user: all eight proposed checks passed, including rock resistance and F3, both block sizes, interruption and target switching, pause and single-step, multicomponent ore and F9, conservation on completion, save/load and existing brushes and generation: [M6.1](docs/m6-1-mining-interaction.md).

M6.1a block damage visualization was visually accepted by the user, who confirmed it works and provided screenshots showing different crack stages on 4×4 and 8×8 blocks in generated worlds: [M6.1a](docs/m6-1a-block-damage-visualization.md). The next stage is M6.2 — Physical Extraction & Rock Grit, not yet implemented.

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

### Future architectural directions (not implemented or assigned milestone numbers)

**Structural Damage Visualization:** Block damage templates may be reused to display structural fatigue and overloaded ceilings and supports. Mining progress and structural damage remain independent states with distinct physical semantics; only the visual mechanism may be shared. Warning dust falling from a ceiling before actual failure is purely visual: it is not a world cell, adds no physical mass, changes no block mass, size, shape, strength or ownership, and participates in neither collisions nor saves. Real rock grit produced by mining or failure is a separate future matter-conserving mechanic. Not implemented.

**Rendering Backend Independence:** Simulation determines world state; the graphics backend determines how to display it. New physics and gameplay systems must not depend on Java2D, Graphics2D, BufferedImage, Path2D, Swing or a future GPU API. Data needed for display must have an API-independent representation; backend-specific resources and operations stay in the rendering layer. Avoid requiring one new object per block per frame, and account for performance and data transfer volume.

**GPU-Accelerated Rendering:** A future hardware-accelerated backend should not require rewriting simulation core, cellular physics, block support, generation, mining, ore model or persistence. LWJGL + OpenGL is a candidate, not a final technology choice; Java2D may remain an alternative diagnostic backend. GPU rendering does not imply GPU physics. Not implemented.

**Texture Atlas & Batched Rendering:** Future texture atlases, cached visual resources and batched rendering of blocks and other world elements. Not implemented.

**GPU Lighting & Visual Effects:** Future hardware-accelerated lighting, fog of war, visibility masks and visual particles. Gameplay visibility and discovery rules for rock and ore remain independent of the graphics backend. Not implemented.

**World Pre-Simulation:** Generate the entire finite world, then run a global headless physical simulation to settle unstable rock, granular materials and water. Freeze the resulting initial world state, choose a safe player spawn and begin gameplay. This stage is not restricted to the visible region or real-time 60 TPS. Define stabilization criteria and a bounded compute budget; use existing physics without cave-specific rules.

**Dynamic World Activation:** During gameplay distinguish the visible region, a physically active area around the player, a neighboring chunk preload buffer, and inactive regions whose state is preserved. Activate regions when disturbances propagate, including collapses, granular movement and water flows. Active-region boundaries must not become artificial physical barriers or supports. These are future directions, not current-stage requirements.

**Directional Block Movement & External Impulses:** Future research into generalized atomic grid movement of fixed blocks, horizontal movement of free blocks, and character pushing of individual blocks or groups, limited by mass and future gameplay characteristics. Explore temporary external impulses, simplified ballistic motion, localized game explosions and block dispersal, and optional low-priority experimental diagonal sliding of free blocks. Distinguish free rocks from blocks in a monolithic mass; preserve ownership, rock and substance identity, determinism and operation atomicity, accounting for collisions, water displacement and subsequent support recalculation. An explosion's game radius limits initial effects, not necessarily subsequent structural collapses. Keep square blocks fixed in orientation and aligned to the grid; do not return to arbitrary-shape rigid bodies, rotation or the historical rigid-body solver. Roadmap only; not implemented.

**Advanced Structural Load Model:** Future research into support loads, the mass of supported blocks, weak boundaries and stress concentrations. Do not replace the accepted support model without separately agreed architecture and acceptance criteria. Roadmap only; not implemented.

**Underground Fog of War:** In gameplay hide unexplored underground rock, ore, caves, water, sand, gravel and other materials, without revealing cavities by silhouettes. Exposed surface and observable excavation boundaries are visible under future lighting/visibility rules; retain the full-view sandbox. Not implemented.

**Dynamic Lighting & Visibility:** Future light sources, mine illumination and rock occlusion. Separate world state, physical activation, lighting and visibility; invisibility does not automatically suspend physics. Not implemented.

**Exploration State:** Future memory of explored regions must not reveal the current state of hidden rock through previously explored maps. Not implemented.

**Geological Prospecting:** Future Vintage Story-inspired analysis of accessible samples and nearby actual ore content within a limited radius; tool quality controls accuracy and detection threshold, with results in ppm or percent and multiple measurements for exploration. Do not expose exact ore coordinates or hidden geometry. Define mass weighting, the concentration denominator and treatment of voids separately. Not implemented.

**Exploratory Drilling:** Future directed drilling beyond accessible workings and analysis of extracted samples. Not implemented.

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

M4.4b.4d hardens interactive extraction publication, session retirement and deterministic replay; measured limits and remaining GUI/physics limitations are documented in `docs/m4-4b4d-integration-hardening.md`. Automatic detachment was not part of that milestone.

M4.5a adds bounded automatic detachment of unsupported STONE in the ordinary bedrock sandbox. The support rule, paint grouping, 1,024-cell reference limit and performance are documented in `docs/m4-5a-auto-rock-detachment.md`.

M4.5a.1 coordinates sleeping across contact islands and avoids redundant sleeping-stack queries. Headless before/after measurements and remaining contact and GUI limitations are in `docs/m4-5a1-contact-stability.md`.

M4.5a.2 accelerates large compound contacts and explicitly allows supported microscopic sliding to sleep. Measurements, determinism, limitations and manual checks: `docs/m4-5a2-large-compound-contacts.md`.

M4.5a.3-lite keeps the gameplay sleep window while preventing significant within-tick motion from counting as quiet. See `docs/m4-5a3-lite-rock-settling.md`.

M4.5a.4 separates coupled rigid-terrain revision from cellular changes to prevent unnecessary wakes. Its follow-up adds bounded atomic cellular recovery after verified swept-obstruction rollback and explicit blocked diagnostics; persistent obstructions can still require intervention. See `docs/m4-5a4-cellular-after-collapse.md`.

M4.5a.5-design specifies a HELD rigid-body contract; M4.5a.5b.1 implements an internal trial-only solver foundation, not automatic SWEPT recovery. See `docs/m4-5a5-held-body-design.md` and `docs/m4-5a5b1-held-solver.md`.

M4.5b.1 adds a settled-rock ownership registry and ownership-aware rock bond boundaries. Settling, re-detachment and sandbox integration are not implemented. See `docs/m4-5b1-settled-ownership.md`.

M4.5b.2 provides a bounded headless group-settling transaction with separate ownership and idealized BEDROCK support checks. Automatic sandbox settling and re-detachment remain unimplemented; limitations and measurements are in `docs/m4-5b2-group-integration.md`.

M4.5b.3a adds explicit headless atomic reactivation of a settled rock using its current occupied cells, with provenance and mass conservation. Automatic reactivation and cascade remain unimplemented; see `docs/m4-5b3a-reactivation.md`.

M4.5b.3b adds a budgeted headless support coordinator and atomic dependent-group reactivation; the conservative support model, quarantine, limits and measurements are in `docs/m4-5b3b-support-cascade.md`. Swing integration remains future work.

### M5 — Better graphics

LWJGL/OpenGL renderer, GPU texture updates, lighting, visual effects. Preserve CPU simulation initially.

### M6 — Gameplay

Progression, equipment, hazards, water management, material-specific mining, inventory and goals.

**M6.1a: Block Damage Visualization** implements ten cumulative, backend-independent crack stages with cached Java2D masks; the user accepted the visual result for both block sizes. See [M6.1a](docs/m6-1a-block-damage-visualization.md). The next stage is M6.2 — Physical Extraction & Rock Grit.

## Open design questions

- One cell represents 10 cm; adaptive structural elements remain a future optimization, not a different cell scale.
- Fixed square blocks are the primary physical form of rock. A return to arbitrary-geometry rigid bodies is not planned. Future mining is intended to convert a block into same-substance GRIT cells using the existing granular physics; FRAGMENT is not a planned physical form, and ITEM is an inventory representation, not a physical body. Historical rigid-body and fragment work remains in the repository but must not be integrated into the new architecture or deleted. GRIT, inventory representation and mining are not implemented by M5.5a.
- How should water volume and pressure be approximated at different scales?
- What performance target is appropriate for active simulation cells on the target hardware?

## Definition of done for changes

A change is done when it compiles, tests pass, behavior can be demonstrated, architecture boundaries remain intact, and the README or this specification is updated when the behavior or design changes.

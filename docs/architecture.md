# Architecture and limitations

`World` owns the fixed-size, flat `byte[]` material state; coordinates are checked and out-of-bounds access throws. Callers do not receive the mutable array. Painting and clearing deliberately replace cells; simulation movement preserves material counts.

`Material` has explicit stable IDs and categories; granular materials can displace liquid. `Simulation` is headless, runs only on explicit `step()` calls, and depends on neither Swing nor time. Seeded randomness, bottom-up traversal and a per-tick moved mask define the current discrete rules. Granular material can exchange places with water, but cannot displace water that has already moved in that tick. Water can move once per tick across up to eight contiguous air cells horizontally; see `water-flow.md`. FULL_SCAN is the default; ACTIVE_REGIONS remains experimental (`m2-activity.md`). No stable particle identities are stored; material behavior is documented in `materials.md`.

`SimulationControl` implements pause and single-step and times actual calls to `step()` outside the physics. `Main` owns the 60 Hz accumulator and caps catch-up at five ticks; elapsed time beyond that cap is dropped. Paused time is discarded. FPS, TPS and timings measure different things.

Swing's event dispatch thread (EDT) handles input, simulation, timer callbacks and painting. This avoids concurrent world mutation, but slow simulation or rendering delays input and frames. `Java2DRenderer` fills a reusable image buffer and scales it; it does not advance physics. The small `Renderer` interface nevertheless takes `Graphics2D`, so it is an abstraction between Java2D implementations, not a backend-neutral graphics API.

An eventual LWJGL/OpenGL implementation will need a different rendering boundary and appropriate window/context ownership, input and thread coordination; the current interface cannot directly support it. Material rules need not depend on that choice. No such redesign, concurrency, chunk storage or GPU physics is introduced here.

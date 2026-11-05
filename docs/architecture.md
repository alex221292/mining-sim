# Architecture and limitations

`World` owns the fixed-size, flat `byte[]` material state; coordinates are checked and out-of-bounds access throws. Callers do not receive the mutable array. Painting and clearing deliberately replace cells; simulation movement preserves material counts.

`Simulation` is headless, runs only on explicit `step()` calls, and depends on neither Swing nor time. Seeded randomness, bottom-up traversal and a per-tick moved mask define the current discrete rules. Sand can exchange places with water, but cannot displace water that has already moved in that tick. The full grid and moved mask are scanned each tick; no stable particle identities are stored.

`SimulationControl` implements pause and single-step and times actual calls to `step()` outside the physics. `Main` owns the 60 Hz accumulator and caps catch-up at five ticks; elapsed time beyond that cap is dropped. Paused time is discarded. FPS, TPS and timings measure different things.

Swing's event dispatch thread (EDT) handles input, simulation, timer callbacks and painting. This avoids concurrent world mutation, but slow simulation or rendering delays input and frames. `Java2DRenderer` fills a reusable image buffer and scales it; it does not advance physics. The small `Renderer` interface nevertheless takes `Graphics2D`, so it is an abstraction between Java2D implementations, not a backend-neutral graphics API.

An eventual LWJGL/OpenGL implementation will need a different rendering boundary and appropriate window/context ownership, input and thread coordination; the current interface cannot directly support it. Material rules need not depend on that choice. No such redesign, concurrency, active regions or GPU physics is introduced here.

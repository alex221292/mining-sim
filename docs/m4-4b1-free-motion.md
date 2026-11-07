# M4.4b.1 — fragment geometry and free motion

This headless, opt-in layer is not connected to Swing or `World`. The registry owns the fragment and its exact material mass; the body holds a reference and motion state, not a second material owner. A system is bound to one registry. Synchronization creates one body per stable ID, preserves existing body state, removes bodies whose IDs were removed, and iterates in ascending ID order. A scene reset requires a new registry and system; it is not integrated into the UI yet.

Cells are 0.1 m wide, with downward-positive Y; positions are in continuous cell coordinates and refer to the center of mass. The local center is the average of cell centers. The initial center is the fragment origin plus this local center, so zero rotation preserves the original placement. Positive angles rotate clockwise in screen coordinates. Mass is converted from the descriptor's exact milligrams to kg. Inertia in kg·m² is the sum over cells of `m * (0.1² / 6 + distanceToCenterMetres²)`. Nonpositive or non-finite mass and inertia are rejected.

An explicit `step()` synchronizes the registry then advances one 1/60 s tick using semi-implicit Euler, gravity 9.81 m/s² and four substeps by default; tests can configure a different count. Velocity is in m/s, angular velocity in radians/s, and displacement is divided by 0.1 to convert metres to cells. No wall clock or renderer advances the system. Setters reject non-finite velocities; integration rejects a non-finite result. Overflow rejection is not a transactional rollback of an entire multi-body tick. Determinism assumes the same initial state, operations and runtime floating-point behavior.

`SquareCollision` independently tests two unit oriented squares using SAT; exact touching is not penetration. It returns a normal and depth, not a contact point or solver response.

**Limitations:** free bodies pass through world cells, boundaries and other bodies. There is no collision response, tunnelling protection, occupancy index, sleep, or UI integration. Do not use this free-motion layer as collision-safe gameplay physics. This substage does not alter saves or cellular simulation.

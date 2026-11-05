# Agent Instructions — Mining Sim

Read `PROJECT.md` before proposing architecture or implementing features. Treat it as the product and milestone specification. This file governs how coding agents should work in this repository.

## Mission

Build the smallest correct, runnable, and testable implementation of **Milestone 1: Falling Sand Sandbox**. Work incrementally. Do not silently expand scope into a complete game engine.

## Workflow

1. Inspect the repository and read `PROJECT.md`, `AGENTS.md`, and any existing `README.md`.
2. Briefly state your intended change and affected files before making a substantial change.
3. Implement a small vertical slice; prefer a running prototype over speculative abstractions.
4. Add or update tests for simulation behavior and boundary cases.
5. Run `./gradlew test` and, when possible, `./gradlew build`. Report actual outcomes; never claim a command succeeded if it was not run.
6. If a graphical session is available, run the app and verify interaction; otherwise state that GUI behavior was not verified.
7. Summarize changes, decisions, and the next small step.

If the repository is empty, bootstrap the Gradle Java application, Gradle Wrapper, tests, and README first. Use a Gradle version compatible with JDK 25. If JDK 25 or Gradle cannot be installed in the environment, explain the blocker rather than quietly switching versions.

## Architecture rules

- Keep **world state**, **simulation**, **rendering**, and **input/application loop** separate.
- Simulation must be headless: no dependencies on AWT, Swing, OpenGL, or windowing.
- Renderer must never determine physical state or advance simulation time.
- Use primitive arrays for cell state; avoid an object per cell.
- Prefer a fixed-size flat world for M1. Do not add chunking until its milestone.
- Prefer a minimal renderer interface over a generalized graphics framework.
- Do not introduce Spring, Guice, reflection-based DI, ORM, a generic ECS, an event bus, or networking.
- Avoid allocating objects in per-cell update loops unless necessary and justified.
- Keep source code and identifiers in English; documentation and explanations may be in Russian.
- Prefer clear names and short comments explaining *why*, not comments restating code.

## Simulation correctness

- Simulation advances through explicit fixed ticks (`step()`); do not use wall-clock time inside material rules.
- Any randomness must be seedable and deterministic in tests.
- Define behavior at boundaries explicitly; never index arrays without ensuring valid coordinates.
- Ensure a particle cannot unintentionally move multiple times during one tick.
- Do not overwrite occupied cells or duplicate/delete materials except where a documented rule explicitly does so.
- For basic sand/water movement, preserve material counts in closed worlds.
- Test representative cases: falling, blocked movement, diagonal sand movement, sideways water movement, bottom/edge boundaries, immobile stone/wood, and repeatability.
- Prefer simple, documented cellular automaton rules over premature physically accurate fluid simulation.

## Performance guidelines

- Start with a straightforward full-grid simulation and measure before optimizing.
- Use `byte[]` or another compact primitive representation for material IDs.
- Use `BufferedImage` with direct pixel-buffer writes for the Java2D renderer where practical.
- Avoid one `Graphics2D.fillRect` call per cell as the primary high-density render path.
- Track FPS and simulation tick rate separately.
- Do not parallelize simulation updates until data dependencies, determinism, and benchmarks are understood.
- Do not implement GPU compute as part of M1.

## Input and threading

- Use a fixed-timestep accumulator with a reasonable catch-up cap.
- Keep AWT event dispatch safe: do not mutate simulation state concurrently from UI callbacks without synchronization or queued commands.
- Make pause, single-step, brush selection, erase, and reset behavior explicit and testable where possible.
- Handle window closing and loop shutdown cleanly.

## Scope and change control

- Implement only the requested milestone or user-approved extension.
- Ask before introducing major dependencies, architectural rewrites, or changing the simulation representation.
- If the specification is ambiguous, choose the simplest reversible option, document it, and proceed when risk is low.
- Do not remove tests to make a build green.
- Do not commit generated build outputs, IDE files, secrets, or machine-specific configuration.
- Do not run destructive commands or rewrite repository history without explicit approval.

## Suggested first execution plan

1. Create a Gradle Java 25 application with wrapper and JUnit Jupiter.
2. Implement `Material` and fixed-size `World`, plus tests.
3. Implement headless sand/water simulation, plus deterministic tests.
4. Implement Java2D window, pixel-buffer renderer, and fixed-timestep loop.
5. Add mouse painting, erase, pause, single-step, reset, and basic diagnostics.
6. Update README with exact commands and controls; run tests and build.

## Initial agent task

> Read `PROJECT.md` and `AGENTS.md`. Implement Milestone 1 as a small runnable Java 25 + Gradle project. First show a concise implementation plan, then create the code, tests, Gradle Wrapper, and README. Run the tests and build. Keep physics independent of Java2D. Do not implement later milestones. Report any unverified GUI behavior or environment limitations.

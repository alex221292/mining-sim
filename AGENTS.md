# Agent Instructions — Mining Sim

This file defines repository-wide instructions for coding agents working on **Mining Sim**. Read it before making changes. It replaces the original Milestone 1 bootstrap instructions; do not restart M1 or treat historical plans as the current task.

## Mission and source of truth

Build Mining Sim incrementally as a deterministic, testable Java simulation game. The project currently includes cellular materials, chunked world storage, structural rock analysis, coherent rock fragments, and early rigid-body physics. Follow the **current user-approved milestone**, not an obsolete milestone number embedded in older documents.

Before implementing a feature:

1. Read `PROJECT.md`, this file, and relevant existing documentation, especially the current milestone's EN/RU documents.
2. Inspect the actual code, tests, and Git status. Do not assume APIs or repository state from previous conversations.
3. Resolve the requested scope and acceptance criteria from the latest explicit user request. Use `PROJECT.md` for product context and established decisions; use current code and tests to establish implementation contracts.
4. Preserve existing behavior unless the requested milestone explicitly changes it. Call out conflicting specifications rather than silently choosing a destructive interpretation.

The user's current task defines **what must be completed**. Small implementation slices define **how to get there**, not **when to stop**.

## Execution continuity — critical

When the user requests implementation of a complete milestone or a specified set of acceptance criteria, execute it end-to-end within the available environment.

1. Inspect and form a concise internal implementation sequence. Briefly state the intended change and affected areas if useful; then implement.
2. Implement a small, testable slice.
3. Run focused tests; diagnose and fix failures.
4. **Continue immediately** to the next remaining slice. Do not end the turn merely because a class is implemented, a test passes, or a subphase works.
5. Repeat until all requested acceptance criteria are satisfied.
6. Run regression tests and the requested build; measure performance when required.
7. Update documentation and its Russian translation.
8. Review the diff and commit if the user explicitly requested a commit and the milestone is complete.
9. Only then provide a final completion report.

**A successful intermediate build is a checkpoint, not a completion condition.** Do not replace unfinished work with a summary of what remains. Do not voluntarily narrow the user's requested milestone, invent a smaller deliverable, or request confirmation between routine implementation phases.

Keep a lightweight checklist for multi-step work, if useful, and revisit remaining items after each tool call. Progress messages are optional; they must not substitute for execution. Do not make artificial promises about guaranteed uninterrupted execution or tool availability.

### Legitimate reasons to stop early

Stop without claiming completion only when:

- A real permission, tooling, resource, context, or execution limit prevents continuation.
- A high-impact ambiguity requires a user decision and cannot be resolved safely from existing contracts.
- A concrete technical blocker persists after reasonable investigation and attempted fixes.
- Continuing would require an unauthorized destructive operation or violate an explicit constraint.

When blocked, preserve the working tree. Report the exact failing command or invariant, the attempts made, the files changed, and the next actionable step. Distinguish clearly between **blocked**, **partially implemented**, and **complete**. Never create a misleading milestone-complete commit.

If the user explicitly requests a review, plan, explanation, or a single small change rather than end-to-end implementation, perform only that task. These continuity rules do not authorize expanding scope.

## Repository and development environment

- Language: **Java 25**.
- Build: **Gradle Wrapper**, currently Gradle 9.x; check the wrapper rather than assuming a version.
- Desktop UI: **Swing / Java2D**.
- Tests: use the repository's existing test framework and conventions.
- Prefer `gradlew.bat test build` on Windows and `./gradlew test build` on Unix-like environments. Use the command appropriate to the actual environment.
- Run focused tests during implementation, then the full requested suite before declaring completion.
- Report only commands actually executed and their actual results. If GUI verification is unavailable, state that explicitly.
- Do not change the JDK, Gradle version, major dependencies, or build system merely to bypass a local environment problem.

## Architecture boundaries

- Keep **World and material storage**, **cellular simulation**, **structural analysis**, **rigid-body simulation**, **rendering**, and **input/application orchestration** separate.
- Core physics and structural systems must be headless. They must not depend on AWT, Swing, graphics contexts, or UI timing.
- Rendering and repaint must never advance simulation time, apply damage, move bodies, or mutate physics state.
- Input/UI events must not mutate simulation state concurrently with a simulation tick. Use the project's established command/ownership pattern.
- Preserve existing world storage and chunk contracts; do not rewrite them for convenience.
- Keep material cells compact; avoid per-cell heap objects and allocations in hot update loops unless measured and justified.
- Do not introduce Spring, Guice, reflection-based DI, ORM, a generic ECS, an event bus, networking, or a general-purpose physics engine without explicit approval.
- Favor narrow, comprehensible APIs over speculative frameworks. Avoid unrelated refactoring in a milestone commit.
- Keep source code, identifiers, and code comments in English. User-facing explanations may be in Russian.

## Simulation invariants

- Simulation progresses through explicit fixed ticks. No wall-clock time in physical rules or damage accumulation.
- Randomness must be explicitly seeded and reproducible in tests. Never use uncontrolled randomness to decide authoritative physics outcomes.
- Define all coordinate systems and units at API boundaries. In current physical models, **1 cell = 0.1 m**; do not mix cell coordinates with meters, kilograms, seconds, or radians implicitly.
- Define boundary behavior explicitly; validate indices and finite numeric values.
- Do not duplicate, silently delete, or overwrite matter. Any conversion, extraction, or destruction must follow an explicit documented conservation/ownership contract.
- Respect material identity and provenance. A future marble fragment or marble rubble must not silently become generic gravel or lose its substance metadata.
- Cellular particles must not accidentally update multiple times per tick unless a documented rule permits it.
- Keep determinism independent of rendering, camera position, frame rate, hash-map iteration order, and wall-clock scheduling.
- Prefer stable IDs and deterministic iteration/contact ordering where simulation outcomes depend on ordering.

## Structural rock and fragment contracts

- `RockConnectivity` owns the rock-bond topology and its `World.ChangeSink` integration. Do not install competing listeners or bypass its authoritative bond-breaking API.
- Respect topology revision, bond incarnation, pending-state, and damage invalidation contracts. Do not consume stale structural snapshots as if they were current.
- Connectivity to foundation is not the same as mechanical stability; structural failure must follow the established load/damage model.
- Do not run unbounded full-world structural solves inside the simulation tick. Preserve explicit work budgets and failure statuses.
- Coherent fragment extraction transfers ownership atomically from `World` to the fragment registry. Never expose a partially extracted world to topology callbacks or observers.
- Once extracted, a rigid fragment is owned by the fragment/physics systems, not simultaneously by cellular World storage. Do not silently rasterize moving fragments back into World.
- Preserve fragment material composition, mass, stable identity, and geometry. Avoid arbitrary mass creation or loss.
- Structural cracks, bond failures, and rigid-body motion are separate concerns; implement only the coupling requested by the current milestone.
- For rigid-body collisions, use actual occupied-cell geometry rather than treating concave fragment AABBs as solid. Document contact-normal orientation, impulse signs, units, work limits, and numerical failure behavior.
- Do not silently skip contacts or partially apply a failed physics tick. Follow the current milestone's explicit rollback/failure contract.
- Do not introduce body-body collisions, fragmentation, granular debris, inventory, crafting, or UI behavior into a milestone that explicitly excludes them.

## Correctness and tests

For each behavior change, add or update focused headless tests, including relevant edge cases:

- Deterministic replay and fixed-tick behavior.
- Boundaries, chunk seams, and coordinate conversions.
- Mass/material conservation and ownership transfer.
- Revision/incarnation invalidation and stale-data handling.
- Numerical finiteness, failure statuses, and bounded work.
- Collision geometry, contact direction, stability, and no unexpected energy growth when applicable.
- Pause, single-step, reset, and UI interactions when the milestone changes them.
- Compatibility with existing world, structural, extraction, and cellular tests.

Do not delete, disable, or weaken tests merely to obtain a green build. When changing a test because a documented contract changed, explain why. Avoid brittle exact floating-point assertions for iterative numerical solvers; use justified tolerances and invariants.

A green build does not by itself prove the requested feature works. Check the acceptance criteria and, when relevant, inspect representative runtime scenarios.

## Performance and diagnostics

- Measure before optimizing. Keep benchmarks reproducible and separate from correctness tests.
- Report world dimensions, scenario, JVM, relevant configuration, and measurement methodology when publishing performance results.
- Track simulation tick cost separately from FPS and rendering cost.
- Avoid allocations in hot per-cell or per-contact loops where practical; justify new allocations with measurements.
- Do not parallelize physics or cellular updates until ordering, determinism, ownership, and benchmarking implications are understood.
- Prefer bounded algorithms and explicit failure statuses over silently truncated results.
- Performance regressions must be disclosed, not hidden by switching test scenarios or omitting dense cases.

## Documentation

- Update relevant project and milestone documentation when behavior or architecture changes.
- Keep English primary documentation and corresponding `_ru.md` translation synchronized. Create a Russian translation for new primary milestone documentation.
- Record controls, units, invariants, limitations, benchmarks, and exact verification commands where relevant.
- Mark incomplete or experimental behavior honestly. Do not claim GUI testing, numerical correctness, or benchmark results without actually verifying them.
- Keep `AGENTS.md` itself in English as the authoritative repository-wide agent instructions.

## Scope and change control

- Implement only the active user-approved milestone or explicitly requested extension.
- Choose the simplest reversible interpretation for low-risk ambiguities; document the choice and proceed.
- Ask before changing core storage representation, major dependencies, save-format compatibility, architectural ownership, or other high-impact contracts not covered by the task.
- Do not use a technical difficulty as a reason to silently reduce scope. Investigate and iterate; if truly blocked, report the blocker.
- Preserve user changes and existing uncommitted work. Check `git status` before edits. Never discard, reset, overwrite, or reformat unrelated work without authorization.
- Do not run destructive commands, rewrite history, force-push, or remove user files without explicit approval.
- Do not commit generated build outputs, IDE metadata, credentials, secrets, or machine-specific settings.

## Git and milestone completion

- Create a commit only when requested by the user or explicitly required by the active milestone specification.
- Before committing, inspect `git status` and the diff. Stage only files belonging to the requested task.
- Do not commit an incomplete milestone under a message implying completion.
- If the user requests incremental commits, use the agreed milestone/submilestone boundary. Otherwise do not invent a new boundary merely because one internal component is finished.
- Do not push unless the user requested it or the active task explicitly includes pushing.
- Use the milestone-specific commit message when provided. Report the actual commit SHA, not an invented or anticipated one.
- Do not claim tests or benchmarks were executed unless they were.

## Final response contract

For a completed implementation, report concisely:

1. What was implemented and which milestone criteria are satisfied.
2. Tests/build/benchmarks actually run and their outcomes.
3. Any limitations or unverified GUI behavior.
4. Commit SHA and push status, if applicable.

For incomplete work, explicitly say **NOT COMPLETE**, explain the actual blocker, list preserved changes, and identify the precise next action. A progress summary is not a substitute for continuing an executable task.

**Remember: implement in small verified slices, but stop only at the requested task boundary or a genuine blocker.**

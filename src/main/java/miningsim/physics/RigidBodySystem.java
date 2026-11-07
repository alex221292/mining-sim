package miningsim.physics;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import miningsim.fragment.FragmentRegistry;

/** Headless fixed-step motion, optionally with discrete static collisions. */
public final class RigidBodySystem {
    public static final double TICK_SECONDS = 1.0 / 60.0;
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private final FragmentRegistry registry;
    private final int substeps;
    private final StaticContactSolver solver;
    private StaticContactSolver.Diagnostics diagnostics = new StaticContactSolver.Diagnostics(0, 0, 0, 0, 0, 0);
    private final TreeMap<Long, RigidBody> bodies = new TreeMap<>();

    public RigidBodySystem(FragmentRegistry registry) { this(registry, 4); }
    public RigidBodySystem(FragmentRegistry registry, int substeps) {
        this(registry, substeps, null);
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver) {
        this.registry = Objects.requireNonNull(registry);
        if (substeps < 1 || substeps > 1024) throw new IllegalArgumentException("Invalid substep count");
        this.substeps = substeps;
        this.solver = solver;
    }

    /** Call at the simulation boundary; IDs belong to this system's fixed registry. */
    public void synchronize() {
        var fragments = registry.snapshot();
        var ids = new java.util.HashSet<Long>();
        for (var fragment : fragments) {
            ids.add(fragment.id());
            bodies.computeIfAbsent(fragment.id(), id -> new RigidBody(fragment));
        }
        bodies.keySet().removeIf(id -> !ids.contains(id));
    }

    public List<RigidBody> bodies() { return List.copyOf(bodies.values()); }
    public RigidBody body(long id) { return bodies.get(id); }
    public StaticContactSolver.Diagnostics diagnostics() { return diagnostics; }

    /** Explicit tick, independent of wall time and rendering. */
    public void step() {
        var original = new TreeMap<>(bodies);
        var states = new TreeMap<Long, RigidBody.State>();
        for (var entry : bodies.entrySet()) states.put(entry.getKey(), entry.getValue().state());
        var oldDiagnostics = diagnostics;
        try {
            synchronize();
            if (solver != null) solver.resetDiagnostics();
            double dt = TICK_SECONDS / substeps, maxDisplacement = 0, maxAngle = 0;
            for (int i = 0; i < substeps; i++) for (var body : bodies.values()) {
                double x = body.xCells(), y = body.yCells(), angle = body.angleRadians();
                body.integrate(dt, GRAVITY_METRES_PER_SECOND_SQUARED);
                maxDisplacement = Math.max(maxDisplacement, Math.hypot(body.xCells() - x, body.yCells() - y));
                maxAngle = Math.max(maxAngle, Math.abs(body.angleRadians() - angle));
                if (solver != null) solver.solve(body);
            }
            if (solver != null) diagnostics = solver.diagnostics(maxDisplacement, maxAngle);
        } catch (RuntimeException ex) {
            bodies.clear(); bodies.putAll(original);
            for (var entry : states.entrySet()) bodies.get(entry.getKey()).restore(entry.getValue());
            diagnostics = oldDiagnostics;
            throw ex;
        }
    }
}

package miningsim.physics;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import miningsim.fragment.FragmentRegistry;

/** Headless fixed-step motion, optionally with discrete static collisions. */
public final class RigidBodySystem {
    public record MotionConfig(double maxTravelCells, int maxMicrostepsPerBody, int maxTotalMicrosteps) {
        public MotionConfig {
            if (!Double.isFinite(maxTravelCells) || maxTravelCells <= 0 || maxTravelCells > 0.25
                    || maxMicrostepsPerBody < 1 || maxTotalMicrosteps < 1)
                throw new IllegalArgumentException("Invalid bounded motion configuration");
        }
        public static MotionConfig defaults() { return new MotionConfig(0.25, 128, 4096); }
    }
    public static final class MotionBudgetException extends IllegalStateException {
        public MotionBudgetException(String message) { super(message); }
    }
    public record MotionDiagnostics(int baseSubsteps, int executedMicrosteps, int maxMicrostepsPerBody,
                                    double maxPredictedPointTravelCells, double maxCenterDisplacementCells,
                                    double maxAngularChangeRadians) { }
    public static final double TICK_SECONDS = 1.0 / 60.0;
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private final FragmentRegistry registry;
    private final int substeps;
    private final StaticContactSolver solver;
    private final MotionConfig motionConfig;
    private MotionDiagnostics motionDiagnostics = new MotionDiagnostics(0, 0, 0, 0, 0, 0);
    private String lastFailure;
    private StaticContactSolver.Diagnostics diagnostics = new StaticContactSolver.Diagnostics(0, 0, 0, 0, 0, 0);
    private final TreeMap<Long, RigidBody> bodies = new TreeMap<>();

    public RigidBodySystem(FragmentRegistry registry) { this(registry, 4); }
    public RigidBodySystem(FragmentRegistry registry, int substeps) {
        this(registry, substeps, null);
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver) {
        this(registry, substeps, solver, MotionConfig.defaults());
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig) {
        this.registry = Objects.requireNonNull(registry);
        if (substeps < 1 || substeps > 1024) throw new IllegalArgumentException("Invalid substep count");
        this.substeps = substeps;
        this.solver = solver;
        this.motionConfig = Objects.requireNonNull(motionConfig);
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
    public MotionDiagnostics motionDiagnostics() { return motionDiagnostics; }
    public String lastFailure() { return lastFailure; }

    /** Conservative for the semi-implicit position update, which uses the end-of-interval velocity. */
    private static double travelBound(RigidBody body, double dt) {
        double linear = (Math.hypot(body.velocityXMetresPerSecond(), body.velocityYMetresPerSecond())
                + GRAVITY_METRES_PER_SECOND_SQUARED * dt) * dt / FragmentGeometry.METRES_PER_CELL;
        double angular = body.geometry().maxRadiusCells() * Math.abs(body.angularVelocityRadiansPerSecond()) * dt;
        double bound = linear + angular;
        if (!Double.isFinite(bound) || bound < 0) throw new IllegalStateException("Non-finite motion bound for body " + body.id());
        return bound;
    }

    /** Explicit tick, independent of wall time and rendering. */
    public void step() {
        var original = new TreeMap<>(bodies);
        var states = new TreeMap<Long, RigidBody.State>();
        for (var entry : bodies.entrySet()) states.put(entry.getKey(), entry.getValue().state());
        var oldDiagnostics = diagnostics;
        try {
            synchronize();
            if (solver != null) solver.resetDiagnostics();
            double dt = TICK_SECONDS / substeps, maxDisplacement = 0, maxAngle = 0, maxPointTravel = 0;
            int executed = 0, maxPerBody = 0;
            var perBody = new TreeMap<Long, Integer>();
            for (int i = 0; i < substeps; i++) for (var body : bodies.values()) {
                double remaining = dt;
                do {
                    int used = perBody.getOrDefault(body.id(), 0);
                    double required = solver == null ? 1 : Math.max(1, Math.ceil(travelBound(body, remaining) / motionConfig.maxTravelCells()));
                    if (solver != null && (required > motionConfig.maxMicrostepsPerBody() - used
                            || required > motionConfig.maxTotalMicrosteps() - executed)) {
                        throw new MotionBudgetException("Body " + body.id() + " schedule requests " + required
                                + " scheduled microsteps for remaining substep; body remaining "
                                + (motionConfig.maxMicrostepsPerBody() - used) + ", total remaining " + (motionConfig.maxTotalMicrosteps() - executed));
                    }
                    double interval = remaining / required;
                    double predicted = solver == null ? 0 : travelBound(body, interval);
                    if (solver != null && predicted > motionConfig.maxTravelCells() * (1 + 1e-12))
                        throw new IllegalStateException("Motion bound exceeded for body " + body.id());
                    double x = body.xCells(), y = body.yCells(), angle = body.angleRadians();
                    body.integrate(interval, GRAVITY_METRES_PER_SECOND_SQUARED);
                    maxDisplacement = Math.max(maxDisplacement, Math.hypot(body.xCells() - x, body.yCells() - y));
                    maxAngle = Math.max(maxAngle, Math.abs(body.angleRadians() - angle));
                    if (solver != null) solver.solve(body);
                    maxPointTravel = Math.max(maxPointTravel, predicted);
                    executed++; used++; perBody.put(body.id(), used); maxPerBody = Math.max(maxPerBody, used);
                    remaining = required == 1 ? 0 : remaining - interval;
                } while (remaining > 0);
            }
            if (solver != null) diagnostics = solver.diagnostics(maxDisplacement, maxAngle);
            motionDiagnostics = new MotionDiagnostics(substeps, executed, maxPerBody, maxPointTravel, maxDisplacement, maxAngle);
            lastFailure = null;
        } catch (RuntimeException ex) {
            bodies.clear(); bodies.putAll(original);
            for (var entry : states.entrySet()) bodies.get(entry.getKey()).restore(entry.getValue());
            diagnostics = oldDiagnostics;
            lastFailure = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            throw ex;
        }
    }
}

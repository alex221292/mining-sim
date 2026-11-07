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
    public record InteractionConfig(long maxPairs, long maxChecks, long maxRawContacts, long maxReducedContacts,
                                    long maxWakeWork, int positionIterations) {
        public InteractionConfig {
            if (maxPairs < 1 || maxChecks < 1 || maxRawContacts < 1 || maxReducedContacts < 1 || maxWakeWork < 1
                    || positionIterations < 0 || positionIterations > 32) throw new IllegalArgumentException("Invalid interaction limits");
        }
        public static InteractionConfig defaults() { return new InteractionConfig(1_000_000, 10_000_000, 1_000_000, 1_000_000, 1_000_000, 3); }
    }
    public record SleepConfig(double linearSpeed, double angularSpeed, int ticks) {
        public SleepConfig {
            if (!Double.isFinite(linearSpeed) || linearSpeed <= 0 || !Double.isFinite(angularSpeed) || angularSpeed <= 0 || ticks < 1)
                throw new IllegalArgumentException("Invalid sleep configuration");
        }
        public static SleepConfig defaults() { return new SleepConfig(0.02, 0.02, 60); }
    }
    public record InteractionDiagnostics(int sharedMicrosteps, long pairs, long overlappingPairs, long checks,
                                         long rawContacts, long reducedContacts, long solverIterations,
                                         int sleepingBodies, int wokenBodies, double maxPenetrationCells) { }
    private static final class InteractionWork {
        long pairs, overlapping, checks, raw, reduced, iterations, wakeWork;
        int woken;
        double penetration;
    }
    public static final double TICK_SECONDS = 1.0 / 60.0;
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private final FragmentRegistry registry;
    private final int substeps;
    private final StaticContactSolver solver;
    private final BodyCollisionQuery bodyQuery;
    private final BodyContactSolver bodySolver;
    private final InteractionConfig interactionConfig;
    private final SleepConfig sleepConfig;
    private InteractionDiagnostics interactionDiagnostics = new InteractionDiagnostics(0,0,0,0,0,0,0,0,0,0);
    private InteractionWork interactionWork;
    private final MotionConfig motionConfig;
    private MotionDiagnostics motionDiagnostics = new MotionDiagnostics(0, 0, 0, 0, 0, 0);
    private String lastFailure;
    private long observedWorldRevision = -1;
    private java.util.Set<Long> lastSuccessfulBodyIds = java.util.Set.of();
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
        this(registry, substeps, solver, motionConfig, null, null);
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig,
                           BodyCollisionQuery bodyQuery, BodyContactSolver bodySolver) {
        this(registry, substeps, solver, motionConfig, bodyQuery, bodySolver, InteractionConfig.defaults());
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig,
                           BodyCollisionQuery bodyQuery, BodyContactSolver bodySolver, InteractionConfig interactionConfig) {
        this(registry, substeps, solver, motionConfig, bodyQuery, bodySolver, interactionConfig, SleepConfig.defaults());
    }
    public RigidBodySystem(FragmentRegistry registry, int substeps, StaticContactSolver solver, MotionConfig motionConfig,
                           BodyCollisionQuery bodyQuery, BodyContactSolver bodySolver, InteractionConfig interactionConfig, SleepConfig sleepConfig) {
        this.registry = Objects.requireNonNull(registry);
        if (substeps < 1 || substeps > 1024) throw new IllegalArgumentException("Invalid substep count");
        this.substeps = substeps;
        this.solver = solver;
        this.motionConfig = Objects.requireNonNull(motionConfig);
        if ((bodyQuery == null) != (bodySolver == null)) throw new IllegalArgumentException("Body query and solver must be configured together");
        this.bodyQuery = bodyQuery; this.bodySolver = bodySolver;
        this.interactionConfig = Objects.requireNonNull(interactionConfig);
        this.sleepConfig = Objects.requireNonNull(sleepConfig);
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
    public InteractionDiagnostics interactionDiagnostics() { return interactionDiagnostics; }

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
            if (bodyQuery != null) {
                interactionWork = new InteractionWork();
                if (!bodies.keySet().containsAll(lastSuccessfulBodyIds) || solver != null && observedWorldRevision >= 0 && observedWorldRevision != solver.worldRevision()) wakeAll();
                sharedStep();
                if (solver != null) observedWorldRevision = solver.worldRevision();
                lastSuccessfulBodyIds = java.util.Set.copyOf(bodies.keySet());
                lastFailure = null;
                return;
            }
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

    /** All bodies are integrated to the same time before pair detection. */
    private void sharedStep() {
        double dt = TICK_SECONDS / substeps, maxTravel = 0, maxDisplacement = 0, maxAngle = 0;
        int shared = 0, work = 0, maxPerBody = 0;
        var perBody = new TreeMap<Long, Integer>();
        for (int sub = 0; sub < substeps; sub++) {
            double remaining = dt;
            do {
                // Wake potentially intersecting sleep islands before advancing either participant.
                wakeNearby(remaining);
                double required = 1;
                int awake = 0;
                for (var body : bodies.values()) if (!body.sleeping()) { awake++; required = Math.max(required, Math.ceil(travelBound(body, remaining) / motionConfig.maxTravelCells())); }
                if (required > motionConfig.maxMicrostepsPerBody() - shared
                        || required * awake > motionConfig.maxTotalMicrosteps() - work)
                    throw new MotionBudgetException("Shared schedule requests " + required + " microsteps with " + bodies.size() + " bodies; remaining body="
                            + (motionConfig.maxMicrostepsPerBody() - shared) + ", total=" + (motionConfig.maxTotalMicrosteps() - work));
                double interval = remaining / required;
                for (var body : bodies.values()) if (!body.sleeping()) {
                    int count = perBody.merge(body.id(), 1, Integer::sum);
                    maxPerBody = Math.max(maxPerBody, count);
                    maxTravel = Math.max(maxTravel, travelBound(body, interval));
                    double x = body.xCells(), y = body.yCells(), angle = body.angleRadians();
                    body.integrate(interval, GRAVITY_METRES_PER_SECOND_SQUARED);
                    maxDisplacement = Math.max(maxDisplacement, Math.hypot(body.xCells() - x, body.yCells() - y));
                    maxAngle = Math.max(maxAngle, Math.abs(body.angleRadians() - angle));
                }
                var staticConstraints = new java.util.ArrayList<StaticContactSolver.Prepared>();
                if (solver != null) for (var body : bodies.values()) if (!body.sleeping()) staticConstraints.add(solver.prepare(body));
                var dynamicContacts = queryBodies();
                if (dynamicContacts.stream().anyMatch(c -> bodies.get(c.bodyA()).sleeping() != bodies.get(c.bodyB()).sleeping()))
                    throw new IllegalStateException("Sleeping body reached without pre-integration wake");
                var activeContacts = dynamicContacts.stream().filter(c -> !bodies.get(c.bodyA()).sleeping() || !bodies.get(c.bodyB()).sleeping()).toList();
                var dynamicConstraints = bodySolver.prepare(activeContacts, bodies);
                int iterations = Math.max(bodySolver.iterations(), solver == null ? 0 : solver.velocityIterations());
                for (int pass = 0; pass < iterations; pass++) {
                    if (solver != null && pass < solver.velocityIterations()) for (var constraint : staticConstraints) constraint.iterate();
                    if (pass < bodySolver.iterations()) dynamicConstraints.iterate();
                    if (pass < bodySolver.iterations() && !activeContacts.isEmpty()) interactionWork.iterations++;
                }
                for (int pass = 0; pass < interactionConfig.positionIterations(); pass++) {
                    if (solver != null && pass < solver.positionIterations()) for (var body : bodies.values()) if (!body.sleeping()) solver.correctPosition(body);
                    var contacts = queryBodies();
                    for (var contact : contacts) {
                        var a = bodies.get(contact.bodyA()); var b = bodies.get(contact.bodyB());
                        if (a.sleeping() && b.sleeping()) continue;
                        if (a.sleeping() || b.sleeping()) throw new IllegalStateException("Position correction reached a sleeping body after integration");
                        double invA = 1 / a.geometry().massKg(), invB = 1 / b.geometry().massKg();
                        double correction = Math.min(0.2, Math.max(0, contact.penetrationCells() - 0.01) * 0.2);
                        a.translate(-contact.normalX() * correction * invA / (invA + invB), -contact.normalY() * correction * invA / (invA + invB));
                        b.translate(contact.normalX() * correction * invB / (invA + invB), contact.normalY() * correction * invB / (invA + invB));
                    }
                }
                shared++; work += awake; remaining = required == 1 ? 0 : remaining - interval;
            } while (remaining > 0);
        }
        var supported = new java.util.HashSet<Long>();
        if (solver != null) for (var body : bodies.values()) if (solver.supported(body)) supported.add(body.id());
        var contacts = queryBodies();
        for (int pass = 0; pass < bodies.size(); pass++) for (var c : contacts) {
            if (++interactionWork.wakeWork > interactionConfig.maxWakeWork()) throw new MotionBudgetException("Support propagation limit exceeded: " + interactionWork.wakeWork);
            if (c.normalY() > 0.5 && supported.contains(c.bodyB())) supported.add(c.bodyA());
            if (c.normalY() < -0.5 && supported.contains(c.bodyA())) supported.add(c.bodyB());
        }
        for (var body : bodies.values()) body.updateSleep(supported.contains(body.id()), sleepConfig);
        int sleeping = 0; for (var body : bodies.values()) if (body.sleeping()) sleeping++;
        interactionDiagnostics = new InteractionDiagnostics(shared,interactionWork.pairs,interactionWork.overlapping,interactionWork.checks,
                interactionWork.raw,interactionWork.reduced,interactionWork.iterations,sleeping,interactionWork.woken,interactionWork.penetration);
        if (solver != null) diagnostics = solver.diagnostics(maxDisplacement, maxAngle);
        motionDiagnostics = new MotionDiagnostics(substeps, work, maxPerBody, maxTravel, maxDisplacement, maxAngle);
    }
    private void wakeAll() {
        for (var body : bodies.values()) {
            if (++interactionWork.wakeWork > interactionConfig.maxWakeWork()) throw new MotionBudgetException("Wake limit exceeded: " + interactionWork.wakeWork);
            if (body.sleeping()) { body.wake(); interactionWork.woken++; }
        }
    }
    private void wakeNearby(double remaining) {
        var queue = new java.util.ArrayDeque<RigidBody>();
        for (var body : bodies.values()) if (!body.sleeping()) queue.add(body);
        while (!queue.isEmpty()) {
            var source = queue.removeFirst();
            var a = source.geometry().bounds(source.xCells(),source.yCells(),source.angleRadians());
            double margin = travelBound(source, remaining) + 0.1;
            for (var other : bodies.values()) {
                if (++interactionWork.wakeWork > interactionConfig.maxWakeWork()) throw new MotionBudgetException("Wake propagation limit exceeded: " + interactionWork.wakeWork);
                if (!other.sleeping()) continue;
                var b = other.geometry().bounds(other.xCells(),other.yCells(),other.angleRadians());
                if (a.minX() - margin <= b.maxX() && a.maxX() + margin >= b.minX()
                        && a.minY() - margin <= b.maxY() && a.maxY() + margin >= b.minY()) {
                    other.wake(); interactionWork.woken++; queue.addLast(other);
                }
            }
        }
    }
    private List<BodyCollisionQuery.Contact> queryBodies() {
        var r = bodyQuery.query(bodies());
        interactionWork.pairs += r.candidatePairs(); interactionWork.overlapping += r.overlappingPairs();
        interactionWork.checks += r.narrowChecks(); interactionWork.raw += r.rawContacts(); interactionWork.reduced += r.contacts().size();
        if (interactionWork.pairs > interactionConfig.maxPairs() || interactionWork.checks > interactionConfig.maxChecks()
                || interactionWork.raw > interactionConfig.maxRawContacts() || interactionWork.reduced > interactionConfig.maxReducedContacts())
            throw new MotionBudgetException("Body contact tick budget exceeded: pairs=" + interactionWork.pairs + ", checks=" + interactionWork.checks
                    + ", raw=" + interactionWork.raw + ", reduced=" + interactionWork.reduced);
        for (var contact : r.contacts()) interactionWork.penetration = Math.max(interactionWork.penetration, contact.penetrationCells());
        return r.contacts();
    }
}

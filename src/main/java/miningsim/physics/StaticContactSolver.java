package miningsim.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Fixed-iteration sequential impulses against infinite-mass static geometry. */
public final class StaticContactSolver {
    public record Config(double restitution, double staticFriction, double dynamicFriction,
                         int velocityIterations, int positionIterations, double slopCells,
                         double correctionFactor, double restitutionThreshold) {
        public Config {
            if (!Double.isFinite(restitution) || restitution < 0 || restitution > 1
                    || !Double.isFinite(staticFriction) || staticFriction < 0
                    || !Double.isFinite(dynamicFriction) || dynamicFriction < 0 || dynamicFriction > staticFriction
                    || velocityIterations < 1 || velocityIterations > 128 || positionIterations < 0 || positionIterations > 128
                    || !Double.isFinite(slopCells) || slopCells < 0 || !Double.isFinite(correctionFactor)
                    || correctionFactor < 0 || correctionFactor > 1 || !Double.isFinite(restitutionThreshold) || restitutionThreshold < 0)
                throw new IllegalArgumentException("Invalid solver configuration");
        }
        public static Config defaults() { return new Config(0.05, 0.7, 0.5, 8, 3, 0.01, 0.2, 0.5); }
    }
    public record Diagnostics(long candidates, long checks, long contacts, long velocityIterations,
                              double maxDisplacementCells, double maxAngleRadians) { }
    private static final class Constraint {
        final double nx, ny, rx, ry, normalMass, tangentMass, target;
        double normalImpulse, tangentImpulse;
        Constraint(RigidBody b, StaticCollisionQuery.Contact c, Config config) {
            nx = c.normalX(); ny = c.normalY();
            if (!Double.isFinite(nx) || !Double.isFinite(ny) || Math.abs(nx * nx + ny * ny - 1) > 1e-6
                    || !Double.isFinite(c.pointX()) || !Double.isFinite(c.pointY())
                    || !Double.isFinite(c.penetrationCells()) || c.penetrationCells() < 0)
                throw new IllegalStateException("Invalid contact geometry");
            rx = (c.pointX() - b.xCells()) * FragmentGeometry.METRES_PER_CELL;
            ry = (c.pointY() - b.yCells()) * FragmentGeometry.METRES_PER_CELL;
            double invMass = 1 / b.geometry().massKg(), invInertia = 1 / b.geometry().inertiaKgMetresSquared();
            double crossN = rx * ny - ry * nx, crossT = rx * nx + ry * ny;
            normalMass = invMass + crossN * crossN * invInertia;
            tangentMass = invMass + crossT * crossT * invInertia;
            if (!Double.isFinite(normalMass) || normalMass <= 0 || !Double.isFinite(tangentMass) || tangentMass <= 0)
                throw new IllegalStateException("Invalid contact effective mass");
            double vn = normalVelocity(b);
            target = vn > config.restitutionThreshold() ? -config.restitution() * vn : 0;
        }
        double normalVelocity(RigidBody b) {
            return (b.velocityXMetresPerSecond() - b.angularVelocityRadiansPerSecond() * ry) * nx
                    + (b.velocityYMetresPerSecond() + b.angularVelocityRadiansPerSecond() * rx) * ny;
        }
        void solve(RigidBody b, Config config) {
            double old = normalImpulse;
            normalImpulse = Math.max(0, old + (normalVelocity(b) - target) / normalMass);
            double delta = normalImpulse - old;
            b.applyImpulse(-delta * nx, -delta * ny, rx, ry);
            double tx = -ny, ty = nx;
            double vt = (b.velocityXMetresPerSecond() - b.angularVelocityRadiansPerSecond() * ry) * tx
                    + (b.velocityYMetresPerSecond() + b.angularVelocityRadiansPerSecond() * rx) * ty;
            double desired = tangentImpulse - vt / tangentMass;
            double limit = config.staticFriction() * normalImpulse;
            if (Math.abs(desired) > limit) desired = Math.copySign(config.dynamicFriction() * normalImpulse, desired);
            double change = desired - tangentImpulse; tangentImpulse = desired;
            b.applyImpulse(change * tx, change * ty, rx, ry);
        }
    }
    private final StaticCollisionQuery query;
    private final Config config;
    private long candidates, checks, contacts, iterations;
    record CounterState(long candidates, long checks, long contacts, long iterations) { }
    public StaticContactSolver(StaticCollisionQuery query, Config config) {
        this.query = Objects.requireNonNull(query); this.config = Objects.requireNonNull(config);
    }
    void resetDiagnostics() { candidates = checks = contacts = iterations = 0; }
    CounterState counterState() { return new CounterState(candidates, checks, contacts, iterations); }
    void restoreCounters(CounterState state) {
        candidates = state.candidates(); checks = state.checks(); contacts = state.contacts(); iterations = state.iterations();
    }
    Diagnostics diagnostics(double maxDisplacement, double maxAngle) { return new Diagnostics(candidates, checks, contacts, iterations, maxDisplacement, maxAngle); }
    private List<StaticCollisionQuery.Contact> query(RigidBody body) {
        var result = query.query(body);
        candidates = Math.addExact(candidates, result.candidates()); checks = Math.addExact(checks, result.narrowPhaseChecks());
        contacts = Math.addExact(contacts, result.contacts().size());
        return result.contacts();
    }
    final class Prepared {
        private final RigidBody body;
        private final List<Constraint> constraints = new ArrayList<>();
        private Prepared(RigidBody body) {
            this.body = body;
            for (var c : query(body)) constraints.add(new Constraint(body, c, config));
        }
        void iterate() {
            for (var constraint : constraints) constraint.solve(body, config);
            if (!constraints.isEmpty()) iterations = Math.addExact(iterations, 1);
        }
    }
    Prepared prepare(RigidBody body) { return new Prepared(body); }
    int velocityIterations() { return config.velocityIterations(); }
    int positionIterations() { return config.positionIterations(); }
    long worldRevision() { return query.worldRevision(); }
    boolean coupledTo(miningsim.world.World world) { return query.coupledTo(world); }
    boolean supported(RigidBody body) {
        for (var contact : query(body)) if (contact.normalY() > 0.5) return true;
        return false;
    }
    void correctPosition(RigidBody body) {
        var current = query(body);
        StaticCollisionQuery.Contact deepest = null;
        for (var c : current) if (deepest == null || c.penetrationCells() > deepest.penetrationCells()) deepest = c;
        if (deepest == null || deepest.penetrationCells() <= config.slopCells()) return;
        double correction = Math.min(0.2, config.correctionFactor() * (deepest.penetrationCells() - config.slopCells()));
        body.translate(-deepest.normalX() * correction, -deepest.normalY() * correction);
    }
    void solve(RigidBody body) {
        var constraints = new ArrayList<Constraint>();
        for (var c : query(body)) constraints.add(new Constraint(body, c, config));
        for (int i = 0; i < config.velocityIterations(); i++) {
            for (var constraint : constraints) constraint.solve(body, config);
            if (!constraints.isEmpty()) iterations = Math.addExact(iterations, 1);
        }
        for (int i = 0; i < config.positionIterations(); i++) {
            var current = query(body);
            StaticCollisionQuery.Contact deepest = null;
            for (var c : current) if (deepest == null || c.penetrationCells() > deepest.penetrationCells()) deepest = c;
            if (deepest == null || deepest.penetrationCells() <= config.slopCells()) break;
            double correction = Math.min(0.2, config.correctionFactor() * (deepest.penetrationCells() - config.slopCells()));
            body.translate(-deepest.normalX() * correction, -deepest.normalY() * correction);
        }
    }
}

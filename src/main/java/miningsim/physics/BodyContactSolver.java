package miningsim.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic sequential impulses for finite-mass body pairs. */
public final class BodyContactSolver {
    public record Config(double restitution, double staticFriction, double dynamicFriction, int iterations) {
        public Config {
            if (!Double.isFinite(restitution) || restitution < 0 || restitution > 1 || !Double.isFinite(staticFriction)
                    || !Double.isFinite(dynamicFriction) || dynamicFriction < 0 || staticFriction < dynamicFriction
                    || iterations < 1 || iterations > 128) throw new IllegalArgumentException("Invalid body solver configuration");
        }
        public static Config defaults() { return new Config(0.05, 0.7, 0.5, 8); }
    }
    private static final class Constraint {
        final RigidBody a, b;
        final double nx, ny, ax, ay, bx, by, normalMass, tangentMass, target;
        double normal, tangent;
        Constraint(RigidBody a, RigidBody b, BodyCollisionQuery.Contact c, Config config) {
            this.a = a; this.b = b; nx = c.normalX(); ny = c.normalY();
            ax = (c.pointX() - a.xCells()) * 0.1; ay = (c.pointY() - a.yCells()) * 0.1;
            bx = (c.pointX() - b.xCells()) * 0.1; by = (c.pointY() - b.yCells()) * 0.1;
            if (!Double.isFinite(nx) || !Double.isFinite(ny) || Math.abs(nx * nx + ny * ny - 1) > 1e-6)
                throw new IllegalStateException("Invalid body contact normal");
            normalMass = effective(nx,ny); tangentMass = effective(-ny,nx);
            if (!Double.isFinite(normalMass) || normalMass <= 0 || !Double.isFinite(tangentMass) || tangentMass <= 0)
                throw new IllegalStateException("Invalid body effective mass");
            double speed = relative(nx, ny);
            target = speed < -0.5 ? -config.restitution() * speed : 0;
        }
        double effective(double x, double y) {
            double ca=ax*y-ay*x, cb=bx*y-by*x;
            return 1/a.geometry().massKg()+1/b.geometry().massKg()+ca*ca/a.geometry().inertiaKgMetresSquared()+cb*cb/b.geometry().inertiaKgMetresSquared();
        }
        double relative(double x, double y) {
            double vx=b.velocityXMetresPerSecond()-b.angularVelocityRadiansPerSecond()*by-a.velocityXMetresPerSecond()+a.angularVelocityRadiansPerSecond()*ay;
            double vy=b.velocityYMetresPerSecond()+b.angularVelocityRadiansPerSecond()*bx-a.velocityYMetresPerSecond()-a.angularVelocityRadiansPerSecond()*ax;
            return vx*x+vy*y;
        }
        void impulse(double x, double y) { a.applyImpulse(-x,-y,ax,ay); b.applyImpulse(x,y,bx,by); }
        void solve(Config config) {
            double next=Math.max(0,normal+(target-relative(nx,ny))/normalMass), change=next-normal; normal=next;
            impulse(change*nx,change*ny);
            double tx=-ny,ty=nx, desired=tangent-relative(tx,ty)/tangentMass;
            if (Math.abs(desired)>config.staticFriction()*normal) desired=Math.copySign(config.dynamicFriction()*normal,desired);
            change=desired-tangent; tangent=desired; impulse(change*tx,change*ty);
        }
    }
    private final Config config;
    public BodyContactSolver(Config config) { this.config=Objects.requireNonNull(config); }
    final class Prepared {
        private final List<Constraint> constraints = new ArrayList<>();
        private Prepared(List<BodyCollisionQuery.Contact> contacts, Map<Long, RigidBody> bodies) {
            for (var c:contacts) constraints.add(new Constraint(Objects.requireNonNull(bodies.get(c.bodyA())),Objects.requireNonNull(bodies.get(c.bodyB())),c,config));
        }
        void iterate() { for (var constraint:constraints) constraint.solve(config); }
    }
    Prepared prepare(List<BodyCollisionQuery.Contact> contacts, Map<Long, RigidBody> bodies) { return new Prepared(contacts, bodies); }
    int iterations() { return config.iterations(); }
    public void solve(List<BodyCollisionQuery.Contact> contacts, Map<Long, RigidBody> bodies) {
        var constraints=new ArrayList<Constraint>();
        for (var c:contacts) constraints.add(new Constraint(Objects.requireNonNull(bodies.get(c.bodyA())),Objects.requireNonNull(bodies.get(c.bodyB())),c,config));
        for (int i=0;i<config.iterations();i++) for (var constraint:constraints) constraint.solve(config);
    }
}

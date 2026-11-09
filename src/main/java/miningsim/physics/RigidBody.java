package miningsim.physics;

import java.util.Objects;
import miningsim.fragment.RockFragment;

/** Motion only; the registry remains the owner of the fragment and its material. */
public final class RigidBody {
    record State(double x, double y, double angle, double vx, double vy, double omega, boolean sleeping, int sleepTicks) { }
    private final RockFragment fragment;
    private final FragmentGeometry geometry;
    private double x, y, angle, velocityX, velocityY, angularVelocity;
    private boolean sleeping;
    private int sleepTicks;

    RigidBody(RockFragment fragment) {
        this.fragment = Objects.requireNonNull(fragment);
        geometry = new FragmentGeometry(fragment);
        var center = geometry.initialCenter();
        x = center.x(); y = center.y();
    }

    public long id() { return fragment.id(); }
    public RockFragment fragment() { return fragment; }
    public FragmentGeometry geometry() { return geometry; }
    public double xCells() { return x; }
    public double yCells() { return y; }
    public double angleRadians() { return angle; }
    public double velocityXMetresPerSecond() { return velocityX; }
    public double velocityYMetresPerSecond() { return velocityY; }
    public double angularVelocityRadiansPerSecond() { return angularVelocity; }
    public boolean sleeping() { return sleeping; }
    public int sleepTicks() { return sleepTicks; }

    State state() { return new State(x, y, angle, velocityX, velocityY, angularVelocity, sleeping, sleepTicks); }
    void restore(State state) {
        x = state.x(); y = state.y(); angle = state.angle();
        velocityX = state.vx(); velocityY = state.vy(); angularVelocity = state.omega();
        sleeping = state.sleeping(); sleepTicks = state.sleepTicks();
    }
    void wake() { sleeping = false; sleepTicks = 0; }
    boolean sleepEligible(boolean supported,RigidBodySystem.SleepConfig config) {
        return supported && Math.hypot(velocityX,velocityY)<config.linearSpeed() && Math.abs(angularVelocity)<config.angularSpeed();
    }
    void updateIslandSleep(boolean eligible,int minimumTicks,RigidBodySystem.SleepConfig config) {
        if(sleeping) return;
        if(!eligible) { sleepTicks=0; return; }
        sleepTicks=minimumTicks+1;
        if(sleepTicks>=config.ticks()) { sleeping=true; velocityX=velocityY=angularVelocity=0; }
    }
    void translate(double dx, double dy) {
        double nx = x + dx, ny = y + dy;
        if (!Double.isFinite(nx) || !Double.isFinite(ny)) throw new IllegalStateException("Non-finite position");
        x = nx; y = ny;
        if (sleeping && (dx != 0 || dy != 0)) wake();
    }
    void applyImpulse(double ix, double iy, double rx, double ry) {
        double inverseMass = 1 / geometry.massKg(), inverseInertia = 1 / geometry.inertiaKgMetresSquared();
        double vx = velocityX + ix * inverseMass, vy = velocityY + iy * inverseMass;
        double omega = angularVelocity + (rx * iy - ry * ix) * inverseInertia;
        if (!Double.isFinite(vx) || !Double.isFinite(vy) || !Double.isFinite(omega))
            throw new IllegalStateException("Non-finite impulse result");
        velocityX = vx; velocityY = vy; angularVelocity = omega;
        if (sleeping && (ix != 0 || iy != 0)) wake();
    }

    public void setVelocity(double xMetresPerSecond, double yMetresPerSecond, double radiansPerSecond) {
        if (!Double.isFinite(xMetresPerSecond) || !Double.isFinite(yMetresPerSecond) || !Double.isFinite(radiansPerSecond))
            throw new IllegalArgumentException("Velocity must be finite");
        velocityX = xMetresPerSecond; velocityY = yMetresPerSecond; angularVelocity = radiansPerSecond;
        if (xMetresPerSecond != 0 || yMetresPerSecond != 0 || radiansPerSecond != 0) wake();
    }

    void integrate(double dt, double gravity) {
        double nextVelocityY = velocityY + gravity * dt;
        double nextX = x + velocityX * dt / FragmentGeometry.METRES_PER_CELL;
        double nextY = y + nextVelocityY * dt / FragmentGeometry.METRES_PER_CELL;
        double nextAngle = angle + angularVelocity * dt;
        if (!Double.isFinite(nextVelocityY) || !Double.isFinite(nextX) || !Double.isFinite(nextY) || !Double.isFinite(nextAngle))
            throw new IllegalStateException("Rigid-body integration exceeded finite range");
        velocityY = nextVelocityY; x = nextX; y = nextY; angle = nextAngle;
    }
}

package miningsim.physics;

import java.util.Objects;
import miningsim.fragment.RockFragment;

/** Motion only; the registry remains the owner of the fragment and its material. */
public final class RigidBody {
    private final RockFragment fragment;
    private final FragmentGeometry geometry;
    private double x, y, angle, velocityX, velocityY, angularVelocity;

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

    public void setVelocity(double xMetresPerSecond, double yMetresPerSecond, double radiansPerSecond) {
        if (!Double.isFinite(xMetresPerSecond) || !Double.isFinite(yMetresPerSecond) || !Double.isFinite(radiansPerSecond))
            throw new IllegalArgumentException("Velocity must be finite");
        velocityX = xMetresPerSecond; velocityY = yMetresPerSecond; angularVelocity = radiansPerSecond;
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

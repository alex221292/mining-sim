package miningsim.physics;

/** SAT for two unit squares in cell coordinates, with normals from A toward B. */
public final class SquareCollision {
    public record Square(double x, double y, double angle) {
        public Square {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(angle))
                throw new IllegalArgumentException("Non-finite square");
        }
    }
    public record Contact(double normalX, double normalY, double penetrationCells) { }
    private SquareCollision() { }

    /** Exact touching is not penetrating and returns null. */
    public static Contact intersect(Square a, Square b) {
        double ac = Math.cos(a.angle()), as = Math.sin(a.angle());
        double bc = Math.cos(b.angle()), bs = Math.sin(b.angle());
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        double[] axes = {ac, as, -as, ac, bc, bs, -bs, bc};
        double best = Double.POSITIVE_INFINITY, nx = 0, ny = 0;
        for (int i = 0; i < axes.length; i += 2) {
            double x = axes[i], y = axes[i + 1];
            double radiusA = 0.5 * (Math.abs(x * ac + y * as) + Math.abs(-x * as + y * ac));
            double radiusB = 0.5 * (Math.abs(x * bc + y * bs) + Math.abs(-x * bs + y * bc));
            double separation = dx * x + dy * y;
            double overlap = radiusA + radiusB - Math.abs(separation);
            if (overlap <= 0) return null;
            if (overlap < best) {
                best = overlap;
                double sign = separation < 0 ? -1 : 1;
                nx = x * sign; ny = y * sign;
            }
        }
        return new Contact(nx, ny, best);
    }
}

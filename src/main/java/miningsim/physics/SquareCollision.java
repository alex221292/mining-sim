package miningsim.physics;

import java.util.ArrayList;
import java.util.List;

/** SAT for two unit squares in cell coordinates, with normals from A toward B. */
public final class SquareCollision {
    public record Square(double x, double y, double angle) {
        public Square {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(angle))
                throw new IllegalArgumentException("Non-finite square");
        }
    }
    public record Contact(double normalX, double normalY, double penetrationCells, double pointX, double pointY) { }
    private record Point(double x, double y) { }
    private SquareCollision() { }

    /** Exact touching is not penetrating and returns null. */
    public static Contact intersect(Square a, Square b) {
        double ac = Math.cos(a.angle()), as = Math.sin(a.angle());
        double bc = Math.cos(b.angle()), bs = Math.sin(b.angle());
        double dx = b.x() - a.x(), dy = b.y() - a.y();
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) throw new IllegalArgumentException("Square separation overflow");
        double[] axes = {ac, as, -as, ac, bc, bs, -bs, bc};
        double best = Double.POSITIVE_INFINITY, nx = 0, ny = 0;
        for (int i = 0; i < axes.length; i += 2) {
            double x = axes[i], y = axes[i + 1];
            double radiusA = 0.5 * (Math.abs(x * ac + y * as) + Math.abs(-x * as + y * ac));
            double radiusB = 0.5 * (Math.abs(x * bc + y * bs) + Math.abs(-x * bs + y * bc));
            double separation = dx * x + dy * y;
            if (!Double.isFinite(separation)) throw new IllegalArgumentException("Square projection overflow");
            double overlap = radiusA + radiusB - Math.abs(separation);
            if (overlap <= 0) return null;
            if (overlap < best) {
                best = overlap;
                double sign = separation < 0 ? -1 : 1;
                nx = x * sign; ny = y * sign;
            }
        }
        // The average of the intersection polygon vertices lies inside both squares.
        // This is a representative point in the overlap, not a time-of-impact point.
        List<Point> polygon = vertices(a);
        List<Point> clip = vertices(b);
        for (int i = 0; i < 4 && !polygon.isEmpty(); i++) {
            Point p = clip.get(i), q = clip.get((i + 1) % 4);
            polygon = clip(polygon, p, q);
        }
        if (polygon.isEmpty()) throw new IllegalStateException("SAT overlap produced an empty intersection");
        double x = 0, y = 0;
        for (Point p : polygon) { x += p.x(); y += p.y(); }
        x /= polygon.size(); y /= polygon.size();
        if (!Double.isFinite(nx) || !Double.isFinite(ny) || !Double.isFinite(best)
                || !Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalStateException("Non-finite contact");
        return new Contact(nx, ny, best, x, y);
    }

    private static List<Point> vertices(Square square) {
        double c = Math.cos(square.angle()), s = Math.sin(square.angle());
        var points = new ArrayList<Point>(4);
        for (int[] corner : new int[][]{{-1,-1},{1,-1},{1,1},{-1,1}}) {
            double x = corner[0] * 0.5, y = corner[1] * 0.5;
            points.add(new Point(square.x() + c * x - s * y, square.y() + s * x + c * y));
        }
        return points;
    }

    private static double side(Point a, Point b, Point p) {
        return (b.x() - a.x()) * (p.y() - a.y()) - (b.y() - a.y()) * (p.x() - a.x());
    }

    private static List<Point> clip(List<Point> input, Point a, Point b) {
        var output = new ArrayList<Point>();
        Point previous = input.getLast(); double prevSide = side(a, b, previous);
        for (Point current : input) {
            double currentSide = side(a, b, current);
            if ((prevSide >= 0) != (currentSide >= 0)) {
                double t = prevSide / (prevSide - currentSide);
                output.add(new Point(previous.x() + t * (current.x() - previous.x()),
                        previous.y() + t * (current.y() - previous.y())));
            }
            if (currentSide >= 0) output.add(current);
            previous = current; prevSide = currentSide;
        }
        return output;
    }
}

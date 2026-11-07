package miningsim.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.world.Material;
import miningsim.world.World;

/** Read-only discrete overlap query. This is not a sweep or a collision response. */
public final class StaticCollisionQuery {
    public enum Boundary { NONE, LEFT, RIGHT, TOP, BOTTOM }
    public record Contact(long bodyId, Cell fragmentCell, int worldX, int worldY,
                          double normalX, double normalY, double penetrationCells,
                          double pointX, double pointY, Boundary boundary) { }
    public record Result(List<Contact> contacts, long candidates, long narrowPhaseChecks) {
        public Result { contacts = List.copyOf(contacts); }
    }
    private final World world;
    private final long maxCandidates, maxChecks;

    public StaticCollisionQuery(World world, long maxCandidates, long maxChecks) {
        this.world = Objects.requireNonNull(world);
        if (maxCandidates < 1 || maxChecks < 1) throw new IllegalArgumentException("Work limits must be positive");
        this.maxCandidates = maxCandidates; this.maxChecks = maxChecks;
    }
    long worldRevision() { return world.mutationRevision(); }

    public static boolean solid(Material material) {
        return switch (Objects.requireNonNull(material)) {
            case STONE, BEDROCK, WOOD, SAND, SOIL, GRAVEL -> true;
            case AIR, WATER -> false;
        };
    }

    /** Row-major world candidates followed by the descriptor's deterministic cell order. */
    public Result query(RigidBody body) {
        Objects.requireNonNull(body);
        var bounds = body.geometry().bounds(body.xCells(), body.yCells(), body.angleRadians());
        int left = clampFloor(bounds.minX(), world.width());
        int right = clampCeil(bounds.maxX(), world.width());
        int top = clampFloor(bounds.minY(), world.height());
        int bottom = clampCeil(bounds.maxY(), world.height());
        long candidates = (long) (right - left) * (bottom - top);
        if (candidates > maxCandidates) throw new IllegalStateException("Static collision candidate limit exceeded");
        long checks = 0;
        var contacts = new ArrayList<Contact>();
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            if (!solid(world.get(x, y))) continue;
            var fixed = new SquareCollision.Square(x + 0.5, y + 0.5, 0);
            for (var cell : body.fragment().descriptor().cells()) {
                if (++checks > maxChecks) throw new IllegalStateException("Static collision narrow-phase limit exceeded");
                var center = body.geometry().transform(cell.x() + 0.5, cell.y() + 0.5,
                        body.xCells(), body.yCells(), body.angleRadians());
                var hit = SquareCollision.intersect(new SquareCollision.Square(center.x(), center.y(), body.angleRadians()), fixed);
                if (hit != null) {
                    double nx = hit.normalX(), ny = hit.normalY(), depth = hit.penetrationCells();
                    // A shared tile edge inside a horizontal solid run is not an exposed wall.
                    // Use the outer top face when the body approaches that face from above.
                    boolean leftSolid = x > 0 && solid(world.get(x - 1, y));
                    boolean rightSolid = x + 1 < world.width() && solid(world.get(x + 1, y));
                    boolean run = leftSolid || rightSolid;
                    boolean exposedTop = y == 0 || !solid(world.get(x, y - 1));
                    boolean internalSide = nx > 0 && leftSolid || nx < 0 && rightSolid;
                    if (run && exposedTop && center.y() < y && (ny > 0.5 || internalSide)) {
                        nx = 0; ny = 1;
                        depth = center.y() + 0.5 * (Math.abs(Math.cos(body.angleRadians())) + Math.abs(Math.sin(body.angleRadians()))) - y;
                    }
                    contacts.add(new Contact(body.id(), cell, x, y, nx, ny, depth, hit.pointX(), hit.pointY(), Boundary.NONE));
                }
                if (contacts.size() > 1000) throw new IllegalStateException("Static contact reduction limit exceeded");
            }
        }
        for (var cell : body.fragment().descriptor().cells()) {
            for (int dy = 0; dy <= 1; dy++) for (int dx = 0; dx <= 1; dx++) {
                if (++checks > maxChecks) throw new IllegalStateException("Static collision narrow-phase limit exceeded");
                var p = body.geometry().transform(cell.x() + (double) dx, cell.y() + (double) dy,
                        body.xCells(), body.yCells(), body.angleRadians());
                addBoundary(contacts, body.id(), cell, Boundary.LEFT, -1, 0, -p.x(), 0, p.y());
                addBoundary(contacts, body.id(), cell, Boundary.RIGHT, 1, 0, p.x() - world.width(), world.width(), p.y());
                addBoundary(contacts, body.id(), cell, Boundary.TOP, 0, -1, -p.y(), p.x(), 0);
                boolean bedrockContact = false;
                for (var c : contacts) if (c.boundary() == Boundary.NONE && c.fragmentCell().equals(cell)
                        && c.worldY() == world.height() - 1
                        && world.get(c.worldX(), c.worldY()) == Material.BEDROCK
                        && p.x() >= c.worldX() && p.x() <= c.worldX() + 1.0) { bedrockContact = true; break; }
                if (!bedrockContact) addBoundary(contacts, body.id(), cell, Boundary.BOTTOM, 0, 1,
                        p.y() - world.height(), p.x(), world.height());
                if (contacts.size() > 1000) throw new IllegalStateException("Static contact reduction limit exceeded");
            }
        }
        return new Result(reduce(contacts), candidates, checks);
    }

    private static void addBoundary(List<Contact> contacts, long id, Cell cell, Boundary boundary,
                                    double nx, double ny, double depth, double x, double y) {
        if (!Double.isFinite(depth) || !Double.isFinite(x) || !Double.isFinite(y))
            throw new IllegalStateException("Non-finite boundary contact");
        if (depth > 0) contacts.add(new Contact(id, cell, -1, -1, nx, ny, depth, x, y, boundary));
    }

    /** Bounded deterministic connected clusters of coplanar surfaces and matching normals. */
    private static List<Contact> reduce(List<Contact> raw) {
        int n = raw.size();
        if ((long) n * n > 1_000_000) throw new IllegalStateException("Static contact reduction limit exceeded");
        int[] parent = new int[n]; for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) for (int j = 0; j < i; j++) {
            Contact a = raw.get(i), b = raw.get(j);
            if (sameSurface(a, b)) {
                int ra = root(parent, i), rb = root(parent, j);
                if (ra != rb) parent[ra] = rb;
            }
        }
        var result = new ArrayList<Contact>();
        for (int i = 0; i < n; i++) if (root(parent, i) == i) {
            Contact min = null, max = null; double low = Double.POSITIVE_INFINITY, high = Double.NEGATIVE_INFINITY;
            for (int j = 0; j < n; j++) if (root(parent, j) == i) {
                Contact c = raw.get(j); double tangent = -c.normalY() * c.pointX() + c.normalX() * c.pointY();
                if (tangent < low) { low = tangent; min = c; }
                if (tangent > high) { high = tangent; max = c; }
            }
            result.add(min);
            if (high - low > 1e-9) result.add(max);
        }
        return result;
    }
    private static int root(int[] parents, int i) {
        while (parents[i] != i) i = parents[i];
        return i;
    }
    private static boolean sameSurface(Contact a, Contact b) {
        if (a.boundary() != b.boundary() || a.normalX() * b.normalX() + a.normalY() * b.normalY() < 0.999999) return false;
        if (a.boundary() != Boundary.NONE) {
            return Math.abs(a.fragmentCell().x() - b.fragmentCell().x()) + Math.abs(a.fragmentCell().y() - b.fragmentCell().y()) <= 1;
        }
        int dx = a.worldX() - b.worldX(), dy = a.worldY() - b.worldY();
        return Math.abs(dx) + Math.abs(dy) <= 1 && Math.abs(dx * a.normalX() + dy * a.normalY()) < 1e-6;
    }

    private static int clampFloor(double value, int limit) {
        if (!Double.isFinite(value)) throw new IllegalStateException("Non-finite bounds");
        return (int) Math.max(0, Math.min(limit, Math.floor(value)));
    }
    private static int clampCeil(double value, int limit) {
        if (!Double.isFinite(value)) throw new IllegalStateException("Non-finite bounds");
        return (int) Math.max(0, Math.min(limit, Math.ceil(value)));
    }
}

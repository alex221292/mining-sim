package miningsim.physics;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.world.Material;
import miningsim.world.World;

/** Read-only discrete overlap query. This is not a sweep or a collision response. */
public final class StaticCollisionQuery {
    public enum MaterialPolicy { LEGACY, DISPLACE_GRANULAR }
    public enum Boundary { NONE, LEFT, RIGHT, TOP, BOTTOM }
    public record Contact(long bodyId, Cell fragmentCell, int worldX, int worldY,
                          double normalX, double normalY, double penetrationCells,
                          double pointX, double pointY, Boundary boundary) { }
    public record Result(List<Contact> contacts, long candidates, long narrowPhaseChecks) {
        public Result { contacts = List.copyOf(contacts); }
    }
    private final World world;
    private final long maxCandidates, maxChecks;
    private final MaterialPolicy policy;

    public StaticCollisionQuery(World world, long maxCandidates, long maxChecks) {
        this(world,maxCandidates,maxChecks,MaterialPolicy.LEGACY);
    }
    public StaticCollisionQuery(World world, long maxCandidates, long maxChecks, MaterialPolicy policy) {
        this.world = Objects.requireNonNull(world);
        if (maxCandidates < 1 || maxChecks < 1) throw new IllegalArgumentException("Work limits must be positive");
        this.maxCandidates = maxCandidates; this.maxChecks = maxChecks;
        this.policy = Objects.requireNonNull(policy);
    }
    long worldRevision() { return policy==MaterialPolicy.DISPLACE_GRANULAR?world.rigidTerrainRevision():world.mutationRevision(); }
    boolean coupledTo(World candidate) { return world==candidate && policy==MaterialPolicy.DISPLACE_GRANULAR; }

    public static boolean solid(Material material) {
        return switch (Objects.requireNonNull(material)) {
            case STONE, BEDROCK, WOOD, SAND, SOIL, GRAVEL -> true;
            case AIR, WATER -> false;
        };
    }
    private boolean isSolid(Material material) {
        if(policy==MaterialPolicy.LEGACY) return solid(material);
        return switch(material) {
            case STONE, BEDROCK, WOOD -> true;
            case AIR, WATER, SAND, SOIL, GRAVEL -> false;
        };
    }

    /** Row-major world candidates followed by the descriptor's deterministic cell order. */
    public Result query(RigidBody body) {
        return query(body,true);
    }
    Result queryBruteForce(RigidBody body) { return query(body,false); }
    private Result query(RigidBody body,boolean accelerated) {
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
        var geometry=body.geometry(); var cells=body.fragment().descriptor().cells();
        double cos=Math.cos(body.angleRadians()),sin=Math.sin(body.angleRadians());
        double radius=.5*(Math.abs(cos)+Math.abs(sin))+.5;
        for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
            if (!isSolid(world.get(x, y))) continue;
            var fixed = new SquareCollision.Square(x + 0.5, y + 0.5, 0);
            double dx=x+.5-body.xCells(),dy=y+.5-body.yCells();
            double localX=geometry.localCenterX()+cos*dx+sin*dy,localY=geometry.localCenterY()-sin*dx+cos*dy;
            if(!Double.isFinite(localX) || !Double.isFinite(localY)) throw new IllegalStateException("Non-finite static candidate geometry");
            double scale=Math.max(Math.max(Math.abs(body.xCells()),Math.abs(body.yCells())),Math.max(x+1.0,y+1.0));
            scale=Math.max(scale,Math.max(Math.abs(localX),Math.abs(localY)));
            double margin=1e-9+32*Math.ulp(Math.max(1,scale));
            double minX=localX-radius-margin,maxX=localX+radius+margin,minY=localY-radius-margin,maxY=localY+radius+margin;
            if(!accelerated) { minX=Double.NEGATIVE_INFINITY; maxX=Double.POSITIVE_INFINITY; minY=Double.NEGATIVE_INFINITY; maxY=Double.POSITIVE_INFINITY; }
            for(int row=geometry.firstRow(minY);row<geometry.rowCount() && geometry.rowY(row)+.5<=maxY;row++) {
                int low=geometry.rowStart(row),high=geometry.rowEnd(row);
                while(low<high) { int mid=(low+high)>>>1; if(cells.get(mid).x()+.5<minX) low=mid+1; else high=mid; }
                for(int ci=low;ci<geometry.rowEnd(row);ci++) {
                var cell=cells.get(ci); if(cell.x()+.5>maxX) break;
                checks = Math.incrementExact(checks);
                if (checks > maxChecks) throw new IllegalStateException("Static collision narrow-phase limit exceeded");
                var center = body.geometry().transform(cell.x() + 0.5, cell.y() + 0.5,
                        body.xCells(), body.yCells(), body.angleRadians());
                var hit = SquareCollision.intersect(new SquareCollision.Square(center.x(), center.y(), body.angleRadians()), fixed);
                if (hit != null) {
                    double nx = hit.normalX(), ny = hit.normalY(), depth = hit.penetrationCells();
                    // A shared tile edge inside a horizontal solid run is not an exposed wall.
                    // Use the outer top face when the body approaches that face from above.
                    boolean leftSolid = x > 0 && isSolid(world.get(x - 1, y));
                    boolean rightSolid = x + 1 < world.width() && isSolid(world.get(x + 1, y));
                    boolean run = leftSolid || rightSolid;
                    boolean exposedTop = y == 0 || !isSolid(world.get(x, y - 1));
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
        }
        for (var cell : body.fragment().descriptor().cells()) {
            for (int dy = 0; dy <= 1; dy++) for (int dx = 0; dx <= 1; dx++) {
                checks = Math.incrementExact(checks);
                if (checks > maxChecks) throw new IllegalStateException("Static collision narrow-phase limit exceeded");
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

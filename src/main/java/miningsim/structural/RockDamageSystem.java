package miningsim.structural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import miningsim.world.Material;
import miningsim.world.World;

/** Single-threaded, selected-component reference; not an automatic simulation clock. */
public final class RockDamageSystem {
    public enum Status { PENDING, CONVERGED, UNSUPPORTED, TOO_LARGE, SINGULAR, NON_CONVERGED }
    public record Bond(int key, long incarnation, int x1, int y1, int x2, int y2,
                       double axial1, double shear1, double moment1, double axial2, double shear2, double moment2,
                       double utilization, double damage, double rate) { }
    public record Snapshot(long revision, int componentId, int nodeCount, double maxDisplacementMetres, List<Bond> bonds) {
        public Snapshot { bonds = List.copyOf(bonds); }
    }
    private record Damage(long incarnation, int x1, int y1, int x2, int y2, double value) { }
    private final World world;
    private final RockConnectivity connectivity;
    private final RockMaterialRegistry registry;
    private final double gravity;
    private final TreeMap<Integer, Damage> damage = new TreeMap<>();
    private Status status = Status.PENDING;
    private Snapshot snapshot;
    private long lastTick = -1;
    private long assemblyNanos, solveNanos;
    private static final double L = 0.1, AREA = L * L, INERTIA = L * L * L * L / 12, MODULUS = L * L * L / 6;

    public RockDamageSystem(World world, RockConnectivity connectivity, RockMaterialRegistry registry, double gravity) {
        this.world = Objects.requireNonNull(world); this.connectivity = Objects.requireNonNull(connectivity);
        this.registry = Objects.requireNonNull(registry);
        if (!connectivity.owns(world)) throw new IllegalArgumentException("Connectivity belongs to another world");
        if (!Double.isFinite(gravity) || gravity < 0) throw new IllegalArgumentException("Invalid gravity");
        this.gravity = gravity;
    }
    public Status status() {
        if (connectivity.pending() || snapshot != null && connectivity.revision() != snapshot.revision()) return Status.PENDING;
        return status;
    }
    public long assemblyNanos() { return assemblyNanos; }
    public long solveNanos() { return solveNanos; }
    public Snapshot snapshot() {
        if (status() != Status.CONVERGED) throw new IllegalStateException("No current converged analysis");
        return snapshot;
    }
    private Status fail(Status s) { snapshot = null; return status = s; }
    private void cleanDamage() {
        damage.values().removeIf(d -> connectivity.bondState(d.x1,d.y1,d.x2,d.y2) == RockConnectivity.BondState.ABSENT
                || connectivity.bondIncarnation(d.x1,d.y1,d.x2,d.y2) != d.incarnation);
    }
    /** Analyze the complete component containing (x,y); other components are not analyzed. */
    public Status analyze(int x, int y) {
        if (!world.contains(x, y)) throw new IndexOutOfBoundsException();
        if (!connectivity.pending() && connectivity.componentAt(x, y) < 0) throw new IllegalArgumentException("Cell is not stone");
        try { return analyzeChecked(x, y); }
        catch (IllegalArgumentException ex) { return fail(Status.NON_CONVERGED); }
    }
    private Status analyzeChecked(int x, int y) {
        long start = System.nanoTime(); assemblyNanos = solveNanos = 0;
        snapshot = null;
        if (connectivity.pending()) return fail(Status.PENDING);
        long revision = connectivity.revision();
        cleanDamage();
        int id = connectivity.componentAt(x, y);
        if (id < 0) throw new IllegalArgumentException("Cell is not stone");
        int ordinal = 0;
        while (connectivity.componentId(ordinal) != id) ordinal++;
        if (connectivity.componentSize(ordinal) > PlanarFrameSolver.MAX_NODES) return fail(Status.TOO_LARGE);
        if (!connectivity.foundationConnected(ordinal)) return fail(Status.UNSUPPORTED);
        ArrayList<Integer> cells = new ArrayList<>();
        for (int cy = 0; cy < world.height(); cy++) for (int cx = 0; cx < world.width(); cx++)
            if (connectivity.componentAt(cx, cy) == id) cells.add(cy * world.width() + cx);
        if (connectivity.revision() != revision || connectivity.pending()) return fail(Status.PENDING);
        PlanarFrameSolver solver = new PlanarFrameSolver(0, 1e-8);
        for (int cell : cells) {
            int cx = cell % world.width(), cy = cell / world.width();
            RockProperties p = registry.get(Byte.toUnsignedInt(world.get(cx, cy).id()));
            if (p == null) return fail(Status.NON_CONVERGED);
            boolean support = touchesBedrock(cx, cy);
            solver.addNode((cx + 0.5) * L, -(cy + 0.5) * L, support, support, support,
                    0, -p.densityKgPerM3() * L * L * L * gravity, 0);
        }
        ArrayList<int[]> bonds = new ArrayList<>(); ArrayList<RockProperties> props = new ArrayList<>();
        for (int i = 0; i < cells.size(); i++) {
            int cell = cells.get(i), cx = cell % world.width(), cy = cell / world.width();
            for (int direction = 0; direction < 2; direction++) {
                int nx = cx + (direction == 0 ? 1 : 0), ny = cy + (direction == 1 ? 1 : 0);
                if (!world.contains(nx, ny) || connectivity.bondState(cx, cy, nx, ny) != RockConnectivity.BondState.INTACT) continue;
                int j = cells.indexOf(ny * world.width() + nx); if (j < 0) continue;
                RockProperties a = registry.get(Byte.toUnsignedInt(world.get(cx, cy).id()));
                RockProperties b = registry.get(Byte.toUnsignedInt(world.get(nx, ny).id()));
                if (a == null || b == null) return fail(Status.NON_CONVERGED);
                RockProperties p = RockProperties.weakerInterface(a, b);
                solver.addElement(i, j, p.youngModulusPa() * AREA, p.youngModulusPa() * INERTIA, 0);
                bonds.add(new int[]{cx, cy, nx, ny}); props.add(p);
            }
        }
        if (connectivity.revision() != revision || connectivity.pending()) return fail(Status.PENDING);
        long solverStart = System.nanoTime();
        PlanarFrameSolver.Result result = solver.solve();
        assemblyNanos = solverStart - start + solver.assemblyNanos();
        solveNanos = solver.solutionNanos();
        if (connectivity.revision() != revision || connectivity.pending()) return fail(Status.PENDING);
        if (result.status() != PlanarFrameSolver.Status.CONVERGED) return fail(Status.valueOf(result.status().name()));
        ArrayList<Bond> output = new ArrayList<>();
        try {
            for (int i = 0; i < bonds.size(); i++) {
                int[] b = bonds.get(i); double[] f = result.endForces()[i];
                int key = connectivity.bondKey(b[0],b[1],b[2],b[3]); long inc = connectivity.bondIncarnation(b[0],b[1],b[2],b[3]);
                Damage old = damage.get(key); double d = old == null || old.incarnation != inc ? 0 : old.value;
                RockProperties p = props.get(i);
                output.add(new Bond(key,inc,b[0],b[1],b[2],b[3],f[0],f[1],f[2],f[3],f[4],f[5],
                        RockBondStrength.utilization(p,AREA,MODULUS,f),d,p.damageRatePerSecond()));
            }
        } catch (IllegalArgumentException ex) { return fail(Status.NON_CONVERGED); }
        if (connectivity.revision() != revision || connectivity.pending()) return fail(Status.PENDING);
        double maxDisplacement = 0;
        for (int i = 0; i < cells.size(); i++)
            maxDisplacement = Math.max(maxDisplacement, Math.hypot(result.displacements()[3*i], result.displacements()[3*i+1]));
        snapshot = new Snapshot(revision,id,cells.size(),maxDisplacement,output); return status = Status.CONVERGED;
    }
    private boolean touchesBedrock(int x, int y) {
        return (x > 0 && world.get(x-1,y) == Material.BEDROCK) || (x+1 < world.width() && world.get(x+1,y) == Material.BEDROCK)
                || (y > 0 && world.get(x,y-1) == Material.BEDROCK) || (y+1 < world.height() && world.get(x,y+1) == Material.BEDROCK);
    }
    /** Strictly increasing nonnegative IDs; a rejected analysis still consumes its tick. */
    public Status advanceTick(long tickId) {
        if (tickId < 0 || tickId <= lastTick) throw new IllegalArgumentException("Tick must increase");
        lastTick = tickId;
        if (status() != Status.CONVERGED) return status();
        Snapshot previous = snapshot;
        ArrayList<Bond> next = new ArrayList<>();
        for (Bond b : previous.bonds()) {
            double value = RockBondStrength.damageAfterTick(b.damage(),b.utilization(),b.rate());
            damage.put(b.key(),new Damage(b.incarnation(),b.x1(),b.y1(),b.x2(),b.y2(),value));
            if (value >= 1) {
                connectivity.breakBond(b.x1(),b.y1(),b.x2(),b.y2());
                return fail(Status.PENDING);
            }
            next.add(new Bond(b.key(),b.incarnation(),b.x1(),b.y1(),b.x2(),b.y2(),b.axial1(),b.shear1(),b.moment1(),b.axial2(),b.shear2(),b.moment2(),b.utilization(),value,b.rate()));
        }
        snapshot = new Snapshot(previous.revision(),previous.componentId(),previous.nodeCount(),previous.maxDisplacementMetres(),next);
        return status;
    }
}

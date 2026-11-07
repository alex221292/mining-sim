package miningsim.structural;

import java.util.ArrayList;
import java.util.List;

/** Small, deterministic, linear elastic planar-frame reference. Coordinates are metres. */
public final class PlanarFrameSolver {
    public static final double CELL_METRES = 0.1;
    public static final int MAX_NODES = 64;
    public static final int MAX_ELEMENTS = 256;
    public enum Status { CONVERGED, SINGULAR, NON_CONVERGED, TOO_LARGE }
    private record Node(double x, double y, boolean fx, boolean fy, boolean fr, double px, double py, double moment) { }
    private record Element(int a, int b, double ea, double ei, double massPerMetre) { }
    public record Result(Status status, double[] displacements, double[][] endForces, double relativeResidual) { }
    private final List<Node> nodes = new ArrayList<>();
    private final List<Element> elements = new ArrayList<>();
    private final double gravity, tolerance;
    private long assemblyNanos, solutionNanos;
    public long assemblyNanos() { return assemblyNanos; }
    public long solutionNanos() { return solutionNanos; }

    public PlanarFrameSolver(double gravity, double tolerance) {
        if (!Double.isFinite(gravity) || gravity < 0 || !Double.isFinite(tolerance) || tolerance <= 0)
            throw new IllegalArgumentException("Invalid gravity or tolerance");
        this.gravity = gravity; this.tolerance = tolerance;
    }
    /** Loads in N and N m; y increases upward. Fixed DOFs have zero displacement. */
    public int addNode(double x, double y, boolean fixedX, boolean fixedY, boolean fixedRotation,
                       double forceX, double forceY, double moment) {
        for (double v : new double[]{x,y,forceX,forceY,moment}) if (!Double.isFinite(v)) throw new IllegalArgumentException("Non-finite node");
        nodes.add(new Node(x,y,fixedX,fixedY,fixedRotation,forceX,forceY,moment));
        return nodes.size() - 1;
    }
    /** EA in N, EI in N m², linear density in kg/m. */
    public void addElement(int a, int b, double ea, double ei, double massPerMetre) {
        if (a < 0 || b < 0 || a >= nodes.size() || b >= nodes.size() || a == b) throw new IllegalArgumentException("Invalid endpoints");
        if (!Double.isFinite(ea) || ea <= 0 || !Double.isFinite(ei) || ei <= 0 || !Double.isFinite(massPerMetre) || massPerMetre < 0)
            throw new IllegalArgumentException("Invalid element properties");
        if (Math.hypot(nodes.get(b).x - nodes.get(a).x, nodes.get(b).y - nodes.get(a).y) == 0) throw new IllegalArgumentException("Zero length element");
        elements.add(new Element(a,b,ea,ei,massPerMetre));
    }
    private static Result failed(Status s) { return new Result(s, new double[0], new double[0][], Double.NaN); }
    /** Input order determines assembly and pivot tie-breaking. */
    public Result solve() {
        try { return solveChecked(); }
        catch (IllegalArgumentException ex) { return failed(Status.NON_CONVERGED); }
    }
    private Result solveChecked() {
        assemblyNanos = solutionNanos = 0;
        if (nodes.size() > MAX_NODES || elements.size() > MAX_ELEMENTS) return failed(Status.TOO_LARGE);
        long start = System.nanoTime();
        int n = nodes.size() * 3;
        if (n == 0) return failed(Status.SINGULAR);
        double[][] k = new double[n][n]; double[] f = new double[n]; boolean[] fixed = new boolean[n];
        for (int i = 0; i < nodes.size(); i++) {
            Node p = nodes.get(i); int j = i * 3;
            f[j] = p.px; f[j+1] = p.py; f[j+2] = p.moment;
            fixed[j] = p.fx; fixed[j+1] = p.fy; fixed[j+2] = p.fr;
        }
        for (Element e : elements) {
            double[][] t = transform(e); double[] localLoad = gravityLoad(e, t);
            int[] ids = indices(e);
            for (int j = 0; j < 6; j++) {
                double[] unit = new double[6], kl = new double[6]; unit[j] = 1;
                PlanarFrameElement.endForces(length(e), e.ea, e.ei, unit, kl);
                for (int row = 0; row < 6; row++) for (int col = 0; col < 6; col++)
                    k[ids[row]][ids[col]] += t[j][col] * t[0][row] * kl[0] + t[j][col] * t[1][row] * kl[1]
                            + t[j][col] * t[2][row] * kl[2] + t[j][col] * t[3][row] * kl[3]
                            + t[j][col] * t[4][row] * kl[4] + t[j][col] * t[5][row] * kl[5];
            }
            for (int i = 0; i < 6; i++) for (int j = 0; j < 6; j++) f[ids[i]] += t[j][i] * localLoad[j];
        }
        for (double value : f) if (!Double.isFinite(value)) return failed(Status.NON_CONVERGED);
        assemblyNanos = System.nanoTime() - start;
        start = System.nanoTime();
        int m = 0; int[] free = new int[n];
        for (int i = 0; i < n; i++) if (!fixed[i]) free[m++] = i;
        double[][] a = new double[m][m]; double[] b = new double[m]; double scale = 0;
        for (int i = 0; i < m; i++) { b[i] = f[free[i]]; for (int j = 0; j < m; j++) { a[i][j] = k[free[i]][free[j]]; scale = Math.max(scale, Math.abs(a[i][j])); } }
        if (!Double.isFinite(scale)) return failed(Status.NON_CONVERGED);
        for (int col = 0; col < m; col++) {
            int pivot = col;
            for (int row = col + 1; row < m; row++) if (Math.abs(a[row][col]) > Math.abs(a[pivot][col])) pivot = row;
            if (Math.abs(a[pivot][col]) <= scale * 1e-12 || scale == 0) return failed(Status.SINGULAR);
            double[] temp = a[col]; a[col] = a[pivot]; a[pivot] = temp;
            double tb = b[col]; b[col] = b[pivot]; b[pivot] = tb;
            for (int row = col + 1; row < m; row++) {
                double factor = a[row][col] / a[col][col];
                for (int j = col + 1; j < m; j++) a[row][j] -= factor * a[col][j];
                b[row] -= factor * b[col];
            }
        }
        double[] u = new double[n];
        for (int i = m - 1; i >= 0; i--) {
            double value = b[i]; for (int j = i + 1; j < m; j++) value -= a[i][j] * u[free[j]];
            u[free[i]] = value / a[i][i];
            if (!Double.isFinite(u[free[i]])) return failed(Status.NON_CONVERGED);
        }
        double residual = 0, norm = 0;
        for (int i = 0; i < m; i++) {
            int row = free[i]; double v = -f[row];
            for (int j = 0; j < n; j++) v += k[row][j] * u[j];
            residual = Math.max(residual, Math.abs(v)); norm = Math.max(norm, Math.abs(f[row]));
        }
        double relative = residual / Math.max(1, norm);
        if (!Double.isFinite(relative) || relative > tolerance) return failed(Status.NON_CONVERGED);
        double[][] forces = new double[elements.size()][6];
        for (int index = 0; index < elements.size(); index++) {
            Element e = elements.get(index); int[] ids = indices(e); double[][] t = transform(e); double[] local = new double[6];
            for (int i = 0; i < 6; i++) for (int j = 0; j < 6; j++) local[i] += t[i][j] * u[ids[j]];
            PlanarFrameElement.endForces(length(e), e.ea, e.ei, local, forces[index]);
            double[] load = gravityLoad(e, t);
            for (int i = 0; i < 6; i++) {
                forces[index][i] -= load[i];
                if (!Double.isFinite(forces[index][i])) return failed(Status.NON_CONVERGED);
            }
        }
        solutionNanos = System.nanoTime() - start;
        return new Result(Status.CONVERGED, u, forces, relative);
    }
    private int[] indices(Element e) { return new int[]{3*e.a,3*e.a+1,3*e.a+2,3*e.b,3*e.b+1,3*e.b+2}; }
    private double length(Element e) { Node a = nodes.get(e.a), b = nodes.get(e.b); return Math.hypot(b.x-a.x, b.y-a.y); }
    private double[][] transform(Element e) {
        Node a = nodes.get(e.a), b = nodes.get(e.b); double l = length(e), c = (b.x-a.x)/l, s = (b.y-a.y)/l;
        double[][] t = new double[6][6];
        for (int i : new int[]{0,3}) { t[i][i]=c; t[i][i+1]=s; t[i+1][i]=-s; t[i+1][i+1]=c; t[i+2][i+2]=1; }
        return t;
    }
    private double[] gravityLoad(Element e, double[][] t) {
        double l = length(e), qx = -e.massPerMetre * gravity * t[0][1], qy = -e.massPerMetre * gravity * t[1][1];
        double[] load = {qx*l/2,qy*l/2,qy*l*l/12,qx*l/2,qy*l/2,-qy*l*l/12};
        for (double value : load) if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite gravity load");
        return load;
    }
}

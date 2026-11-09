package miningsim.physics;

import java.util.Objects;
import miningsim.fragment.RockFragment;

/** Immutable mass properties and center-of-mass transform; positions are in cells. */
public final class FragmentGeometry {
    public static final double METRES_PER_CELL = 0.1;
    public record Point(double x, double y) { }
    public record Bounds(double minX, double minY, double maxX, double maxY) { }
    private final RockFragment fragment;
    private final double localX, localY, massKg, inertiaKgMetresSquared, maxRadiusCells;
    private final int[] rowY,rowStarts;

    public FragmentGeometry(RockFragment fragment) {
        this.fragment = Objects.requireNonNull(fragment);
        var descriptor = fragment.descriptor();
        var rows=new java.util.ArrayList<Integer>(); var starts=new java.util.ArrayList<Integer>();
        int previous=-1,index=0;
        for(var cell:descriptor.cells()) {
            if(cell.y()!=previous) { previous=cell.y(); rows.add(previous); starts.add(index); }
            index++;
        }
        rowY=rows.stream().mapToInt(Integer::intValue).toArray(); rowStarts=new int[starts.size()+1];
        for(int i=0;i<starts.size();i++) rowStarts[i]=starts.get(i);
        rowStarts[starts.size()]=descriptor.cellCount();
        double x = 0, y = 0;
        for (var cell : descriptor.cells()) { x += cell.x() + 0.5; y += cell.y() + 0.5; }
        localX = x / descriptor.cellCount(); localY = y / descriptor.cellCount();
        massKg = descriptor.massMilligrams() / 1_000_000.0;
        double cellMass = massKg / descriptor.cellCount(), inertia = 0;
        for (var cell : descriptor.cells()) {
            double dx = (cell.x() + 0.5 - localX) * METRES_PER_CELL;
            double dy = (cell.y() + 0.5 - localY) * METRES_PER_CELL;
            inertia += cellMass * (METRES_PER_CELL * METRES_PER_CELL / 6 + dx * dx + dy * dy);
        }
        if (!Double.isFinite(massKg) || massKg <= 0 || !Double.isFinite(inertia) || inertia <= 0)
            throw new IllegalArgumentException("Invalid fragment mass or inertia");
        inertiaKgMetresSquared = inertia;
        double radius = 0;
        for (var cell : descriptor.cells()) for (int cy = 0; cy <= 1; cy++) for (int cx = 0; cx <= 1; cx++)
            radius = Math.max(radius, Math.hypot(cell.x() + (double) cx - localX, cell.y() + (double) cy - localY));
        if (!Double.isFinite(radius) || radius <= 0) throw new IllegalArgumentException("Invalid fragment radius");
        maxRadiusCells = radius;
    }

    public double localCenterX() { return localX; }
    public double localCenterY() { return localY; }
    public double massKg() { return massKg; }
    public double inertiaKgMetresSquared() { return inertiaKgMetresSquared; }
    public double maxRadiusCells() { return maxRadiusCells; }
    int rowCount() { return rowY.length; }
    int rowY(int row) { return rowY[row]; }
    int rowStart(int row) { return rowStarts[row]; }
    int rowEnd(int row) { return rowStarts[row+1]; }
    int firstRow(double minCenterY) {
        int low=0,high=rowY.length;
        while(low<high) { int middle=(low+high)>>>1; if(rowY[middle]+0.5<minCenterY) low=middle+1; else high=middle; }
        return low;
    }
    public Point initialCenter() { return new Point(fragment.originX() + localX, fragment.originY() + localY); }

    /** Positive angles rotate clockwise in the downward-positive world coordinate system. */
    public Point transform(double x, double y, double centerX, double centerY, double angle) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(centerX)
                || !Double.isFinite(centerY) || !Double.isFinite(angle)) throw new IllegalArgumentException("Non-finite transform");
        double dx = x - localX, dy = y - localY, cos = Math.cos(angle), sin = Math.sin(angle);
        return new Point(centerX + cos * dx - sin * dy, centerY + sin * dx + cos * dy);
    }

    public Bounds bounds(double centerX, double centerY, double angle) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = Double.NEGATIVE_INFINITY, maxY = maxX;
        for (var cell : fragment.descriptor().cells()) for (int dy = 0; dy <= 1; dy++) for (int dx = 0; dx <= 1; dx++) {
            Point p = transform(cell.x() + (double) dx, cell.y() + (double) dy, centerX, centerY, angle);
            minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
            maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
        }
        return new Bounds(minX, minY, maxX, maxY);
    }
}

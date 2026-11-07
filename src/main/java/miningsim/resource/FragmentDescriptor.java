package miningsim.resource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable connected 2D cell shape; coordinates are nonnegative in its local frame. */
public final class FragmentDescriptor {
    public record Cell(int x, int y) implements Comparable<Cell> {
        public Cell { if (x < 0 || y < 0 || x == Integer.MAX_VALUE || y == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid coordinate"); }
        @Override public int compareTo(Cell other) { int c = Integer.compare(y,other.y); return c != 0 ? c : Integer.compare(x,other.x); }
    }
    private final SubstanceProperties substance;
    private final List<Cell> cells;
    private final long massMilligrams;
    private final int width, height;
    public FragmentDescriptor(SubstanceProperties substance, Collection<Cell> cells) {
        this.substance = Objects.requireNonNull(substance);
        Objects.requireNonNull(cells);
        if (cells.isEmpty()) throw new IllegalArgumentException("Empty fragment");
        var set = new HashSet<Cell>(cells);
        if (set.size() != cells.size() || set.contains(null)) throw new IllegalArgumentException("Duplicate or null cell");
        var sorted = new ArrayList<>(set); sorted.sort(Comparator.naturalOrder());
        if (components(set).size() != 1) throw new IllegalArgumentException("Fragment must be connected");
        this.cells = List.copyOf(sorted);
        width = Math.addExact(sorted.stream().mapToInt(Cell::x).max().orElseThrow() - sorted.stream().mapToInt(Cell::x).min().orElseThrow(),1);
        height = Math.addExact(sorted.stream().mapToInt(Cell::y).max().orElseThrow() - sorted.stream().mapToInt(Cell::y).min().orElseThrow(),1);
        massMilligrams = MassAccounting.solidCellsMilligrams(substance, sorted.size());
    }
    public SubstanceProperties substance() { return substance; }
    public List<Cell> cells() { return cells; }
    public int cellCount() { return cells.size(); }
    public int width() { return width; }
    public int height() { return height; }
    public long massMilligrams() { return massMilligrams; }
    public List<FragmentDescriptor> partition(List<? extends Collection<Cell>> parts) {
        if (parts.size() < 2) throw new IllegalArgumentException("At least two parts required");
        Set<Cell> seen = new HashSet<>(); List<FragmentDescriptor> result = new ArrayList<>();
        for (var part : parts) {
            for (Cell cell : part) if (!seen.add(cell)) throw new IllegalArgumentException("Overlapping parts");
            result.add(new FragmentDescriptor(substance,part));
        }
        if (!seen.equals(new HashSet<>(cells))) throw new IllegalArgumentException("Parts must cover exactly the original geometry");
        return List.copyOf(result);
    }
    static List<Set<Cell>> components(Set<Cell> input) {
        var rest = new HashSet<>(input); var result = new ArrayList<Set<Cell>>();
        while (!rest.isEmpty()) {
            Cell first = rest.stream().min(Comparator.naturalOrder()).orElseThrow();
            var part = new HashSet<Cell>(); var queue = new ArrayDeque<Cell>(); queue.add(first); rest.remove(first);
            while (!queue.isEmpty()) {
                Cell c = queue.remove(); part.add(c);
                for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    long x=(long)c.x()+d[0], y=(long)c.y()+d[1];
                    if (x<0 || y<0 || x>=Integer.MAX_VALUE || y>=Integer.MAX_VALUE) continue;
                    Cell n=new Cell((int)x,(int)y); if (rest.remove(n)) queue.add(n);
                }
            }
            result.add(part);
        }
        return result;
    }
}

package miningsim.structural;

import java.util.BitSet;
import java.util.Objects;
import miningsim.world.Material;
import miningsim.world.World;

/** Single-threaded, explicitly advanced topological reference; not a stability solver. */
public final class RockConnectivity implements World.ChangeSink {
    private final World world;
    private final int width, height, cells;
    // Two canonical bits per cell: bond to right, bond below.
    private BitSet broken = new BitSet();
    private final int[] seen, labels, queue, componentIds, sizes;
    private final boolean[] foundations;
    private int generation, scan, head, tail, currentId, count;
    private boolean pending = true, running;
    private long revision, restarts, totalWork;

    public RockConnectivity(World world) {
        this.world = Objects.requireNonNull(world);
        width = world.width(); height = world.height(); cells = Math.multiplyExact(width, height);
        // BitSet uses int bit indices, so both bond orientations must fit.
        if (cells > Integer.MAX_VALUE / 2) throw new IllegalArgumentException("World too large for bond indices");
        seen = new int[cells]; labels = new int[cells]; queue = new int[cells];
        componentIds = new int[cells]; sizes = new int[cells]; foundations = new boolean[cells];
        world.setChangeSink(this);
    }

    private void attached() {
        if (!world.hasChangeSink(this)) throw new IllegalStateException("Structural change sink was detached or replaced");
    }
    private void dirty() {
        if (running) { restarts++; running = false; }
        pending = true; revision++;
    }
    @Override public void changed(int x, int y, byte previous, byte next) {
        if ((previous == Material.STONE.id()) == (next == Material.STONE.id())) return;
        int i = y * width + x;
        broken.clear(i * 2); broken.clear(i * 2 + 1);
        if (x > 0) broken.clear((i - 1) * 2);
        if (y > 0) broken.clear((i - width) * 2 + 1);
        dirty();
    }
    @Override public void invalidated() { broken = new BitSet(); dirty(); }

    /** Canonical bit index for orthogonally adjacent in-bounds cells. */
    public int bondKey(int x1, int y1, int x2, int y2) {
        if (!world.contains(x1, y1) || !world.contains(x2, y2)) throw new IndexOutOfBoundsException();
        if (Math.abs(x1 - x2) + Math.abs(y1 - y2) != 1) throw new IllegalArgumentException("Cells must be orthogonal neighbors");
        int a = y1 * width + x1, b = y2 * width + x2;
        return Math.min(a, b) * 2 + (y1 == y2 ? 0 : 1);
    }
    /** Break an existing intact stone bond; returns false if already broken. */
    public boolean breakBond(int x1, int y1, int x2, int y2) {
        attached();
        int key = bondKey(x1, y1, x2, y2);
        if (world.get(x1, y1) != Material.STONE || world.get(x2, y2) != Material.STONE)
            throw new IllegalArgumentException("Bond requires two stone cells");
        if (broken.get(key)) return false;
        broken.set(key); dirty(); return true;
    }
    public boolean pending() { attached(); return pending; }
    public long revision() { attached(); return revision; }
    public long restarts() { attached(); return restarts; }
    public long totalWork() { attached(); return totalWork; }

    /** One unit scans a world cell or expands one queued stone cell (up to four neighbors). */
    public int advance(int budget) {
        attached();
        if (budget <= 0) throw new IllegalArgumentException("Budget must be positive");
        if (!pending) return 0;
        if (!running) {
            // Generation wrap is practically unreachable, but keep the marker correct.
            if (++generation == 0) { java.util.Arrays.fill(seen, 0); generation = 1; }
            scan = head = tail = count = 0; running = true;
        }
        int used = 0;
        while (used < budget && pending) {
            if (head < tail) {
                int i = queue[head++], x = i % width, y = i / width;
                if (x > 0) visit(i, i - 1, x - 1, y);
                if (x + 1 < width) visit(i, i + 1, x + 1, y);
                if (y > 0) visit(i, i - width, x, y - 1);
                if (y + 1 < height) visit(i, i + width, x, y + 1);
            } else if (scan < cells) {
                int i = scan++;
                if (seen[i] != generation && world.get(i % width, i / width) == Material.STONE) {
                    currentId = i; componentIds[count++] = i; sizes[i] = 1; foundations[i] = false;
                    seen[i] = generation; labels[i] = i; head = 0; tail = 1; queue[0] = i;
                }
            } else { pending = false; running = false; break; }
            used++;
        }
        if (scan == cells && head == tail) { pending = false; running = false; }
        totalWork += used;
        return used;
    }
    private void visit(int from, int to, int x, int y) {
        Material material = world.get(x, y);
        if (material == Material.BEDROCK) { foundations[currentId] = true; return; }
        if (material != Material.STONE || seen[to] == generation) return;
        int key = Math.min(from, to) * 2 + (Math.abs(from - to) == 1 && from / width == to / width ? 0 : 1);
        if (broken.get(key)) return;
        seen[to] = generation; labels[to] = currentId; sizes[currentId]++; queue[tail++] = to;
    }
    private void complete() { attached(); if (pending) throw new IllegalStateException("Connectivity analysis incomplete"); }
    public int componentCount() { complete(); return count; }
    /** IDs are minimum row-major cell indices, returned in increasing order. */
    public int componentId(int ordinal) { complete(); if (ordinal < 0 || ordinal >= count) throw new IndexOutOfBoundsException(); return componentIds[ordinal]; }
    /** Returns -1 for non-stone cells. */
    public int componentAt(int x, int y) {
        complete(); if (!world.contains(x, y)) throw new IndexOutOfBoundsException();
        int i = y * width + x; return seen[i] == generation ? labels[i] : -1;
    }
    public int componentSize(int ordinal) { return sizes[componentId(ordinal)]; }
    public boolean foundationConnected(int ordinal) { return foundations[componentId(ordinal)]; }
}

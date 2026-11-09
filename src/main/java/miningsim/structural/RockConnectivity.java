package miningsim.structural;

import java.util.BitSet;
import java.util.Objects;
import miningsim.world.Material;
import miningsim.world.World;

/** Single-threaded, explicitly advanced topological reference; not a stability solver. */
public final class RockConnectivity implements World.ChangeSink {
    public enum BondState { ABSENT, INTACT, BROKEN }
    @FunctionalInterface public interface BondVisitor {
        void visit(int x1, int y1, int x2, int y2, BondState state);
    }
    private final World world;
    private final int width, height, cells;
    // Two canonical bits per cell: bond to right, bond below.
    private BitSet broken = new BitSet();
    private final int[] seen, labels, queue, componentIds, sizes, componentOrdinals, nextCell, lastCell;
    private final boolean[] foundations;
    private final long[] cellIncarnations;
    private int generation, scan, head, tail, currentId, count;
    private boolean pending = true, running;
    private long revision, restarts, totalWork, invalidationRevision;
    private long lastAdditionRevision, lastDisruptionRevision;
    private boolean revisionExhausted;
    private int traversalDepth;

    public RockConnectivity(World world) {
        this.world = Objects.requireNonNull(world);
        width = world.width(); height = world.height(); cells = Math.multiplyExact(width, height);
        // BitSet uses int bit indices, so both bond orientations must fit.
        if (cells > Integer.MAX_VALUE / 2) throw new IllegalArgumentException("World too large for bond indices");
        seen = new int[cells]; labels = new int[cells]; queue = new int[cells];
        componentIds = new int[cells]; sizes = new int[cells]; foundations = new boolean[cells];
        componentOrdinals = new int[cells]; nextCell = new int[cells]; lastCell = new int[cells];
        cellIncarnations = new long[cells];
        world.setChangeSink(this);
    }

    private void attached() {
        if (!world.hasChangeSink(this)) throw new IllegalStateException("Structural change sink was detached or replaced");
        if (revisionExhausted) throw new IllegalStateException("Topology revision exhausted; create a new connectivity instance");
    }
    private void dirty() {
        if (running) { restarts++; running = false; }
        pending = true;
        if (revision == Long.MAX_VALUE) revisionExhausted = true;
        else revision++;
    }
    @Override public void changed(int x, int y, byte previous, byte next) {
        if ((previous == Material.STONE.id()) == (next == Material.STONE.id())) return;
        int i = y * width + x;
        broken.clear(i * 2); broken.clear(i * 2 + 1);
        if (x > 0) broken.clear((i - 1) * 2);
        if (y > 0) broken.clear((i - width) * 2 + 1);
        dirty();
        if(next==Material.STONE.id()) lastAdditionRevision=revision; else lastDisruptionRevision=revision;
        cellIncarnations[i] = revision;
    }
    @Override public void changedBatch(java.util.List<World.CellChange> changes) {
        boolean rockChanged = false;
        for (var c : changes) if ((c.expected() == Material.STONE) != (c.replacement() == Material.STONE)) { rockChanged = true; break; }
        if (!rockChanged) return;
        dirty();
        for (var c : changes) {
            if ((c.expected() == Material.STONE) == (c.replacement() == Material.STONE)) continue;
            if(c.replacement()==Material.STONE) lastAdditionRevision=revision; else lastDisruptionRevision=revision;
            int i = c.y() * width + c.x();
            broken.clear(i * 2); broken.clear(i * 2 + 1);
            if (c.x() > 0) broken.clear((i - 1) * 2);
            if (c.y() > 0) broken.clear((i - width) * 2 + 1);
            cellIncarnations[i] = revision;
        }
    }
    @Override public boolean allowBatch() { return traversalDepth == 0; }
    @Override public void invalidated() { broken = new BitSet(); dirty(); invalidationRevision = revision; }

    /** Canonical bit index for orthogonally adjacent in-bounds cells. */
    public int bondKey(int x1, int y1, int x2, int y2) {
        if (!world.contains(x1, y1) || !world.contains(x2, y2)) throw new IndexOutOfBoundsException();
        if (Math.abs(x1 - x2) + Math.abs(y1 - y2) != 1) throw new IllegalArgumentException("Cells must be orthogonal neighbors");
        int a = y1 * width + x1, b = y2 * width + x2;
        return Math.min(a, b) * 2 + (y1 == y2 ? 0 : 1);
    }
    /** Live physical state, available even while component analysis is pending. */
    public BondState bondState(int x1, int y1, int x2, int y2) {
        attached();
        int key = bondKey(x1, y1, x2, y2);
        if (world.get(x1, y1) != Material.STONE || world.get(x2, y2) != Material.STONE) return BondState.ABSENT;
        return broken.get(key) ? BondState.BROKEN : BondState.INTACT;
    }
    /** Incarnation of the current pair of endpoints, unchanged by breaking the bond. */
    public long bondIncarnation(int x1, int y1, int x2, int y2) {
        if (bondState(x1, y1, x2, y2) == BondState.ABSENT) throw new IllegalStateException("Bond has no stone endpoints");
        return Math.max(invalidationRevision, Math.max(cellIncarnations[y1 * width + x1], cellIncarnations[y2 * width + x2]));
    }
    /** Visits bonds with both endpoints in the half-open rectangle, in row-major, right-then-down order.
     * Visitor must not mutate the world or topology; on mutation the caller must discard all observations. */
    public void forEachBond(int left, int top, int right, int bottom, BondVisitor visitor) {
        attached(); Objects.requireNonNull(visitor);
        if (left < 0 || top < 0 || right < left || bottom < top || right > width || bottom > height)
            throw new IndexOutOfBoundsException("Invalid bond rectangle");
        long expected = revision;
        traversalDepth++;
        try {
            for (int y = top; y < bottom; y++) for (int x = left; x < right; x++) {
                if (world.get(x, y) != Material.STONE) continue;
                if (x + 1 < right) visitBond(x, y, x + 1, y, visitor, expected);
                if (y + 1 < bottom) visitBond(x, y, x, y + 1, visitor, expected);
            }
            attached();
        } finally { traversalDepth--; }
    }
    private void visitBond(int x1, int y1, int x2, int y2, BondVisitor visitor, long expected) {
        BondState state = bondState(x1, y1, x2, y2);
        if (state != BondState.ABSENT) {
            visitor.visit(x1, y1, x2, y2, state);
            attached();
            if (revision != expected) throw new IllegalStateException("Topology changed during bond traversal");
        }
    }
    /** Break an existing intact stone bond; returns false if already broken. */
    public boolean breakBond(int x1, int y1, int x2, int y2) {
        attached();
        int key = bondKey(x1, y1, x2, y2);
        if (world.get(x1, y1) != Material.STONE || world.get(x2, y2) != Material.STONE)
            throw new IllegalArgumentException("Bond requires two stone cells");
        if (broken.get(key)) return false;
        broken.set(key); dirty(); lastDisruptionRevision=revision; return true;
    }
    public boolean pending() { attached(); return pending; }
    public boolean owns(World candidate) { attached(); return world == candidate; }
    public boolean traversing() { attached(); return traversalDepth != 0; }
    public long revision() { attached(); return revision; }
    public long restarts() { attached(); return restarts; }
    public long totalWork() { attached(); return totalWork; }
    public long lastAdditionRevision() { attached(); return lastAdditionRevision; }
    public long lastDisruptionRevision() { attached(); return lastDisruptionRevision; }

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
                    currentId = i; componentOrdinals[i]=count; componentIds[count++] = i; sizes[i] = 1; foundations[i] = false;
                    nextCell[i]=-1; lastCell[i]=i;
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
        nextCell[lastCell[currentId]]=to; lastCell[currentId]=to; nextCell[to]=-1;
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
    /** Returns the completed component's row-major linear coordinates, without scanning the world. */
    public int[] componentCells(int ordinal) {
        int id=componentId(ordinal); int[] result=new int[sizes[id]]; int cell=id;
        for(int i=0;i<result.length;i++) { result[i]=cell; cell=nextCell[cell]; }
        java.util.Arrays.sort(result); return result;
    }
    public int componentOrdinal(int id) {
        complete(); if(id<0 || id>=cells || seen[id]!=generation || labels[id]!=id) return -1;
        return componentOrdinals[id];
    }
}

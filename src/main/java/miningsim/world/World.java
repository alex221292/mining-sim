package miningsim.world;

import java.util.Arrays;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import miningsim.fragment.FragmentRegistry;

public final class World {
    public enum Mode { LEGACY, BEDROCK_ENABLED }
    public enum Mutation { CHANGED, NO_OP, REJECTED }
    public enum BatchResult { CHANGED, NO_OP, REJECTED }
    public record CellChange(int x, int y, Material expected, Material replacement) { }
    /** Synchronous single consumer; callbacks must not mutate this world or throw. */
    public interface ChangeSink {
        void changed(int x, int y, byte previous, byte next);
        void invalidated();
        /** Called once after a complete batch; default maintains compatibility with existing sinks. */
        default void changedBatch(List<CellChange> changes) {
            for (CellChange c : changes) changed(c.x(), c.y(), c.expected().id(), c.replacement().id());
        }
        default boolean allowBatch() { return true; }
    }
    public static final int ACTIVITY_TILE_SIZE = 32;
    public static final int CHUNK_SIZE = Chunk.SIZE;
    private static final int CHUNK_SHIFT = 6, CHUNK_MASK = CHUNK_SIZE - 1;
    private final int width, height;
    private final int chunkColumns;
    private final Chunk[] chunks;
    private final int tileColumns, tileRows;
    private final boolean[] changedTiles;
    private boolean trackActivity;
    private final Mode mode;
    private ChangeSink changeSink;
    private long mutationRevision;

    public World(int width, int height) {
        this(width, height, Mode.LEGACY);
    }
    public World(int width, int height, Mode mode) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Dimensions must be positive");
        this.mode = Objects.requireNonNull(mode);
        this.width = width;
        this.height = height;
        Math.multiplyExact(width, height);
        chunkColumns = (width - 1) / CHUNK_SIZE + 1;
        int chunkRows = (height - 1) / CHUNK_SIZE + 1;
        chunks = new Chunk[Math.multiplyExact(chunkColumns, chunkRows)];
        for (int i = 0; i < chunks.length; i++) chunks[i] = new Chunk();
        tileColumns = (width - 1) / ACTIVITY_TILE_SIZE + 1;
        tileRows = (height - 1) / ACTIVITY_TILE_SIZE + 1;
        changedTiles = new boolean[tileColumns * tileRows];
        Arrays.fill(changedTiles, true);
        if (mode == Mode.BEDROCK_ENABLED) {
            for (int x = 0; x < width; x++) {
                chunkAt(x, height - 1).cells[((height - 1 & CHUNK_MASK) << CHUNK_SHIFT) | (x & CHUNK_MASK)] = Material.BEDROCK.id();
            }
        }
    }

    public Mode mode() { return mode; }
    /** Replacing or attaching a consumer requires it to rebuild from the current world. */
    public void setChangeSink(ChangeSink sink) { changeSink = sink; if (sink != null) sink.invalidated(); }
    public boolean hasChangeSink(ChangeSink sink) { return sink != null && changeSink == sink; }
    public int width() { return width; }
    public int height() { return height; }
    /** Single-owner mutation token; does not install or replace the structural change sink. */
    public long mutationRevision() { return mutationRevision; }
    public boolean contains(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    private Chunk chunkAt(int x, int y) {
        if (!contains(x, y)) throw new IndexOutOfBoundsException(x + ", " + y);
        // Bounds checking guarantees nonnegative coordinates for this power-of-two mapping.
        return chunks[(y >>> CHUNK_SHIFT) * chunkColumns + (x >>> CHUNK_SHIFT)];
    }
    public Material get(int x, int y) { return Material.fromId(chunkAt(x, y).cells[((y & CHUNK_MASK) << CHUNK_SHIFT) | (x & CHUNK_MASK)]); }
    /** Reusable live read view of one bounded row segment; not a snapshot. */
    public final class RowSegment {
        private byte[] rowCells;
        private int rowOffset, start, end;

        public void bind(int y, int x) {
            Chunk chunk = chunkAt(x, y);
            rowCells = chunk.cells;
            rowOffset = (y & CHUNK_MASK) << CHUNK_SHIFT;
            start = (x >>> CHUNK_SHIFT) << CHUNK_SHIFT;
            end = Math.min(width, start + CHUNK_SIZE);
        }
        public Material get(int x) {
            if (x < start || x >= end || rowCells == null) throw new IndexOutOfBoundsException("x=" + x);
            return Material.fromId(rowCells[rowOffset + (x & CHUNK_MASK)]);
        }
    }
    public RowSegment newRowSegment() { return new RowSegment(); }
    public Mutation set(int x, int y, Material material) {
        Chunk chunk = chunkAt(x, y);
        byte id = Objects.requireNonNull(material).id();
        int localIndex = ((y & CHUNK_MASK) << CHUNK_SHIFT) | (x & CHUNK_MASK);
        byte previous = chunk.cells[localIndex];
        if (previous == id) return Mutation.NO_OP;
        if (material == Material.BEDROCK || previous == Material.BEDROCK.id()) return Mutation.REJECTED;
        long nextRevision = Math.addExact(mutationRevision, 1);
        chunk.cells[localIndex] = id;
        mutationRevision = nextRevision;
        if (trackActivity) changedTiles[(y / ACTIVITY_TILE_SIZE) * tileColumns + x / ACTIVITY_TILE_SIZE] = true;
        if (changeSink != null) changeSink.changed(x, y, previous, id);
        return Mutation.CHANGED;
    }
    /** Single-owner operation: validate completely, then publish one immutable batch. */
    public BatchResult replaceAllIfMatches(List<CellChange> changes) {
        return replaceAllIfMatches(changes,null);
    }
    /** Extraction-only ownership transfer. The prepared registry state is published before observers are notified. */
    public BatchResult replaceAllIfMatches(List<CellChange> changes, FragmentRegistry.Prepared prepared) {
        Objects.requireNonNull(changes);
        if (changeSink != null && !changeSink.allowBatch()) return BatchResult.REJECTED;
        if (prepared != null && !prepared.valid()) return BatchResult.REJECTED;
        ArrayList<CellChange> sorted = new ArrayList<>(changes);
        if (sorted.stream().anyMatch(Objects::isNull)) return BatchResult.REJECTED;
        sorted.sort(Comparator.comparingInt(CellChange::y).thenComparingInt(CellChange::x));
        ArrayList<CellChange> actual = new ArrayList<>();
        CellChange previous = null;
        for (CellChange c : sorted) {
            if (c.expected() == null || c.replacement() == null || !contains(c.x(),c.y()) ||
                    previous != null && previous.x() == c.x() && previous.y() == c.y()) return BatchResult.REJECTED;
            previous = c;
            Material current = get(c.x(),c.y());
            if (current != c.expected()) return BatchResult.REJECTED;
            if (current != c.replacement()) {
                if (current == Material.BEDROCK || c.replacement() == Material.BEDROCK) return BatchResult.REJECTED;
                actual.add(c);
            }
        }
        if (actual.isEmpty()) return prepared == null ? BatchResult.NO_OP : BatchResult.REJECTED;
        if (prepared != null) {
            var fragment = prepared.fragment();
            if (!fragment.descriptor().substance().equals(miningsim.resource.SubstanceRegistry.reference().get(miningsim.resource.SubstanceRegistry.STONE))) return BatchResult.REJECTED;
            if (actual.size() != fragment.descriptor().cellCount()) return BatchResult.REJECTED;
            for (int i = 0; i < actual.size(); i++) {
                var change = actual.get(i); var cell = fragment.descriptor().cells().get(i);
                if (change.expected() != Material.STONE || change.replacement() != Material.AIR ||
                        change.x() != fragment.worldX(cell) || change.y() != fragment.worldY(cell)) return BatchResult.REJECTED;
            }
        }
        List<CellChange> notification = List.copyOf(actual);
        long nextRevision = Math.addExact(mutationRevision, 1);
        for (CellChange c : notification) {
            Chunk chunk = chunkAt(c.x(),c.y());
            chunk.cells[((c.y() & CHUNK_MASK) << CHUNK_SHIFT) | (c.x() & CHUNK_MASK)] = c.replacement().id();
            if (trackActivity) changedTiles[(c.y() / ACTIVITY_TILE_SIZE) * tileColumns + c.x() / ACTIVITY_TILE_SIZE] = true;
        }
        mutationRevision = nextRevision;
        // No external code runs between preflight and this allocation-free single-owner publication.
        if (prepared != null) prepared.publish();
        if (changeSink != null) changeSink.changedBatch(notification);
        return BatchResult.CHANGED;
    }
    public void clear() {
        long nextRevision = Math.addExact(mutationRevision, 1);
        if (mode == Mode.LEGACY) {
            for (Chunk chunk : chunks) chunk.clear();
        } else {
            int bottomChunkRow = (height - 1) >>> CHUNK_SHIFT;
            int bottomOffset = ((height - 1) & CHUNK_MASK) << CHUNK_SHIFT;
            for (int i = 0; i < chunks.length; i++) {
                if (i / chunkColumns == bottomChunkRow) Arrays.fill(chunks[i].cells, 0, bottomOffset, Material.AIR.id());
                else chunks[i].clear();
            }
        }
        Arrays.fill(changedTiles, true);
        mutationRevision = nextRevision;
        if (changeSink != null) changeSink.invalidated();
    }
    public int activityTileColumns() { return tileColumns; }
    public int activityTileRows() { return tileRows; }
    /** A world has one simulation owner; switching mode invalidates the activity mask. */
    public void setActivityTracking(boolean enabled) {
        trackActivity = enabled;
        if (enabled) Arrays.fill(changedTiles, true);
    }
    /** Transfers pending changes to the simulation's activity mask, then clears them. */
    public void collectActivity(boolean[] activity) {
        if (activity.length != changedTiles.length) throw new IllegalArgumentException("wrong activity mask size");
        for (int ty = 0; ty < tileRows; ty++) for (int tx = 0; tx < tileColumns; tx++) {
            int index = ty * tileColumns + tx;
            if (!changedTiles[index]) continue;
            // Whole neighboring tiles conservatively cover the water search radius.
            for (int y = Math.max(0, ty - 1); y <= Math.min(tileRows - 1, ty + 1); y++)
                for (int x = Math.max(0, tx - 1); x <= Math.min(tileColumns - 1, tx + 1); x++)
                    activity[y * tileColumns + x] = true;
            changedTiles[index] = false;
        }
    }
    public int occupiedCount() {
        int count = 0;
        // Unused edge cells remain AIR because public access checks world bounds.
        for (Chunk chunk : chunks) count += chunk.occupiedCount();
        return count;
    }
}

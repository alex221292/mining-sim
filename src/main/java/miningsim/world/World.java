package miningsim.world;

import java.util.Arrays;
import java.util.Objects;

public final class World {
    public static final int ACTIVITY_TILE_SIZE = 32;
    public static final int CHUNK_SIZE = Chunk.SIZE;
    private static final int CHUNK_SHIFT = 6, CHUNK_MASK = CHUNK_SIZE - 1;
    private final int width, height;
    private final int chunkColumns;
    private final Chunk[] chunks;
    private final int tileColumns, tileRows;
    private final boolean[] changedTiles;
    private boolean trackActivity;

    public World(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Dimensions must be positive");
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
    }

    public int width() { return width; }
    public int height() { return height; }
    public boolean contains(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    private Chunk chunkAt(int x, int y) {
        if (!contains(x, y)) throw new IndexOutOfBoundsException(x + ", " + y);
        // Bounds checking guarantees nonnegative coordinates for this power-of-two mapping.
        return chunks[(y >>> CHUNK_SHIFT) * chunkColumns + (x >>> CHUNK_SHIFT)];
    }
    public Material get(int x, int y) { return Material.fromId(chunkAt(x, y).cells[((y & CHUNK_MASK) << CHUNK_SHIFT) | (x & CHUNK_MASK)]); }
    public void set(int x, int y, Material material) {
        Chunk chunk = chunkAt(x, y);
        byte id = Objects.requireNonNull(material).id();
        int localIndex = ((y & CHUNK_MASK) << CHUNK_SHIFT) | (x & CHUNK_MASK);
        if (chunk.cells[localIndex] == id) return;
        chunk.cells[localIndex] = id;
        if (trackActivity) changedTiles[(y / ACTIVITY_TILE_SIZE) * tileColumns + x / ACTIVITY_TILE_SIZE] = true;
    }
    public void clear() { for (Chunk chunk : chunks) chunk.clear(); Arrays.fill(changedTiles, true); }
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

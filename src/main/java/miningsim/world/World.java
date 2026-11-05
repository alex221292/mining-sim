package miningsim.world;

import java.util.Arrays;
import java.util.Objects;

public final class World {
    public static final int ACTIVITY_TILE_SIZE = 32;
    private final int width, height;
    private final byte[] cells;
    private final int tileColumns, tileRows;
    private final boolean[] changedTiles;
    private boolean trackActivity;

    public World(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Dimensions must be positive");
        this.width = width;
        this.height = height;
        cells = new byte[Math.multiplyExact(width, height)];
        tileColumns = (width - 1) / ACTIVITY_TILE_SIZE + 1;
        tileRows = (height - 1) / ACTIVITY_TILE_SIZE + 1;
        changedTiles = new boolean[tileColumns * tileRows];
        Arrays.fill(changedTiles, true);
    }

    public int width() { return width; }
    public int height() { return height; }
    public boolean contains(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    private int index(int x, int y) {
        if (!contains(x, y)) throw new IndexOutOfBoundsException(x + ", " + y);
        return y * width + x;
    }
    public Material get(int x, int y) { return Material.fromId(cells[index(x, y)]); }
    public void set(int x, int y, Material material) {
        int index = index(x, y);
        byte id = Objects.requireNonNull(material).id();
        if (cells[index] == id) return;
        cells[index] = id;
        if (trackActivity) changedTiles[(y / ACTIVITY_TILE_SIZE) * tileColumns + x / ACTIVITY_TILE_SIZE] = true;
    }
    public void clear() { Arrays.fill(cells, Material.AIR.id()); Arrays.fill(changedTiles, true); }
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
        for (byte cell : cells) if (cell != Material.AIR.id()) count++;
        return count;
    }
}

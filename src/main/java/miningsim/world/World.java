package miningsim.world;

import java.util.Arrays;
import java.util.Objects;

public final class World {
    private final int width, height;
    private final byte[] cells;

    public World(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Dimensions must be positive");
        this.width = width;
        this.height = height;
        cells = new byte[Math.multiplyExact(width, height)];
    }

    public int width() { return width; }
    public int height() { return height; }
    public boolean contains(int x, int y) { return x >= 0 && x < width && y >= 0 && y < height; }
    private int index(int x, int y) {
        if (!contains(x, y)) throw new IndexOutOfBoundsException(x + ", " + y);
        return y * width + x;
    }
    public Material get(int x, int y) { return Material.fromId(cells[index(x, y)]); }
    public void set(int x, int y, Material material) { cells[index(x, y)] = Objects.requireNonNull(material).id(); }
    public void clear() { Arrays.fill(cells, Material.AIR.id()); }
    public int occupiedCount() {
        int count = 0;
        for (byte cell : cells) if (cell != Material.AIR.id()) count++;
        return count;
    }
}

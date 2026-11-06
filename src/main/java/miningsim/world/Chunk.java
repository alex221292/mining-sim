package miningsim.world;

import java.util.Arrays;

/** Storage only; local coordinates are checked by World before mapping. */
final class Chunk {
    static final int SIZE = 64;
    final byte[] cells = new byte[SIZE * SIZE];

    void clear() { Arrays.fill(cells, Material.AIR.id()); }
    int occupiedCount() {
        int count = 0;
        for (byte cell : cells) if (cell != Material.AIR.id()) count++;
        return count;
    }
}

package miningsim.world;

import java.util.Arrays;
import miningsim.resource.OreComposition;

/** Storage only; local coordinates are checked by World before mapping. */
final class Chunk {
    static final int SIZE = 64;
    final byte[] cells = new byte[SIZE * SIZE];
    OreComposition[] ores;
    int oreCount;

    OreComposition ore(int index) { return ores == null || ores[index] == null ? OreComposition.EMPTY : ores[index]; }
    void putOre(int index, OreComposition value) {
        boolean present = value != OreComposition.EMPTY && !value.components().isEmpty();
        boolean old = ores != null && ores[index] != null;
        if (present && ores == null) ores = new OreComposition[SIZE * SIZE];
        if (ores != null) ores[index] = present ? value : null;
        if (present != old) oreCount += present ? 1 : -1;
        if (oreCount == 0) ores = null;
    }

    void clear() { Arrays.fill(cells, Material.AIR.id()); ores = null; oreCount = 0; }
    int occupiedCount() {
        int count = 0;
        for (byte cell : cells) if (cell != Material.AIR.id()) count++;
        return count;
    }
}

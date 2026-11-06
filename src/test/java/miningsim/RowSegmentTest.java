package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.simulation.Simulation;

class RowSegmentTest {
    @Test void liveViewObservesWritesAndClearAcrossPartialChunks() {
        World w = new World(130, 66); World.RowSegment row = w.newRowSegment();
        assertThrows(IndexOutOfBoundsException.class, () -> row.get(0));
        row.bind(63, 63); w.set(63, 63, Material.SAND); assertEquals(Material.SAND, row.get(63));
        assertThrows(IndexOutOfBoundsException.class, () -> row.get(64));
        row.bind(63, 64); w.set(64, 63, Material.WATER); assertEquals(Material.WATER, row.get(64));
        w.clear(); assertEquals(Material.AIR, row.get(64));
        row.bind(65, 129); w.set(129, 65, Material.SOIL); assertEquals(Material.SOIL, row.get(129));
        assertThrows(IndexOutOfBoundsException.class, () -> row.get(130));
        assertThrows(IndexOutOfBoundsException.class, () -> row.bind(66, 0));
    }

    @Test void boundaryCrossingForBothScanDirectionsAndModes() {
        for (boolean reverse : new boolean[]{false, true}) {
            long seed = 0; while (new Random(seed).nextBoolean() != reverse) seed++;
            for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
                World w = new World(130, 66); Simulation s = new Simulation(w, seed, mode, false);
                int from = reverse ? 64 : 63, to = reverse ? 63 : 64;
                w.set(from, 63, Material.WATER); w.set(from, 64, Material.STONE);
                w.set(from + (reverse ? 1 : -1), 64, Material.STONE);
                s.step(); assertEquals(Material.WATER, w.get(to, 64));
                w.clear(); w.set(from, 63, Material.SAND); w.set(from, 64, Material.WATER);
                w.set(from - 1, 64, Material.STONE); w.set(from + 1, 64, Material.STONE);
                w.set(from - 1, 65, Material.STONE); w.set(from, 65, Material.STONE); w.set(from + 1, 65, Material.STONE);
                s.step(); assertEquals(Material.SAND, w.get(from, 64)); assertEquals(Material.WATER, w.get(from, 63));
            }
        }
    }
}

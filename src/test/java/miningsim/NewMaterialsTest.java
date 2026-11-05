package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class NewMaterialsTest {
    @Test void gravelFallsAndDisplacesWater() {
        World w = new World(1, 3); w.set(0, 0, Material.GRAVEL); w.set(0, 1, Material.WATER); w.set(0, 2, Material.STONE);
        new Simulation(w, 1).step();
        assertEquals(Material.WATER, w.get(0, 0)); assertEquals(Material.GRAVEL, w.get(0, 1));
    }
    @Test void gravelDiagonalRequiresEmptySideButSandDoesNot() {
        for (Material m : new Material[]{Material.SAND, Material.GRAVEL}) {
            World w = new World(3, 2);
            w.set(1, 0, m); w.set(0, 0, Material.STONE); w.set(2, 0, Material.STONE);
            w.set(0, 1, Material.STONE); w.set(1, 1, Material.STONE);
            new Simulation(w, 1).step();
            assertEquals(m, w.get(m == Material.SAND ? 2 : 1, m == Material.SAND ? 1 : 0));
        }
        World w = new World(3, 2); w.set(1, 0, Material.GRAVEL); w.set(1, 1, Material.STONE); w.set(0, 1, Material.STONE);
        new Simulation(w, 1).step(); assertEquals(Material.GRAVEL, w.get(2, 1));
    }
    @Test void restrictedFixtureLeavesGravelPileSteeperThanSand() {
        int[] heights = new int[2]; int i = 0;
        for (Material m : new Material[]{Material.SAND, Material.GRAVEL}) {
            World w = new World(3, 3);
            for (int x = 0; x < 3; x++) w.set(x, 2, Material.STONE);
            w.set(1, 1, m); w.set(1, 0, m); w.set(0, 0, Material.STONE); w.set(2, 0, Material.STONE);
            new Simulation(w, 2).step();
            for (int y = 0; y < 2; y++) if (w.get(1, y) == m) heights[i]++;
            i++;
        }
        assertEquals(1, heights[0]); assertEquals(2, heights[1]);
    }
    @Test void soilSupportRemovalAndNoFloatingCycles() {
        World w = new World(1, 6); w.set(0, 2, Material.SOIL); w.set(0, 3, Material.SOIL); w.set(0, 4, Material.STONE);
        Simulation s = new Simulation(w, 4); for (int i = 0; i < 5; i++) s.step();
        assertEquals(Material.SOIL, w.get(0, 2)); assertEquals(Material.SOIL, w.get(0, 3));
        w.set(0, 4, Material.AIR); for (int i = 0; i < 5; i++) s.step();
        assertEquals(Material.SOIL, w.get(0, 4)); assertEquals(Material.SOIL, w.get(0, 5));
    }
    @Test void soilSinksAndStoneWoodBlockWater() {
        World w = new World(1, 3); w.set(0, 0, Material.SOIL); w.set(0, 1, Material.WATER); w.set(0, 2, Material.WOOD);
        new Simulation(w, 1).step(); assertEquals(Material.WATER, w.get(0, 0)); assertEquals(Material.SOIL, w.get(0, 1));
        for (Material support : new Material[]{Material.STONE, Material.WOOD, Material.SOIL}) {
            World a = new World(1, 2); a.set(0, 0, Material.WATER); a.set(0, 1, support);
            new Simulation(a, 1).step(); assertEquals(Material.WATER, a.get(0, 0));
        }
    }
    @Test void activeRegionsWakeNewMaterialsAcrossBoundary() {
        World w = new World(1, 66); w.set(0, 31, Material.SOIL); w.set(0, 32, Material.STONE);
        Simulation s = new Simulation(w, 5, Simulation.Mode.ACTIVE_REGIONS, false);
        for (int i = 0; i < 5; i++) s.step(); w.set(0, 32, Material.AIR); s.step();
        assertEquals(Material.SOIL, w.get(0, 32));
        w.set(0, 0, Material.GRAVEL); for (int i = 0; i < 33; i++) s.step();
        assertTrue(w.get(0, 33) == Material.GRAVEL || w.get(0, 32) == Material.GRAVEL);
    }
    @Test void mixedMaterialsConservedAndRepeatableInBothModes() {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World a = new World(35, 35), b = new World(35, 35); Random random = new Random(22);
            int[] counts = new int[7];
            for (int y = 0; y < 35; y++) for (int x = 0; x < 35; x++) {
                Material m = Material.fromId((byte) random.nextInt(7)); a.set(x, y, m); b.set(x, y, m); counts[m.id()]++;
            }
            Simulation sa = new Simulation(a, 2, mode, false), sb = new Simulation(b, 2, mode, false);
            for (int i = 0; i < 150; i++) {
                sa.step(); sb.step(); int[] actual = new int[7];
                for (int y = 0; y < 35; y++) for (int x = 0; x < 35; x++) {
                    Material m = a.get(x, y); assertEquals(m, b.get(x, y)); actual[m.id()]++;
                }
                assertArrayEquals(counts, actual);
            }
        }
    }
}

package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import miningsim.app.TickMetrics;
import miningsim.app.SimulationControl;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class SimulationInvariantsTest {
    private static int[] counts(World w) {
        int[] counts = new int[Material.values().length];
        for (int y = 0; y < w.height(); y++) for (int x = 0; x < w.width(); x++) counts[w.get(x, y).ordinal()]++;
        return counts;
    }
    private static void same(World a, World b) {
        for (int y = 0; y < a.height(); y++) for (int x = 0; x < a.width(); x++) assertEquals(a.get(x, y), b.get(x, y), x + "," + y);
    }

    @Test void allCellsInitializeAndClearAndBoundsAreChecked() {
        World w = new World(3, 2);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 3; x++) {
            assertEquals(Material.AIR, w.get(x, y));
            w.set(x, y, Material.values()[1 + (y * 3 + x) % 4]);
        }
        assertEquals(Material.SAND, w.get(1, 0));
        assertEquals(Material.AIR, new World(1, 1).get(0, 0));
        assertFalse(w.contains(-1, 0)); assertFalse(w.contains(0, -1));
        assertFalse(w.contains(3, 0)); assertFalse(w.contains(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> w.get(0, -1));
        assertThrows(IndexOutOfBoundsException.class, () -> w.set(0, 2, Material.AIR));
        assertThrows(NullPointerException.class, () -> w.set(0, 0, null));
        w.clear();
        assertArrayEquals(new int[]{6, 0, 0, 0, 0, 0, 0}, counts(w));
    }

    @Test void waterFallsAndSandDoesNotMoveTwiceThroughAirOrWater() {
        World water = new World(1, 4); water.set(0, 0, Material.WATER);
        new Simulation(water, 3).step();
        assertEquals(Material.WATER, water.get(0, 1));
        World sand = new World(1, 4); sand.set(0, 0, Material.SAND);
        sand.set(0, 1, Material.WATER); sand.set(0, 2, Material.WATER);
        sand.set(0, 3, Material.STONE);
        new Simulation(sand, 3).step();
        assertEquals(Material.WATER, sand.get(0, 0));
        assertEquals(Material.SAND, sand.get(0, 1));
        assertEquals(Material.WATER, sand.get(0, 2));
    }

    @Test void sandCannotDisplaceWaterThatAlreadyMovedThisTick() {
        World w = new World(3, 3);
        w.set(0, 0, Material.SAND); w.set(1, 1, Material.WATER);
        w.set(0, 2, Material.STONE); w.set(1, 2, Material.STONE); w.set(2, 2, Material.STONE); w.set(2, 1, Material.STONE);
        new Simulation(w, 5).step();
        assertEquals(Material.WATER, w.get(0, 1));
        assertEquals(Material.SAND, w.get(1, 1));
    }

    @Test void sandDisplacesWaterDiagonallyWhenVerticalPathBlocked() {
        World w = new World(2, 2);
        w.set(0, 0, Material.SAND); w.set(0, 1, Material.STONE); w.set(1, 1, Material.WATER);
        new Simulation(w, 14).step();
        assertEquals(Material.WATER, w.get(0, 0));
        assertEquals(Material.SAND, w.get(1, 1));
        assertEquals(Material.STONE, w.get(0, 1));
    }

    @Test void timingOnlyRecordsActualTicksIncludingSingleStep() {
        World w = new World(1, 2);
        SimulationControl control = new SimulationControl(new Simulation(w, 2));
        assertFalse(control.singleStep());
        control.togglePause();
        assertFalse(control.tick());
        assertEquals(0, control.metrics().averageMillis());
        assertTrue(control.singleStep());
        assertTrue(control.metrics().lastMillis() >= 0);
        control.togglePause();
        assertTrue(control.tick());
    }

    @Test void cornersBottomAndFullWorldAreSafe() {
        for (int x = 0; x < 2; x++) {
            World w = new World(2, 2);
            w.set(x, 1, Material.SAND);
            new Simulation(w, 4).step();
            assertEquals(Material.SAND, w.get(x, 1));
        }
        World full = new World(3, 3);
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) full.set(x, y, Material.STONE);
        full.set(0, 0, Material.SAND); full.set(2, 2, Material.WATER); full.set(1, 1, Material.WOOD);
        int[] before = counts(full);
        new Simulation(full, 7).step();
        assertArrayEquals(before, counts(full));
        assertEquals(Material.SAND, full.get(0, 0)); assertEquals(Material.WATER, full.get(2, 2));
        assertEquals(Material.WOOD, full.get(1, 1));
    }

    @Test void closedMixedWorldConservesEveryMaterialAndStaticCells() {
        World w = new World(23, 17); Random random = new Random(19);
        for (int y = 0; y < w.height(); y++) for (int x = 0; x < w.width(); x++) w.set(x, y, Material.values()[random.nextInt(5)]);
        boolean[][] stone = new boolean[w.height()][w.width()], wood = new boolean[w.height()][w.width()];
        for (int y = 0; y < w.height(); y++) for (int x = 0; x < w.width(); x++) {
            stone[y][x] = w.get(x, y) == Material.STONE; wood[y][x] = w.get(x, y) == Material.WOOD;
        }
        int[] initial = counts(w); Simulation sim = new Simulation(w, 99);
        for (int i = 0; i < 500; i++) {
            sim.step(); assertArrayEquals(initial, counts(w));
            for (int y = 0; y < w.height(); y++) for (int x = 0; x < w.width(); x++) {
                if (stone[y][x]) assertEquals(Material.STONE, w.get(x, y));
                if (wood[y][x]) assertEquals(Material.WOOD, w.get(x, y));
            }
        }
    }

    @Test void deterministicWithIdenticalExternalActions() {
        World a = new World(8, 7), b = new World(8, 7);
        Simulation sa = new Simulation(a, 123), sb = new Simulation(b, 123);
        for (int tick = 0; tick < 150; tick++) {
            if (tick % 7 == 0) {
                int x = tick % 8; Material m = tick % 2 == 0 ? Material.SAND : Material.WATER;
                a.set(x, 0, m); b.set(x, 0, m);
            }
            if (tick == 80) { a.clear(); b.clear(); }
            sa.step(); sb.step(); same(a, b);
        }
    }

    @Test void metricsUseBoundedRollingWindow() {
        assertThrows(IllegalArgumentException.class, () -> new TickMetrics(0));
        TickMetrics m = new TickMetrics(2);
        assertEquals(0, m.maxMillis());
        m.record(1_000_000); m.record(4_000_000);
        assertEquals(4, m.lastMillis()); assertEquals(2.5, m.averageMillis()); assertEquals(4, m.maxMillis());
        m.record(2_000_000); m.record(3_000_000);
        assertEquals(3, m.lastMillis()); assertEquals(2.5, m.averageMillis()); assertEquals(3, m.maxMillis());
        assertThrows(IllegalArgumentException.class, () -> m.record(-1));
    }
}

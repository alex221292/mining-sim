package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class WaterFlowTest {
    private static int count(World world, Material material) {
        int result = 0;
        for (int y = 0; y < world.height(); y++) for (int x = 0; x < world.width(); x++)
            if (world.get(x, y) == material) result++;
        return result;
    }

    private static World basin() {
        World world = new World(42, 22);
        for (int y = 0; y < 22; y++) { world.set(0, y, Material.STONE); world.set(41, y, Material.STONE); }
        for (int x = 0; x < 42; x++) world.set(x, 21, Material.STONE);
        for (int x = 1; x <= 20; x++) for (int y = 5; y <= 20; y++) world.set(x, y, Material.WATER);
        return world;
    }

    private static int levelDifference(World world) {
        int min = Integer.MAX_VALUE, max = 0;
        for (int x = 1; x <= 40; x++) {
            int height = 0;
            for (int y = 0; y < 21; y++) if (world.get(x, y) == Material.WATER) height++;
            min = Math.min(min, height); max = Math.max(max, height);
        }
        return max - min;
    }

    @Test void fallsVerticallyBeforeDiagonal() {
        World w = new World(3, 3); w.set(1, 0, Material.WATER);
        new Simulation(w, 42).step();
        assertEquals(Material.WATER, w.get(1, 1));
        assertEquals(1, count(w, Material.WATER));
    }

    @Test void flowsDiagonallyBeforeSideways() {
        World w = new World(3, 3); w.set(1, 0, Material.WATER);
        w.set(1, 1, Material.STONE); w.set(0, 1, Material.STONE);
        new Simulation(w, 42).step();
        assertEquals(Material.WATER, w.get(2, 1));
        assertEquals(Material.AIR, w.get(0, 0));
    }

    @Test void spreadsAcrossFlatStone() {
        World w = new World(13, 4);
        for (int x = 0; x < 13; x++) w.set(x, 3, Material.STONE);
        for (int y = 0; y < 3; y++) w.set(6, y, Material.WATER);
        Simulation sim = new Simulation(w, 9);
        for (int i = 0; i < 20; i++) sim.step();
        int bottom = 0;
        for (int x = 0; x < 13; x++) if (w.get(x, 2) == Material.WATER) bottom++;
        assertEquals(3, bottom);
        assertEquals(3, count(w, Material.WATER));
    }

    @Test void boundedHorizontalMoveReachesDropWithoutMovingTwice() {
        World w = new World(11, 4);
        for (int x = 0; x < 11; x++) w.set(x, 3, Material.STONE);
        for (int x = 0; x <= 8; x++) w.set(x, 2, Material.STONE);
        w.set(0, 1, Material.STONE); w.set(1, 1, Material.WATER); w.set(1, 2, Material.WATER);
        new Simulation(w, 42).step();
        assertEquals(Material.WATER, w.get(9, 1));
        assertEquals(Material.AIR, w.get(9, 2));
        assertEquals(2, count(w, Material.WATER));
    }

    @Test void wallsAndNarrowPassagesAreNotCrossed() {
        World w = new World(5, 4);
        for (int y = 0; y < 4; y++) { w.set(0, y, Material.STONE); w.set(2, y, Material.STONE); w.set(4, y, Material.STONE); }
        w.set(1, 0, Material.WATER);
        Simulation sim = new Simulation(w, 1);
        for (int i = 0; i < 20; i++) sim.step();
        assertEquals(Material.WATER, w.get(1, 3));
        assertEquals(Material.AIR, w.get(3, 3));
        assertEquals(1, count(w, Material.WATER));
    }

    @Test void basinLevelsAndRemainsLevelWithSeed42() {
        World w = basin(); Simulation sim = new Simulation(w, 42);
        assertEquals(16, levelDifference(w));
        for (int i = 0; i < 100; i++) sim.step();
        assertTrue(levelDifference(w) <= 1, "column water-count spread after 100 ticks: " + levelDifference(w));
        for (int i = 0; i < 200; i++) {
            sim.step();
            assertTrue(levelDifference(w) <= 1, "surface developed a crest at tick " + (101 + i));
        }
        assertEquals(320, count(w, Material.WATER));
    }

    @Test void sandAndWaterConservedAndRepeatable() {
        World a = basin(), b = basin();
        for (int x = 5; x < 15; x++) { a.set(x, 3, Material.SAND); b.set(x, 3, Material.SAND); }
        Simulation sa = new Simulation(a, 123), sb = new Simulation(b, 123);
        for (int i = 0; i < 200; i++) {
            sa.step(); sb.step();
            assertEquals(320, count(a, Material.WATER)); assertEquals(10, count(a, Material.SAND));
            for (int y = 0; y < a.height(); y++) for (int x = 0; x < a.width(); x++) assertEquals(a.get(x, y), b.get(x, y));
        }
    }
}

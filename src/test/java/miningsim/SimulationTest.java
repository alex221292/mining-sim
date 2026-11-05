package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import miningsim.app.SimulationControl;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class SimulationTest {
    @Test void indexingAndBounds() {
        assertThrows(IllegalArgumentException.class, () -> new World(0, 1));
        World w = new World(2, 3);
        w.set(1, 1, Material.WOOD);
        assertEquals(Material.WOOD, w.get(1, 1));
        assertEquals(Material.AIR, w.get(0, 2));
        assertThrows(IndexOutOfBoundsException.class, () -> w.get(2, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> w.set(-1, 1, Material.AIR));
        w.clear(); assertEquals(0, w.occupiedCount());
    }
    @Test void fallingAndSingleMovement() {
        World w = new World(1, 4); w.set(0, 0, Material.SAND);
        new Simulation(w, 1).step();
        assertEquals(Material.SAND, w.get(0, 1));
        assertEquals(1, w.occupiedCount());
    }
    @Test void sandFallsDiagonallyAndRespectsEdges() {
        World w = new World(2, 2); w.set(0, 0, Material.SAND); w.set(0, 1, Material.STONE);
        new Simulation(w, 2).step();
        assertEquals(Material.SAND, w.get(1, 1));
    }
    @Test void waterFlowsSidewaysOnlyOnce() {
        World w = new World(5, 1); w.set(2, 0, Material.WATER);
        new Simulation(w, 3).step();
        assertTrue(w.get(1, 0) == Material.WATER || w.get(3, 0) == Material.WATER);
        assertEquals(1, w.occupiedCount());
    }
    @Test void sandSinksThroughWaterWithoutLosingEitherParticle() {
        World w = new World(1, 3);
        w.set(0, 0, Material.SAND); w.set(0, 1, Material.WATER); w.set(0, 2, Material.WATER);
        Simulation simulation = new Simulation(w, 1);
        simulation.step();
        assertEquals(Material.WATER, w.get(0, 0));
        assertEquals(Material.SAND, w.get(0, 1));
        assertEquals(Material.WATER, w.get(0, 2));
        simulation.step();
        assertEquals(Material.SAND, w.get(0, 2));
        assertEquals(3, w.occupiedCount());
    }
    @Test void blockedAndStaticMaterialsStay() {
        World w = new World(1, 3); w.set(0, 0, Material.SAND); w.set(0, 1, Material.WOOD); w.set(0, 2, Material.STONE);
        new Simulation(w, 4).step();
        assertEquals(Material.SAND, w.get(0, 0)); assertEquals(Material.WOOD, w.get(0, 1)); assertEquals(Material.STONE, w.get(0, 2));
    }
    @Test void deterministicAndConservesMaterials() {
        World a = new World(8, 8), b = new World(8, 8);
        for (int x = 0; x < 8; x++) { a.set(x, 0, Material.SAND); b.set(x, 0, Material.SAND); a.set(x, 1, Material.WATER); b.set(x, 1, Material.WATER); }
        Simulation sa = new Simulation(a, 42), sb = new Simulation(b, 42);
        for (int i = 0; i < 100; i++) { sa.step(); sb.step(); }
        int sand = 0, water = 0;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            assertEquals(a.get(x, y), b.get(x, y));
            if (a.get(x, y) == Material.SAND) sand++;
            if (a.get(x, y) == Material.WATER) water++;
        }
        assertEquals(8, sand); assertEquals(8, water);
    }
    @Test void pauseAndStep() {
        World w = new World(1, 4); w.set(0, 0, Material.SAND);
        SimulationControl c = new SimulationControl(new Simulation(w, 1));
        assertFalse(c.singleStep()); c.togglePause(); assertFalse(c.tick());
        assertEquals(Material.SAND, w.get(0, 0)); assertTrue(c.singleStep());
        assertEquals(Material.SAND, w.get(0, 1)); c.togglePause(); assertTrue(c.tick());
        assertEquals(Material.SAND, w.get(0, 2));
    }
}

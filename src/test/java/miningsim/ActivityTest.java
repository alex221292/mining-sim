package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class ActivityTest {
    @Test void paintingAfterSleepAndCrossingTileBoundary() {
        World w = new World(65, 65); Simulation s = new Simulation(w, 4);
        for (int i = 0; i < 4; i++) s.step();
        w.set(31, 31, Material.SAND);
        s.step(); assertEquals(Material.SAND, w.get(31, 32));
        s.step(); assertEquals(Material.SAND, w.get(31, 33));
    }

    @Test void erasingSupportWakesSleepingSandAcrossTileBoundary() {
        World w = new World(1, 65);
        w.set(0, 31, Material.SAND); w.set(0, 32, Material.STONE);
        Simulation s = new Simulation(w, 7);
        for (int i = 0; i < 5; i++) s.step();
        assertEquals(Material.SAND, w.get(0, 31));
        w.set(0, 32, Material.AIR);
        s.step(); assertEquals(Material.SAND, w.get(0, 32));
    }

    @Test void waterEntersSleepingNeighborAndClearReactivates() {
        World w = new World(65, 3); Simulation s = new Simulation(w, 5);
        for (int i = 0; i < 3; i++) s.step();
        w.set(31, 0, Material.WATER); w.set(31, 1, Material.STONE); w.set(30, 1, Material.STONE);
        s.step(); assertEquals(Material.WATER, w.get(32, 1));
        w.clear(); s.step(); assertEquals(0, w.occupiedCount());
        w.set(64, 0, Material.WATER); s.step(); assertEquals(Material.WATER, w.get(64, 1));
    }
}

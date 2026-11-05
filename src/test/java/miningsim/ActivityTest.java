package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class ActivityTest {
    @Test void paintingAfterSleepAndCrossingTileBoundary() {
        World w = new World(65, 65); Simulation s = new Simulation(w, 4, Simulation.Mode.ACTIVE_REGIONS, false);
        for (int i = 0; i < 4; i++) s.step();
        w.set(31, 31, Material.SAND);
        s.step(); assertEquals(Material.SAND, w.get(31, 32));
        s.step(); assertEquals(Material.SAND, w.get(31, 33));
    }

    @Test void erasingSupportWakesSleepingSandAcrossTileBoundary() {
        World w = new World(1, 65);
        w.set(0, 31, Material.SAND); w.set(0, 32, Material.STONE);
        Simulation s = new Simulation(w, 7, Simulation.Mode.ACTIVE_REGIONS, false);
        for (int i = 0; i < 5; i++) s.step();
        assertEquals(Material.SAND, w.get(0, 31));
        w.set(0, 32, Material.AIR);
        s.step(); assertEquals(Material.SAND, w.get(0, 32));
    }

    @Test void waterEntersSleepingNeighborAndClearReactivates() {
        World w = new World(65, 3); Simulation s = new Simulation(w, 5, Simulation.Mode.ACTIVE_REGIONS, false);
        for (int i = 0; i < 3; i++) s.step();
        w.set(31, 0, Material.WATER); w.set(31, 1, Material.STONE); w.set(30, 1, Material.STONE);
        s.step(); assertEquals(Material.WATER, w.get(32, 1));
        w.clear(); s.step(); assertEquals(0, w.occupiedCount());
        w.set(64, 0, Material.WATER); s.step(); assertEquals(Material.WATER, w.get(64, 1));
    }

    @Test void longWaterMoveAcrossTileBoundaryDoesNotMoveTwice() {
        World w = new World(65, 4);
        for (int x = 0; x < 65; x++) w.set(x, 3, Material.STONE);
        for (int x = 0; x <= 36; x++) w.set(x, 2, Material.STONE);
        w.set(28, 1, Material.STONE); w.set(29, 1, Material.WATER); w.set(29, 2, Material.WATER);
        Simulation s = new Simulation(w, 42, Simulation.Mode.ACTIVE_REGIONS, false);
        s.step();
        assertEquals(Material.WATER, w.get(37, 1));
        assertEquals(Material.AIR, w.get(37, 2));
    }

    @Test void repeatedlyWakesAfterPaintingAndErasing() {
        World w = new World(1, 65); Simulation s = new Simulation(w, 7, Simulation.Mode.ACTIVE_REGIONS, true);
        for (int cycle = 0; cycle < 8; cycle++) {
            w.clear(); w.set(0, 31, Material.SAND); w.set(0, 32, Material.STONE);
            for (int i = 0; i < 4; i++) s.step();
            assertEquals(0, s.activeTiles());
            w.set(0, 32, Material.AIR); s.step();
            assertEquals(Material.SAND, w.get(0, 32));
        }
    }

    @Test void modesAgreeForVerticalFallAndExternalChanges() {
        World a = new World(1, 70), b = new World(1, 70);
        Simulation full = new Simulation(a, 99, Simulation.Mode.FULL_SCAN, false);
        Simulation active = new Simulation(b, 99, Simulation.Mode.ACTIVE_REGIONS, false);
        for (int tick = 0; tick < 200; tick++) {
            if (tick == 3 || tick == 110) { a.set(0, 0, Material.SAND); b.set(0, 0, Material.SAND); }
            if (tick == 100) { a.clear(); b.clear(); }
            full.step(); active.step();
            for (int y = 0; y < 70; y++) assertEquals(a.get(0, y), b.get(0, y));
        }
    }

    @Test void deterministicConservationAcrossRegionsForBothModes() {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World a = new World(70, 40), b = new World(70, 40); Random random = new Random(22);
            int sand = 0, water = 0;
            for (int y = 0; y < 40; y++) for (int x = 0; x < 70; x++) {
                int n = random.nextInt(10); Material m = n < 2 ? Material.SAND : n < 4 ? Material.WATER : n == 4 ? Material.STONE : Material.AIR;
                a.set(x, y, m); b.set(x, y, m);
                if (m == Material.SAND) sand++; if (m == Material.WATER) water++;
            }
            Simulation sa = new Simulation(a, 17, mode, false), sb = new Simulation(b, 17, mode, false);
            for (int tick = 0; tick < 400; tick++) {
                sa.step(); sb.step(); int s = 0, w = 0;
                for (int y = 0; y < 40; y++) for (int x = 0; x < 70; x++) {
                    Material m = a.get(x, y); assertEquals(m, b.get(x, y));
                    if (m == Material.SAND) s++; if (m == Material.WATER) w++;
                }
                assertEquals(sand, s); assertEquals(water, w);
            }
        }
    }

    @Test void basinLevelsInBothModesAcrossSeveralTiles() {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World w = new World(42, 22);
            for (int y = 0; y < 22; y++) { w.set(0, y, Material.STONE); w.set(41, y, Material.STONE); }
            for (int x = 0; x < 42; x++) w.set(x, 21, Material.STONE);
            for (int x = 1; x <= 20; x++) for (int y = 5; y <= 20; y++) w.set(x, y, Material.WATER);
            Simulation sim = new Simulation(w, 42, mode, false);
            for (int i = 0; i < 300; i++) sim.step();
            int min = 99, max = 0, total = 0;
            for (int x = 1; x <= 40; x++) {
                int height = 0;
                for (int y = 0; y < 21; y++) if (w.get(x, y) == Material.WATER) height++;
                total += height; min = Math.min(min, height); max = Math.max(max, height);
            }
            assertEquals(320, total); assertTrue(max - min <= 1, mode + " level difference=" + (max - min));
        }
    }
}

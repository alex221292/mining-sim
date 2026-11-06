package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import miningsim.simulation.Simulation;
import miningsim.world.Material;
import miningsim.world.World;

class CrossChunkActivityTest {
    private static void same(World a, World b) {
        for (int y = 0; y < a.height(); y++) for (int x = 0; x < a.width(); x++)
            assertEquals(a.get(x, y), b.get(x, y), "cell " + x + "," + y);
    }

    @Test void editsWakeChangedTileAndAllValidNeighborsAtBothGridBoundaries() {
        World w = new World(130, 130); w.setActivityTracking(true);
        boolean[] active = new boolean[w.activityTileColumns() * w.activityTileRows()];
        w.collectActivity(active); java.util.Arrays.fill(active, false);
        for (int coordinate : new int[]{0, 31, 32, 63, 64, 129}) {
            for (int y : new int[]{0, 31, 32, 63, 64, 129}) {
                w.set(coordinate, y, Material.STONE); w.collectActivity(active);
                int tx = coordinate / 32, ty = y / 32;
                for (int row = 0; row < w.activityTileRows(); row++) for (int col = 0; col < w.activityTileColumns(); col++)
                    assertEquals(Math.abs(col - tx) <= 1 && Math.abs(row - ty) <= 1,
                            active[row * w.activityTileColumns() + col], "edit " + coordinate + "," + y);
                java.util.Arrays.fill(active, false);
            }
        }
        w.clear(); w.collectActivity(active);
        for (boolean tile : active) assertTrue(tile);
    }

    @Test void longWaterSearchCrossesChunkEdgeAndFallsOnFollowingTick() {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World w = new World(130, 66);
            for (int x = 55; x <= 68; x++) w.set(x, 65, Material.STONE);
            for (int x = 55; x <= 63; x++) w.set(x, 64, Material.STONE);
            w.set(56, 63, Material.STONE); w.set(57, 63, Material.WATER); w.set(57, 64, Material.WATER);
            Simulation s = new Simulation(w, 42, mode, false); s.step();
            assertEquals(Material.WATER, w.get(64, 63)); assertEquals(Material.AIR, w.get(64, 64));
            s.step(); assertEquals(Material.WATER, w.get(64, 64));
        }
    }

    @Test void fallingAndDisplacementAtFourChunkIntersection() {
        for (Simulation.Mode mode : new Simulation.Mode[]{Simulation.Mode.FULL_SCAN, Simulation.Mode.ACTIVE_REGIONS}) {
            World w = new World(66, 66);
            w.set(63, 63, Material.SAND); w.set(63, 64, Material.STONE); w.set(62, 64, Material.STONE);
            w.set(64, 64, Material.WATER); w.set(64, 65, Material.STONE); w.set(63, 65, Material.STONE); w.set(65, 65, Material.STONE);
            w.set(65, 64, Material.STONE);
            new Simulation(w, 42, mode, false).step();
            assertEquals(Material.SAND, w.get(64, 64)); assertEquals(Material.WATER, w.get(63, 63));
        }
    }

    @Test void waterAndSandTravelAcrossMultipleChunksWithEquivalentForcedPaths() {
        for (Material material : new Material[]{Material.WATER, Material.SAND}) {
            World a = new World(130, 130), b = new World(130, 130);
            for (int y = 0; y < 130; y++) for (int x = 0; x < 130; x++) {
                a.set(x, y, Material.STONE); b.set(x, y, Material.STONE);
            }
            for (int i = 0; i < 130; i++) { a.set(i, i, Material.AIR); b.set(i, i, Material.AIR); }
            a.set(0, 0, material); b.set(0, 0, material);
            Simulation full = new Simulation(a, 123, Simulation.Mode.FULL_SCAN, false);
            Simulation active = new Simulation(b, 123, Simulation.Mode.ACTIVE_REGIONS, false);
            for (int tick = 0; tick < 129; tick++) {
                full.step(); active.step();
                assertEquals(material, a.get(tick + 1, tick + 1)); same(a, b);
            }
        }
    }

    @Test void distantDisturbancesWakeSettleAndReactivateAcrossBoundaries() {
        World a = new World(130, 131), b = new World(130, 131);
        Simulation full = new Simulation(a, 9, Simulation.Mode.FULL_SCAN, false);
        Simulation active = new Simulation(b, 9, Simulation.Mode.ACTIVE_REGIONS, true);
        for (int i = 0; i < 3; i++) { full.step(); active.step(); }
        assertEquals(0, active.activeTiles());
        for (int x : new int[]{0, 63, 64, 129}) { a.set(x, 63, Material.SAND); b.set(x, 63, Material.SAND); }
        for (int tick = 0; tick < 68; tick++) { full.step(); active.step(); same(a, b); }
        for (int i = 0; i < 4; i++) active.step();
        assertEquals(0, active.activeTiles()); assertEquals(0, active.movements());
        b.set(63, 130, Material.AIR); b.set(63, 129, Material.STONE); active.step();
        assertTrue(active.activeTiles() > 0);
        b.clear(); active.step(); assertEquals(0, b.occupiedCount());
        for (int i = 0; i < 3; i++) active.step(); assertEquals(0, active.activeTiles());
        b.set(129, 0, Material.SAND); active.step(); assertEquals(Material.SAND, b.get(129, 1));
    }

    @Test void verticalSandCrossesSeveralChunksAndMatchesEachTick() {
        World a = new World(1, 200), b = new World(1, 200);
        a.set(0, 0, Material.SAND); b.set(0, 0, Material.SAND);
        Simulation full = new Simulation(a, 11, Simulation.Mode.FULL_SCAN, false);
        Simulation active = new Simulation(b, 11, Simulation.Mode.ACTIVE_REGIONS, false);
        for (int tick = 0; tick < 199; tick++) {
            full.step(); active.step(); same(a, b); assertEquals(Material.SAND, b.get(0, tick + 1));
        }
    }

    @Test void supportRemovalReactivatesSettledSandOnChunkBoundaryInBothModes() {
        World a = new World(66, 68), b = new World(66, 68);
        for (World w : new World[]{a, b}) {
            w.set(63, 63, Material.SAND);
            for (int x = 62; x <= 64; x++) w.set(x, 64, Material.STONE);
        }
        Simulation full = new Simulation(a, 9, Simulation.Mode.FULL_SCAN, false);
        Simulation active = new Simulation(b, 9, Simulation.Mode.ACTIVE_REGIONS, true);
        for (int i = 0; i < 4; i++) { full.step(); active.step(); same(a, b); }
        assertEquals(0, active.activeTiles());
        a.set(63, 64, Material.AIR); b.set(63, 64, Material.AIR);
        for (int i = 0; i < 4; i++) { full.step(); active.step(); same(a, b); }
        assertEquals(Material.SAND, b.get(63, 67));
    }
}

package miningsim.simulation;

import java.util.Arrays;
import java.util.Random;
import miningsim.world.Material;
import miningsim.world.World;

public final class Simulation {
    private static final int WATER_REACH = 8;
    private final World world;
    private final Random random;
    private final boolean[] moved;
    private final boolean[] active;
    private final int tileColumns;

    public Simulation(World world, long seed) {
        this.world = world;
        random = new Random(seed);
        moved = new boolean[world.width() * world.height()];
        tileColumns = world.activityTileColumns();
        active = new boolean[tileColumns * world.activityTileRows()];
    }

    public void step() {
        world.collectActivity(active);
        Arrays.fill(moved, false);
        boolean reverse = random.nextBoolean();
        for (int y = world.height() - 1; y >= 0; y--) {
            for (int column = 0; column < tileColumns; column++) {
                int tileX = reverse ? tileColumns - 1 - column : column;
                if (!active[(y / World.ACTIVITY_TILE_SIZE) * tileColumns + tileX]) continue;
                int start = tileX * World.ACTIVITY_TILE_SIZE;
                int end = Math.min(world.width(), start + World.ACTIVITY_TILE_SIZE);
                for (int i = start; i < end; i++) {
                int x = reverse ? end - 1 - (i - start) : i;
                if (moved[y * world.width() + x]) continue;
                Material material = world.get(x, y);
                if (material != Material.SAND && material != Material.WATER) continue;
                if (move(x, y, x, y + 1, material)) continue;
                int direction = random.nextBoolean() ? 1 : -1;
                if (move(x, y, x + direction, y + 1, material)
                        || move(x, y, x - direction, y + 1, material)) continue;
                if (material == Material.WATER) flowSideways(x, y, direction);
                }
            }
        }
        Arrays.fill(active, false);
    }

    private void flowSideways(int x, int y, int direction) {
        int first = distanceToDrop(x, y, direction);
        int second = distanceToDrop(x, y, -direction);
        if (first > 0 || second > 0) {
            int offset = first > 0 && (second == 0 || first <= second) ? direction * first : -direction * second;
            move(x, y, x + offset, y, Material.WATER);
            return;
        }
        // When the drop is beyond the search horizon, travel toward the longer
        // open run. Water cannot cross other materials or an occupied cell.
        if (world.contains(x, y + 1) && world.get(x, y + 1) == Material.WATER) {
            int forward = openRun(x, y, direction), backward = openRun(x, y, -direction);
            if (forward > 0 || backward > 0) {
                int offset = forward >= backward ? direction * forward : -direction * backward;
                move(x, y, x + offset, y, Material.WATER);
            }
            return;
        }
        if (!move(x, y, x + direction, y, Material.WATER))
            move(x, y, x - direction, y, Material.WATER);
    }

    private int openRun(int x, int y, int direction) {
        int distance = 0;
        while (distance < WATER_REACH) {
            int nx = x + direction * (distance + 1);
            if (!world.contains(nx, y) || world.get(nx, y) != Material.AIR) break;
            distance++;
        }
        return distance;
    }

    // Only cross contiguous air; move at most once, to the first reachable drop.
    private int distanceToDrop(int x, int y, int direction) {
        for (int distance = 1; distance <= WATER_REACH; distance++) {
            int nx = x + direction * distance;
            if (!world.contains(nx, y) || world.get(nx, y) != Material.AIR) break;
            if (world.contains(nx, y + 1) && world.get(nx, y + 1) == Material.AIR) return distance;
        }
        return 0;
    }

    private boolean move(int x, int y, int nx, int ny, Material material) {
        if (!world.contains(nx, ny)) return false;
        Material target = world.get(nx, ny);
        // Sand displaces water, preserving both particles; other moves require air.
        if (target != Material.AIR && !(material == Material.SAND && target == Material.WATER)) return false;
        if (target == Material.WATER && moved[ny * world.width() + nx]) return false;
        world.set(nx, ny, material);
        world.set(x, y, target);
        moved[ny * world.width() + nx] = true;
        if (target == Material.WATER) moved[y * world.width() + x] = true;
        return true;
    }
}

package miningsim.simulation;

import java.util.Arrays;
import java.util.Random;
import miningsim.world.Material;
import miningsim.world.World;

public final class Simulation {
    private final World world;
    private final Random random;
    private final boolean[] moved;

    public Simulation(World world, long seed) {
        this.world = world;
        random = new Random(seed);
        moved = new boolean[world.width() * world.height()];
    }

    public void step() {
        Arrays.fill(moved, false);
        boolean reverse = random.nextBoolean();
        for (int y = world.height() - 1; y >= 0; y--) {
            for (int i = 0; i < world.width(); i++) {
                int x = reverse ? world.width() - 1 - i : i;
                if (moved[y * world.width() + x]) continue;
                Material material = world.get(x, y);
                if (material != Material.SAND && material != Material.WATER) continue;
                if (move(x, y, x, y + 1, material)) continue;
                int direction = random.nextBoolean() ? 1 : -1;
                int targetY = material == Material.SAND ? y + 1 : y;
                if (!move(x, y, x + direction, targetY, material))
                    move(x, y, x - direction, targetY, material);
            }
        }
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

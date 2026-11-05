package miningsim.app;

import miningsim.world.Material;
import miningsim.world.World;

public final class Brush {
    private Brush() { }

    /** Ignore a pointer outside the world, even if the brush would overlap it. */
    public static void paint(World world, Camera2D camera, double screenX, double screenY, int radius, Material material) {
        double wx = camera.screenToWorldX(screenX), wy = camera.screenToWorldY(screenY);
        if (wx < 0 || wy < 0 || wx >= world.width() || wy >= world.height()) return;
        int cx = (int) Math.floor(wx), cy = (int) Math.floor(wy);
        for (int dy = -radius; dy <= radius; dy++) for (int dx = -radius; dx <= radius; dx++) {
            int x = cx + dx, y = cy + dy;
            if (dx * dx + dy * dy <= radius * radius && world.contains(x, y)) world.set(x, y, material);
        }
    }
}

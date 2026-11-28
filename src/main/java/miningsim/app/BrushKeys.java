package miningsim.app;

import java.awt.event.KeyEvent;
import miningsim.world.Material;

/** Physical digit keys, independent of the character produced by the keyboard layout. */
public final class BrushKeys {
    private BrushKeys() { }
    public static Material select(int code, boolean shift) {
        if (shift) return switch(code) {
            case KeyEvent.VK_1 -> Material.SAND;
            case KeyEvent.VK_3 -> Material.STONE_GRIT;
            case KeyEvent.VK_5 -> Material.GRAVEL;
            case KeyEvent.VK_6 -> Material.SOIL;
            case KeyEvent.VK_7 -> Material.GRANITE_GRIT;
            case KeyEvent.VK_8 -> Material.LIMESTONE_GRIT;
            case KeyEvent.VK_9 -> Material.MARBLE_GRIT;
            default -> null;
        };
        return switch(code) {
            case KeyEvent.VK_2 -> Material.WATER;
            case KeyEvent.VK_3 -> Material.STONE;
            case KeyEvent.VK_4 -> Material.WOOD;
            case KeyEvent.VK_7 -> Material.GRANITE;
            case KeyEvent.VK_8 -> Material.LIMESTONE;
            case KeyEvent.VK_9 -> Material.MARBLE;
            default -> null;
        };
    }
}

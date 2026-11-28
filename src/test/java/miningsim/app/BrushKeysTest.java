package miningsim.app;

import java.awt.event.KeyEvent;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BrushKeysTest {
    @Test void shiftedAndUnshiftedDigitsAreIndependent() {
        assertNull(BrushKeys.select(KeyEvent.VK_1,false));
        assertNull(BrushKeys.select(KeyEvent.VK_5,false));
        assertNull(BrushKeys.select(KeyEvent.VK_6,false));
        assertNull(BrushKeys.select(KeyEvent.VK_2,true));
        assertNull(BrushKeys.select(KeyEvent.VK_4,true));
        assertEquals(Material.WATER,BrushKeys.select(KeyEvent.VK_2,false));
        assertEquals(Material.WOOD,BrushKeys.select(KeyEvent.VK_4,false));
        assertEquals(Material.STONE,BrushKeys.select(KeyEvent.VK_3,false));
        assertEquals(Material.STONE_GRIT,BrushKeys.select(KeyEvent.VK_3,true));
        assertEquals(Material.SAND,BrushKeys.select(KeyEvent.VK_1,true));
        assertEquals(Material.GRAVEL,BrushKeys.select(KeyEvent.VK_5,true));
        assertEquals(Material.SOIL,BrushKeys.select(KeyEvent.VK_6,true));
        assertEquals(Material.GRANITE_GRIT,BrushKeys.select(KeyEvent.VK_7,true));
        assertEquals(Material.LIMESTONE_GRIT,BrushKeys.select(KeyEvent.VK_8,true));
        assertEquals(Material.MARBLE_GRIT,BrushKeys.select(KeyEvent.VK_9,true));
    }
}

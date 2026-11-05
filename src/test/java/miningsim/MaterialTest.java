package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import miningsim.world.Material;
import miningsim.world.Material.Category;

class MaterialTest {
    @Test void stableUniqueIdsAndCategories() {
        assertEquals(0, Material.AIR.id()); assertEquals(1, Material.STONE.id());
        assertEquals(2, Material.SAND.id()); assertEquals(3, Material.WATER.id()); assertEquals(4, Material.WOOD.id());
        assertEquals(5, Material.GRAVEL.id()); assertEquals(6, Material.SOIL.id());
        boolean[] seen = new boolean[256];
        for (Material material : Material.values()) {
            int id = Byte.toUnsignedInt(material.id());
            assertFalse(seen[id]); seen[id] = true;
            assertSame(material, Material.fromId(material.id()));
        }
        assertThrows(IllegalArgumentException.class, () -> Material.fromId((byte) 127));
        assertEquals(Category.EMPTY, Material.AIR.category());
        assertEquals(Category.STATIC, Material.STONE.category()); assertEquals(Category.STATIC, Material.WOOD.category());
        assertEquals(Category.GRANULAR, Material.SAND.category()); assertEquals(Category.LIQUID, Material.WATER.category());
        assertEquals(Category.GRANULAR, Material.GRAVEL.category()); assertEquals(Category.GRANULAR, Material.SOIL.category());
    }
    @Test void onlyGranularMaterialsDisplaceLiquid() {
        assertTrue(Material.SAND.canDisplace(Material.WATER));
        assertTrue(Material.GRAVEL.canDisplace(Material.WATER)); assertTrue(Material.SOIL.canDisplace(Material.WATER));
        assertFalse(Material.GRAVEL.canDisplace(Material.SOIL));
        assertFalse(Material.WATER.canDisplace(Material.SAND));
        assertFalse(Material.SAND.canDisplace(Material.STONE));
        assertFalse(Material.SAND.canDisplace(Material.WOOD));
        assertFalse(Material.SAND.canDisplace(Material.SAND));
    }
}

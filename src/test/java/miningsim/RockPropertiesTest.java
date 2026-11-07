package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.RockMaterialRegistry;
import miningsim.structural.RockProperties;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class RockPropertiesTest {
    @Test void referenceAndAdditionalProfiles() {
        var registry = RockMaterialRegistry.stoneReference();
        assertTrue(registry.isRock(Material.STONE.id()));
        assertFalse(registry.isRock(Material.AIR.id()));
        var p = registry.get(Material.STONE.id());
        var builder = new RockMaterialRegistry.Builder().register(1, p);
        var snapshot = builder.build(); builder.register(8, p);
        assertFalse(snapshot.isRock(8)); assertTrue(builder.build().isRock(8));
        assertThrows(IllegalArgumentException.class, () -> builder.register(8, p));
    }

    @Test void rejectsInvalidValuesAndIds() {
        for (double n : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new RockProperties(n,1,1,1,1,1,0));
            assertThrows(IllegalArgumentException.class, () -> new RockProperties(1,n,1,1,1,1,0));
            assertThrows(IllegalArgumentException.class, () -> new RockProperties(1,1,n,1,1,1,0));
            assertThrows(IllegalArgumentException.class, () -> new RockProperties(1,1,1,n,1,1,0));
            assertThrows(IllegalArgumentException.class, () -> new RockProperties(1,1,1,1,n,1,0));
            assertThrows(IllegalArgumentException.class, () -> new RockProperties(1,1,1,1,1,n,0));
        }
        assertThrows(IllegalArgumentException.class, () -> new RockProperties(1,1,1,1,1,1,-1));
        assertThrows(IllegalArgumentException.class, () -> new RockProperties(1,1,1,1,1,1,Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> RockMaterialRegistry.stoneReference().get(256));
    }

    @Test void weakerInterfaceDoesNotStrengthenEitherSide() {
        var a = new RockProperties(1, 10, 2, 5, 7, 11, 0.1);
        var b = new RockProperties(2, 20, 3, 4, 8, 9, 0.2);
        var c = RockProperties.weakerInterface(a, b);
        assertEquals(2, c.tensileStrengthPa()); assertEquals(4, c.compressiveStrengthPa());
        assertEquals(7, c.shearStrengthPa()); assertEquals(9, c.bendingStrengthPa());
        assertEquals(10, c.youngModulusPa()); assertEquals(0.2, c.damageRatePerSecond());
        assertEquals(c, RockProperties.weakerInterface(b, a));
    }
}

package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.RockBondStrength;
import miningsim.structural.RockProperties;
import org.junit.jupiter.api.Test;

class RockBondStrengthTest {
    private final RockProperties rock = new RockProperties(2500, 1e8, 100, 200, 50, 40, 0.3);

    @Test void signedAxialShearAndMomentUseDimensionalCapacities() {
        assertEquals(1, RockBondStrength.utilization(rock, 2, 0.5, new double[]{-200,0,0,200,0,0}), 1e-12);
        assertEquals(1, RockBondStrength.utilization(rock, 2, 0.5, new double[]{400,0,0,-400,0,0}), 1e-12);
        assertEquals(2, RockBondStrength.utilization(rock, 2, 0.5, new double[]{0,100,20,0,-100,-20}), 1e-12);
    }
    @Test void fixedStepDamageIsIrreversibleAndSaturates() {
        double damage = 0;
        for (int i = 0; i < 60; i++) damage = RockBondStrength.damageAfterTick(damage, 2, 0.3);
        assertEquals(0.3, damage, 1e-12);
        assertEquals(damage, RockBondStrength.damageAfterTick(damage, 0.5, 0.3));
        assertEquals(1, RockBondStrength.damageAfterTick(damage, 1000, 0.3));
        assertThrows(IllegalArgumentException.class, () -> RockBondStrength.damageAfterTick(0, Double.NaN, 1));
    }
}

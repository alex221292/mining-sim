package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.PlanarFrameElement;
import org.junit.jupiter.api.Test;

class PlanarFrameElementTest {
    @Test void cantileverTipLoadMatchesClosedForm() {
        // A clamped Euler–Bernoulli cantilever under a unit downward tip force:
        // v(L) = P L^3 / (3 EI), theta(L) = P L^2 / (2 EI).
        double l = 2, ei = 4, p = 3;
        double[] u = {0, 0, 0, 0, p * l*l*l / (3 * ei), p * l*l / (2 * ei)};
        double[] f = new double[6]; PlanarFrameElement.endForces(l, 10, ei, u, f);
        assertEquals(-p, f[1], 1e-12); assertEquals(-p * l, f[2], 1e-12);
        assertEquals(p, f[4], 1e-12); assertEquals(0, f[5], 1e-12);
    }

    @Test void axialAndRigidBodyMotions() {
        double[] f = new double[6];
        PlanarFrameElement.endForces(2, 8, 3, new double[]{0,0,0, 0.5,0,0}, f);
        assertEquals(-2, f[0], 1e-12); assertEquals(2, f[3], 1e-12);
        PlanarFrameElement.endForces(2, 8, 3, new double[]{4,3,0.2, 4,3.4,0.2}, f);
        for (double value : f) assertEquals(0, value, 1e-12);
    }

    @Test void invalidAndOverflowDoNotPartiallyOverwriteOutput() {
        double[] out = {9,9,9,9,9,9};
        assertThrows(IllegalArgumentException.class, () -> PlanarFrameElement.endForces(0, 1, 1, new double[6], out));
        assertThrows(IllegalArgumentException.class, () -> PlanarFrameElement.endForces(1e-200, 1, 1, new double[6], out));
        assertArrayEquals(new double[]{9,9,9,9,9,9}, out);
    }
}

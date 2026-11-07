package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.physics.SquareCollision;
import miningsim.physics.SquareCollision.Square;
import org.junit.jupiter.api.Test;

class SquareCollisionTest {
    @Test void axisAlignedAndTouching() {
        var a = new Square(0, 0, 0);
        assertNull(SquareCollision.intersect(a, new Square(1, 0, 0)));
        var c = SquareCollision.intersect(a, new Square(0.75, 0, 0));
        assertEquals(0.25, c.penetrationCells(), 1e-12);
        assertEquals(1, c.normalX(), 1e-12); assertEquals(0, c.normalY(), 1e-12);
        assertEquals(-1, SquareCollision.intersect(new Square(0.75, 0, 0), a).normalX(), 1e-12);
    }

    @Test void rotatedSquaresAndAabbFalsePositive() {
        var a = new Square(0, 0, Math.PI / 4);
        assertNull(SquareCollision.intersect(a, new Square(1.1, 1.1, 0)));
        var c = SquareCollision.intersect(a, new Square(0.8, 0, 0));
        assertNotNull(c); assertTrue(c.penetrationCells() > 0);
        assertEquals(1, Math.hypot(c.normalX(), c.normalY()), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new Square(0, Double.NaN, 0));
    }
}

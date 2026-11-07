package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.RockFragment;
import miningsim.physics.FragmentGeometry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import org.junit.jupiter.api.Test;

class FragmentGeometryTest {
    private FragmentGeometry geometry(Cell... cells) {
        var descriptor = new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE), List.of(cells));
        return new FragmentGeometry(new RockFragment(1, descriptor, 4, 7, 0));
    }

    @Test void singleCellMassAndInertia() {
        var g = geometry(new Cell(0, 0));
        assertEquals(2.5, g.massKg(), 1e-12);
        assertEquals(2.5 * 0.01 / 6, g.inertiaKgMetresSquared(), 1e-12);
        assertEquals(new FragmentGeometry.Point(4.5, 7.5), g.initialCenter());
    }

    @Test void irregularShapeAndInitialPlacement() {
        var g = geometry(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1));
        assertEquals(5.0 / 6, g.localCenterX(), 1e-12);
        assertEquals(5.0 / 6, g.localCenterY(), 1e-12);
        assertEquals(0.04583333333333334, g.inertiaKgMetresSquared(), 1e-12);
        var center = g.initialCenter();
        var p = g.transform(1.5, 0.5, center.x(), center.y(), 0);
        assertEquals(5.5, p.x(), 1e-12); assertEquals(7.5, p.y(), 1e-12);
        var rotated = g.transform(g.localCenterX() + 1, g.localCenterY(), center.x(), center.y(), Math.PI / 2);
        assertEquals(center.x(), rotated.x(), 1e-12); assertEquals(center.y() + 1, rotated.y(), 1e-12);
        var bounds = g.bounds(center.x(), center.y(), Math.PI / 2);
        assertEquals(center.x() - (2 - g.localCenterY()), bounds.minX(), 1e-12);
        assertEquals(center.y() + (2 - g.localCenterX()), bounds.maxY(), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> g.bounds(0, 0, Double.NaN));
    }
}

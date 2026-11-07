package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.RigidBodySystem;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import org.junit.jupiter.api.Test;

class RigidBodyMotionTest {
    private FragmentRegistry registry() {
        var r = new FragmentRegistry(4);
        r.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),
                List.of(new Cell(0,0), new Cell(1,0), new Cell(0,1))), 4, 7, 0);
        return r;
    }

    @Test void initialPlacementFreeFallAndRotation() {
        var system = new RigidBodySystem(registry()); system.synchronize();
        var body = system.body(1); var center = body.geometry().initialCenter();
        assertEquals(center.x(), body.xCells()); assertEquals(center.y(), body.yCells());
        body.setVelocity(1, 0, 2);
        for (int i = 0; i < 60; i++) system.step();
        assertEquals(center.x() + 10, body.xCells(), 1e-10);
        assertEquals(9.81, body.velocityYMetresPerSecond(), 1e-10);
        assertEquals(center.y() + 49.05, body.yCells(), 0.21);
        assertEquals(2, body.angleRadians(), 1e-10);
        assertThrows(IllegalArgumentException.class, () -> body.setVelocity(Double.NaN, 0, 0));
        assertTrue(Double.isFinite(body.yCells()));
    }

    @Test void deterministicReplayAndRegistryLifecycle() {
        var r = registry(); var a = new RigidBodySystem(r); var b = new RigidBodySystem(registry());
        a.synchronize(); a.synchronize(); b.synchronize();
        assertEquals(1, a.bodies().size());
        for (int i = 0; i < 100; i++) { a.step(); b.step(); }
        assertEquals(a.body(1).yCells(), b.body(1).yCells());
        assertTrue(r.remove(1)); a.synchronize(); assertTrue(a.bodies().isEmpty());
        assertTrue(new RigidBodySystem(new FragmentRegistry(4)).bodies().isEmpty());
    }

    @Test void defaultTickUsesFourSemiImplicitSubsteps() {
        var system = new RigidBodySystem(registry()); system.synchronize();
        var body = system.body(1); double initialY = body.yCells();
        system.step();
        double dt = 1.0 / 240.0;
        assertEquals(initialY + 9.81 * dt * dt * 10 / 0.1, body.yCells(), 1e-12);
        assertEquals(9.81 / 60, body.velocityYMetresPerSecond(), 1e-12);
    }

    @Test void synchronizationKeepsExistingBodyAndOrdersIds() {
        var r = registry(); var system = new RigidBodySystem(r); system.step();
        var first = system.body(1); double y = first.yCells();
        r.insert(first.fragment().descriptor(), 2, 2, 0);
        system.synchronize();
        assertSame(first, system.body(1)); assertEquals(y, first.yCells());
        assertEquals(List.of(1L, 2L), system.bodies().stream().map(body -> body.id()).toList());
        assertTrue(r.remove(1)); system.step();
        assertNull(system.body(1)); assertNotNull(system.body(2));
    }

    @Test void nonFiniteVelocityRejectedWithoutChangingState() {
        var system = new RigidBodySystem(registry()); system.synchronize(); var body = system.body(1);
        body.setVelocity(1, 2, 3);
        assertThrows(IllegalArgumentException.class, () -> body.setVelocity(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> body.setVelocity(0, 0, Double.NEGATIVE_INFINITY));
        assertEquals(1, body.velocityXMetresPerSecond());
        assertEquals(2, body.velocityYMetresPerSecond());
        assertEquals(3, body.angularVelocityRadiansPerSecond());
    }
}

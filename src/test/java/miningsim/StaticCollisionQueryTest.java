package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.RigidBodySystem;
import miningsim.physics.StaticCollisionQuery;
import miningsim.physics.StaticCollisionQuery.Boundary;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class StaticCollisionQueryTest {
    private RigidBodySystem body(Cell... cells) {
        var registry = new FragmentRegistry(1);
        registry.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE), List.of(cells)), 2, 2, 0);
        var system = new RigidBodySystem(registry); system.synchronize(); return system;
    }

    @Test void materialsAndConcaveGap() {
        for (var material : Material.values())
            assertEquals(material != Material.AIR && material != Material.WATER, StaticCollisionQuery.solid(material));
        var system = body(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1));
        var world = new World(8, 8); world.set(3, 3, Material.STONE);
        var query = new StaticCollisionQuery(world, 100, 100);
        assertTrue(query.query(system.body(1)).contacts().isEmpty());
        world.set(2, 2, Material.WATER);
        assertTrue(query.query(system.body(1)).contacts().isEmpty());
        world.set(2, 2, Material.WOOD);
        var result = query.query(system.body(1));
        assertEquals(1, result.contacts().size()); assertEquals(2, result.contacts().getFirst().worldX());
        assertEquals(2, world.occupiedCount()); assertEquals(Material.WOOD, world.get(2, 2));
    }

    @Test void explicitLimitsNeverSilentlySkip() {
        var system = body(new Cell(0, 0), new Cell(1, 0), new Cell(0, 1));
        var world = new World(8, 8); world.set(2, 2, Material.STONE);
        assertThrows(IllegalStateException.class, () -> new StaticCollisionQuery(world, 1, 100).query(system.body(1)));
        assertThrows(IllegalStateException.class, () -> new StaticCollisionQuery(world, 100, 1).query(system.body(1)));
    }

    @Test void allBoundariesAndCornerAreDetected() {
        for (var boundary : List.of(Boundary.LEFT, Boundary.RIGHT, Boundary.TOP, Boundary.BOTTOM)) {
            var system = body(new Cell(0, 0));
            var b = system.body(1);
            switch (boundary) {
                case LEFT -> b.setVelocity(-24, 0, 0);
                case RIGHT -> b.setVelocity(24, 0, 0);
                case TOP -> b.setVelocity(0, -24, 0);
                case BOTTOM -> b.setVelocity(0, 24, 0);
                default -> throw new AssertionError();
            }
            system.step();
            var contacts = new StaticCollisionQuery(new World(5, 5), 100, 100).query(b).contacts();
            assertTrue(contacts.stream().anyMatch(c -> c.boundary() == boundary), boundary.toString());
            assertTrue(contacts.stream().allMatch(c -> Double.isFinite(c.penetrationCells())));
        }
        var system = body(new Cell(0, 0)); system.body(1).setVelocity(-24, -24, 2); system.step();
        var contacts = new StaticCollisionQuery(new World(5, 5), 100, 100).query(system.body(1)).contacts();
        assertTrue(contacts.stream().anyMatch(c -> c.boundary() == Boundary.LEFT));
        assertTrue(contacts.stream().anyMatch(c -> c.boundary() == Boundary.TOP));
    }

    @Test void flatFloorContactsAreReducedAndRepeatable() {
        var system = body(new Cell(0,0), new Cell(1,0), new Cell(2,0));
        var world = new World(8,8); for (int x = 2; x < 5; x++) world.set(x, 3, Material.STONE);
        system.body(1).setVelocity(0, 3, 0); system.step();
        var query = new StaticCollisionQuery(world, 100, 100);
        var before = world.occupiedCount(); var first = query.query(system.body(1));
        assertEquals(first, query.query(system.body(1)));
        assertEquals(2, first.contacts().size());
        assertTrue(first.contacts().stream().allMatch(c -> c.normalY() > 0.99 && c.penetrationCells() > 0));
        assertEquals(before, world.occupiedCount()); assertEquals(Material.STONE, world.get(3, 3));
    }

    @Test void bedrockSuppressesCoincidentBottomBoundary() {
        var system = body(new Cell(0,0)); var world = new World(5, 5, World.Mode.BEDROCK_ENABLED);
        system.body(1).setVelocity(0, 14, 0); system.step();
        var contacts = new StaticCollisionQuery(world, 100, 100).query(system.body(1)).contacts();
        assertTrue(contacts.stream().anyMatch(c -> c.boundary() == Boundary.NONE && c.worldY() == 4));
        assertTrue(contacts.stream().noneMatch(c -> c.boundary() == Boundary.BOTTOM));
    }

    @Test void rotatedFragmentContactsWallAndPreservesBodyState() {
        var system = body(new Cell(0,0), new Cell(1,0), new Cell(0,1));
        var b = system.body(1); b.setVelocity(6, 0, 12); system.step();
        var world = new World(8,8); world.set(4, 2, Material.STONE); world.set(4, 3, Material.STONE);
        var result = new StaticCollisionQuery(world, 100, 100).query(b);
        assertFalse(result.contacts().isEmpty());
        assertTrue(result.contacts().stream().allMatch(c -> c.boundary() == Boundary.NONE));
        assertTrue(result.contacts().stream().allMatch(c -> Double.isFinite(c.pointX()) && Double.isFinite(c.pointY())));
        assertEquals(0.2, b.angleRadians(), 1e-12);
        assertEquals(2, world.occupiedCount());
    }
}

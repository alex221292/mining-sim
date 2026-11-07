package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import org.junit.jupiter.api.Test;

class FragmentRegistryTest {
    @Test void orderedIdsExactMassAndStationaryPlacement() {
        var p = SubstanceRegistry.reference().get(SubstanceRegistry.STONE);
        var shape = new FragmentDescriptor(p,List.of(new Cell(0,0),new Cell(1,0),new Cell(1,1)));
        var registry = new FragmentRegistry(2);
        var first = registry.insert(shape,63,64,100);
        assertEquals(1,first.id()); assertEquals(7_500_000,first.massMilligrams());
        assertEquals(64,first.worldX(new Cell(1,1))); assertEquals(65,first.worldY(new Cell(1,1)));
        assertEquals(0,first.orientationRadians()); assertEquals(SubstanceRegistry.STONE,first.substance());
        var second = registry.insert(shape,1,2,101);
        assertEquals(List.of(first,second),registry.snapshot()); assertEquals(15_000_000,registry.totalMassMilligrams());
        assertThrows(IllegalStateException.class,()->registry.insert(shape,0,0,0));
        assertEquals(2,registry.size()); assertTrue(registry.remove(1)); assertEquals(7_500_000,registry.totalMassMilligrams());
        assertEquals(3,registry.insert(shape,0,0,0).id());
        assertEquals(1,new FragmentRegistry(1).insert(shape,0,0,0).id());
    }
    @Test void preparedStateCannotBeReusedAfterInsertAndRemoval() {
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new Cell(0,0)));
        var registry=new FragmentRegistry(1); var stale=registry.prepare(shape,0,0,0);
        registry.insert(shape,0,0,0); registry.remove(1);
        assertFalse(stale.valid()); assertThrows(IllegalStateException.class,stale::publish);
        assertEquals(2,registry.insert(shape,0,0,0).id());
    }
}

package miningsim.fragment;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import org.junit.jupiter.api.Test;

class PreparedFragmentRemovalTest {
    private static FragmentDescriptor shape() {
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0)));
    }
    @Test void preparationIsIsolatedAndPublicationTransfersExactMass() {
        var registry=new FragmentRegistry(3); var first=registry.insert(shape(),1,1,0); var second=registry.insert(shape(),3,1,0);
        var before=registry.snapshot(); long revision=registry.revision(),mass=registry.totalMassMilligrams();
        var prepared=registry.prepareRemoval(first.id()); assertTrue(prepared.valid()); assertEquals(before,registry.snapshot());
        assertEquals(mass,registry.totalMassMilligrams()); assertEquals(revision,registry.revision());
        prepared.publish(); assertEquals(List.of(second),registry.snapshot()); assertEquals(mass-first.massMilligrams(),registry.totalMassMilligrams());
        assertEquals(revision+1,registry.revision()); assertFalse(prepared.valid());
        assertThrows(IllegalStateException.class,prepared::publish);
    }
    @Test void unrelatedMembershipChangeInvalidatesPreparedRemoval() {
        var registry=new FragmentRegistry(3); var first=registry.insert(shape(),1,1,0);
        var prepared=registry.prepareRemoval(first.id()); registry.insert(shape(),3,1,0);
        var before=registry.snapshot(); assertFalse(prepared.valid()); assertThrows(IllegalStateException.class,prepared::publish);
        assertEquals(before,registry.snapshot()); assertThrows(IllegalArgumentException.class,()->registry.prepareRemoval(999));
    }
}

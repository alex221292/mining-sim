package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.app.InteractiveSession;
import miningsim.physics.LargeCompoundBenchmark;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class LargeProspectiveLifecycleTest {
    @Test void largeStackSettlesOnRemovableSettledSupportAndRepeats() {
        var fixture=LargeCompoundBenchmark.fixture("large"); var original=fixture.session();
        var registry=original.structural().registry(); var fragments=registry.snapshot();
        assertEquals(List.of(134,134,526),fixture.sizes());
        for(var f:fragments) assertTrue(registry.remove(f.id()));
        for(var f:fragments) registry.insert(f.descriptor(),f.originX(),f.originY()-1,0);
        var s=InteractiveSession.headlessLifecycle(original.world(),original.structural(),false,false,SettledSupportCoordinator.Limits.measuredDefaults());
        original.retire(); var world=s.world(); var cells=new ArrayList<FragmentDescriptor.Cell>();
        for(int x=6;x<60;x++) { world.set(x,62,Material.STONE); cells.add(new FragmentDescriptor.Cell(x-6,0)); }
        long support=s.lifecycle().settled().registerExisting(1000,new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells),6,62);
        s.physics().synchronize(); s.occupancy().rebuild(s.physics(),0);
        long mass=registry.totalMassMilligrams();
        for(int i=0;i<1400 && s.lifecycle().settled().liveRocks()<4;i++) { s.tick(); assertNull(s.failure(),"tick "+i); assertFalse(s.blocked()); }
        assertEquals(4,s.lifecycle().settled().liveRocks(),s.lifecycle().diagnostics().toString());
        var upper=s.lifecycle().settled().snapshot().stream().filter(r->r.id()!=support && r.remainingCells()>0).toList();
        assertEquals(3,upper.size()); assertEquals(794,upper.stream().mapToInt(r->r.remainingCells()).sum());
        assertEquals(mass,upper.stream().mapToLong(r->r.massMilligrams()).sum());
        assertTrue(upper.stream().allMatch(r->r.descriptor().substance().id().equals(SubstanceRegistry.STONE)));
        var changes=new ArrayList<World.CellChange>(); for(int x=6;x<60;x++) changes.add(new World.CellChange(x,62,Material.STONE,Material.AIR));
        s.enqueue(()->assertEquals(World.BatchResult.CHANGED,world.replaceAllIfMatches(changes)));
        boolean reactivated=false,completed=false;
        for(int i=0;i<1800;i++) {
            s.tick(); assertNull(s.failure(),"repeat tick "+i); assertFalse(s.blocked());
            if(s.lifecycle().diagnostics().reactivated()==3) reactivated=true;
            if(reactivated && s.lifecycle().settled().liveRocks()==3 && s.physics().bodies().isEmpty()) { completed=true; break; }
        }
        assertTrue(reactivated,s.lifecycle().diagnostics().toString()); assertTrue(completed,s.lifecycle().diagnostics().toString());
        var remaining=s.lifecycle().settled().snapshot().stream().filter(r->r.remainingCells()>0).toList();
        assertEquals(3,remaining.size()); assertEquals(794,remaining.stream().mapToInt(r->r.remainingCells()).sum());
        assertEquals(mass,remaining.stream().mapToLong(r->r.massMilligrams()).sum());
        assertTrue(remaining.stream().allMatch(r->r.descriptor().substance().id().equals(SubstanceRegistry.STONE)));
        assertEquals(upper.stream().map(r->r.massMilligrams()).sorted().toList(),remaining.stream().map(r->r.massMilligrams()).sorted().toList());
        assertEquals(3,remaining.stream().map(r->r.id()).distinct().count());
        assertTrue(remaining.stream().noneMatch(r->upper.stream().anyMatch(previous->previous.id()==r.id())));
    }
}

package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.app.InteractiveSession;
import miningsim.physics.LargeCompoundBenchmark;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class StackReactivationRegressionTest {
    @Test void offsetThirdRockSettlesAndDoesNotReactivate() {
        var original=LargeCompoundBenchmark.fixture("large").session(); var registry=original.structural().registry();
        for(var f:registry.snapshot()) assertTrue(registry.remove(f.id()));
        var s=InteractiveSession.headlessLifecycle(original.world(),original.structural(),false,false,SettledSupportCoordinator.Limits.measuredDefaults()); original.retire();
        var world=s.world(); var three=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0)));
        for(int x=3;x<=5;x++) { world.set(x,62,Material.STONE); world.set(x,61,Material.STONE); }
        s.lifecycle().settled().registerExisting(10,three,3,61);
        for(int x=4;x<=6;x++) world.set(x,60,Material.STONE);
        s.lifecycle().settled().registerExisting(11,three,4,60);
        var one=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        registry.insert(one,6,59,0); s.physics().synchronize(); s.occupancy().rebuild(s.physics(),0);
        for(int i=0;i<1000 && s.lifecycle().settled().liveRocks()<3;i++) { s.tick(); assertNull(s.failure(),"tick "+i); }
        assertEquals(3,s.lifecycle().settled().liveRocks(),s.lifecycle().diagnostics().toString());
        for(int i=0;i<200;i++) { s.tick(); assertNull(s.failure(),"after settling tick "+i); assertEquals(0,s.lifecycle().diagnostics().reactivated()); }
        assertEquals(3,s.lifecycle().settled().liveRocks()); assertTrue(s.physics().bodies().isEmpty());
    }
    @Test void supportLossWithSleepingRigidAboveSettledStack() {
        var original=LargeCompoundBenchmark.fixture("large").session(); var registry=original.structural().registry();
        for(var f:registry.snapshot()) assertTrue(registry.remove(f.id()));
        var s=InteractiveSession.headlessLifecycle(original.world(),original.structural(),false,false,SettledSupportCoordinator.Limits.measuredDefaults());
        original.retire(); var w=s.world();
        var cell=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        w.set(5,62,Material.STONE); w.set(6,62,Material.STONE); w.set(5,61,Material.STONE);
        var pair=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0)));
        s.lifecycle().settled().registerExisting(10,pair,5,62); s.lifecycle().settled().registerExisting(11,cell,5,61);
        registry.insert(cell,5,60,0); s.physics().synchronize(); s.occupancy().rebuild(s.physics(),0);
        s.structural().connectivity().setTestBatchObserver(()->!s.lifecycle().inSettlingPublication(),null);
        for(int i=0;i<500;i++) { s.tick(); assertNull(s.failure(),"tick "+i); }
        assertEquals(1,s.physics().bodies().size()); assertTrue(s.physics().bodies().getFirst().sleeping());
        s.enqueue(()->w.set(5,62,Material.AIR));
        for(int i=0;i<500 && s.lifecycle().diagnostics().reactivated()<2;i++) { s.tick(); assertNull(s.failure(),"tick "+i); }
        assertEquals(2,s.lifecycle().diagnostics().reactivated(),s.lifecycle().diagnostics()+" body y="+s.physics().bodies().getFirst().yCells()+" angle="+s.physics().bodies().getFirst().angleRadians()+" occupancy="+java.util.Arrays.toString(s.occupancy().snapshot().bodyIdsAt(5,61)));
        assertEquals(3,s.physics().bodies().size()); assertEquals(0,s.lifecycle().settled().liveRocks());
        long reactivated=s.physics().bodies().stream().mapToLong(b->b.id()).max().orElseThrow();
        double y=s.physics().body(reactivated).yCells(); boolean fell=false;
        for(int i=0;i<60;i++) { s.tick(); assertNull(s.failure(),"post-reactivation tick "+i); assertFalse(s.blocked());
            if(s.physics().body(reactivated).yCells()>y+0.02) fell=true; }
        assertTrue(fell,"reactivated rock did not fall");
    }
}

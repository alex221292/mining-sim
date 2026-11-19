package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.physics.GroupSettlingCommit;
import miningsim.physics.GroupSettlingEligibility;
import miningsim.physics.LargeCompoundBenchmark;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class GroupSettlingTransactionTest {
    @Test void vetoExceptionAndObserversSeeAtomicState() {
        var s=LargeCompoundBenchmark.fixture("large").session(); var topology=s.structural().connectivity();
        var settled=new SettledRockRegistry(s.world(),topology,10,1024); var ids=List.of(1L,2L,3L);
        var eligibility=new GroupSettlingEligibility(ids);
        for(int i=0;i<500;i++) { s.tick(); assertNull(s.failure()); eligibility.observe(s.physics(),s.coupled(),s.world(),topology,settled); }
        var result=GroupSettlingCommit.prepareGroupSettling(ids,s.physics(),s.coupled(),s.world(),s.occupancy(),topology,settled,eligibility,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,result.status(),result.reason()); var plan=result.prepared();
        long mass=s.structural().registry().totalMassMilligrams();
        Runnable old=()->{ assertEquals(3,s.physics().bodies().size()); assertEquals(3,s.structural().registry().size());
            assertEquals(mass,s.structural().registry().totalMassMilligrams()); assertTrue(settled.snapshot().isEmpty());
            assertEquals(3,s.occupancy().snapshot().diagnostics().indexedBodies());
            for(var c:plan.changes()) assertEquals(Material.AIR,s.world().get(c.x(),c.y())); };
        topology.setTestBatchObserver(()->{old.run(); return false;},null);
        assertEquals(World.BatchResult.REJECTED,s.world().commitGroupSettling(plan)); old.run();
        topology.setTestBatchObserver(()->{old.run(); throw new IllegalStateException("injected");},null);
        assertThrows(IllegalStateException.class,()->s.world().commitGroupSettling(plan)); old.run();
        boolean[] called={false}; topology.setTestBatchObserver(()->{old.run(); return true;},()->{
            called[0]=true; assertTrue(s.physics().bodies().isEmpty()); assertEquals(0,s.structural().registry().size());
            assertEquals(0,s.occupancy().snapshot().diagnostics().indexedBodies()); assertEquals(3,settled.snapshot().size());
            assertEquals(mass,settled.snapshot().stream().mapToLong(SettledRockRegistry.Rock::massMilligrams).sum());
            for(var c:plan.changes()) { assertEquals(Material.STONE,s.world().get(c.x(),c.y())); assertNotEquals(0,settled.ownerAt(c.x(),c.y())); }
        });
        assertEquals(World.BatchResult.CHANGED,s.world().commitGroupSettling(plan)); assertTrue(called[0]);
        topology.setTestBatchObserver(null,null);
        assertEquals(World.BatchResult.REJECTED,s.world().commitGroupSettling(plan));
    }
}

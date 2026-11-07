package miningsim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.*;
import miningsim.structural.RockConnectivity;
import miningsim.world.*;
import miningsim.resource.*;
import org.junit.jupiter.api.Test;

class WorldBatchExtractionTest {
    @Test void batchValidatesBeforeWritesAndNotifiesOnce() {
        World w = new World(4,4,World.Mode.BEDROCK_ENABLED);
        w.set(0,0,Material.STONE); w.set(1,0,Material.STONE);
        int[] callbacks={0};
        w.setChangeSink(new World.ChangeSink() {
            public void invalidated() { }
            public void changed(int x,int y,byte a,byte b) { fail("per-cell callback"); }
            public void changedBatch(List<World.CellChange> changes) {
                callbacks[0]++; assertEquals(Material.AIR,w.get(0,0)); assertEquals(Material.AIR,w.get(1,0));
                assertThrows(UnsupportedOperationException.class,()->changes.add(changes.getFirst()));
            }
        });
        var a=new World.CellChange(0,0,Material.STONE,Material.AIR); var b=new World.CellChange(1,0,Material.STONE,Material.AIR);
        assertEquals(World.BatchResult.REJECTED,w.replaceAllIfMatches(List.of(a,a))); assertEquals(0,callbacks[0]);
        assertEquals(World.BatchResult.REJECTED,w.replaceAllIfMatches(List.of(a,new World.CellChange(0,3,Material.BEDROCK,Material.AIR))));
        assertEquals(Material.STONE,w.get(0,0)); assertEquals(0,callbacks[0]);
        assertEquals(World.BatchResult.NO_OP,w.replaceAllIfMatches(List.of(new World.CellChange(0,0,Material.STONE,Material.STONE))));
        assertEquals(World.BatchResult.CHANGED,w.replaceAllIfMatches(List.of(b,a))); assertEquals(1,callbacks[0]);
    }
    @Test void ownershipAndDistantBrokenBondSurviveExtraction() {
        World w=new World(10,5,World.Mode.BEDROCK_ENABLED);
        w.set(1,1,Material.STONE); w.set(2,1,Material.STONE);
        w.set(6,1,Material.STONE); w.set(7,1,Material.STONE);
        RockConnectivity c=new RockConnectivity(w); c.breakBond(6,1,7,1); while(c.pending()) c.advance(100);
        long inc=c.bondIncarnation(6,1,7,1), massBefore=4*2_500_000L;
        var plan=ExtractionPlanner.prepare(w,c,1,1,64).plan(); var registry=new FragmentRegistry(2);
        var result=ExtractionCommit.commit(plan,registry);
        assertEquals(ExtractionCommit.Status.COMMITTED,result.status()); assertEquals(1,result.fragment().id());
        assertEquals(Material.AIR,w.get(1,1)); assertEquals(Material.AIR,w.get(2,1));
        assertEquals(RockConnectivity.BondState.BROKEN,c.bondState(6,1,7,1)); assertEquals(inc,c.bondIncarnation(6,1,7,1));
        assertEquals(massBefore,2*2_500_000L+registry.totalMassMilligrams());
        assertTrue(c.pending()); while(c.pending()) c.advance(100);
        assertEquals(ExtractionCommit.Status.STALE,ExtractionCommit.commit(plan,registry).status());
    }
    @Test void staleAndFullNeverChangeEitherOwner() {
        World w=new World(5,4); w.set(1,1,Material.STONE); RockConnectivity c=new RockConnectivity(w); while(c.pending()) c.advance(100);
        var plan=ExtractionPlanner.prepare(w,c,1,1,64).plan();
        assertEquals(ExtractionCommit.Status.CAPACITY,ExtractionCommit.commit(plan,new FragmentRegistry(0)).status());
        assertEquals(Material.STONE,w.get(1,1));
        var registry=new FragmentRegistry(2); w.set(3,1,Material.STONE);
        assertEquals(ExtractionCommit.Status.STALE,ExtractionCommit.commit(plan,registry).status());
        assertEquals(0,registry.size()); assertEquals(Material.STONE,w.get(1,1));
        while(c.pending()) c.advance(100);
        assertEquals(1,ExtractionCommit.commit(ExtractionPlanner.prepare(w,c,1,1,64).plan(),registry).fragment().id());
    }
    @Test void callbackObservesCompletedOwnershipTransfer() {
        World w = new World(3,3); w.set(0,0,Material.STONE); w.set(1,0,Material.STONE);
        var registry=new FragmentRegistry(1);
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),
                List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0)));
        var prepared=registry.prepare(descriptor,0,0,0); int[] count={0};
        w.setChangeSink(new World.ChangeSink() {
            public void invalidated() { }
            public void changed(int x,int y,byte old,byte next) { fail("unexpected cell notification"); }
            public void changedBatch(List<World.CellChange> changes) {
                count[0]++; assertEquals(Material.AIR,w.get(0,0)); assertEquals(Material.AIR,w.get(1,0));
                assertEquals(1,registry.size()); assertEquals(5_000_000,registry.totalMassMilligrams());
            }
        });
        assertEquals(World.BatchResult.CHANGED,w.replaceAllIfMatches(List.of(
                new World.CellChange(1,0,Material.STONE,Material.AIR),new World.CellChange(0,0,Material.STONE,Material.AIR)),prepared));
        assertEquals(1,count[0]);
    }
    @Test void batchResetsIncidentIncarnationWithoutClearingDistantDamage() {
        World w = new World(8,3); for(int x:new int[]{0,1,5,6}) w.set(x,1,Material.STONE);
        RockConnectivity c = new RockConnectivity(w); c.breakBond(0,1,1,1); c.breakBond(5,1,6,1);
        while(c.pending()) c.advance(100);
        long old = c.bondIncarnation(0,1,1,1); long rev=c.revision();
        assertEquals(World.BatchResult.CHANGED,w.replaceAllIfMatches(List.of(new World.CellChange(0,1,Material.STONE,Material.AIR))));
        assertEquals(rev+1,c.revision()); assertEquals(RockConnectivity.BondState.BROKEN,c.bondState(5,1,6,1));
        w.set(0,1,Material.STONE); assertEquals(RockConnectivity.BondState.INTACT,c.bondState(0,1,1,1));
        assertTrue(c.bondIncarnation(0,1,1,1)>old);
    }
    @Test void extractionAfterRealMechanicalBreakInvalidatesSnapshot() {
        var scene=miningsim.structural.RockReferenceScenes.cantilever(4);
        var c=scene.connectivity(); while(c.pending()) c.advance(1000);
        assertEquals(miningsim.structural.RockDamageSystem.Status.CONVERGED,scene.damage().analyze(scene.x(),scene.y()));
        for(int tick=0;tick<100 && !c.pending();tick++) scene.damage().advanceTick(tick);
        assertTrue(c.pending()); assertThrows(IllegalStateException.class,scene.damage()::snapshot);
        while(c.pending()) c.advance(1000);
        var plan=ExtractionPlanner.prepare(scene.world(),c,3,2,64);
        assertEquals(ExtractionPlanner.Status.READY,plan.status());
        int before=scene.world().occupiedCount(); var registry=new FragmentRegistry(1);
        assertEquals(ExtractionCommit.Status.COMMITTED,ExtractionCommit.commit(plan.plan(),registry).status());
        assertEquals(before-plan.plan().worldCells().size(),scene.world().occupiedCount());
        assertEquals(plan.plan().descriptor().massMilligrams(),registry.totalMassMilligrams());
        assertTrue(c.pending()); assertThrows(IllegalStateException.class,scene.damage()::snapshot);
    }
    @Test void extractionIsRejectedInsideBondTraversal() {
        World w=new World(4,3); w.set(0,0,Material.STONE); w.set(1,0,Material.STONE);
        RockConnectivity c=new RockConnectivity(w); while(c.pending()) c.advance(100);
        var plan=ExtractionPlanner.prepare(w,c,0,0,64).plan(); var registry=new FragmentRegistry(1);
        c.forEachBond(0,0,4,3,(x,y,nx,ny,state)->assertEquals(ExtractionCommit.Status.TRAVERSING,ExtractionCommit.commit(plan,registry).status()));
        assertEquals(Material.STONE,w.get(0,0)); assertEquals(0,registry.size());
    }
}

package miningsim.app;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.SettledSupportCoordinator;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class StaticRockAssemblyTest {
    @Test void independentAssembliesRemainIndependentAfterSupportRemoval() {
        var w=new World(24,20,World.Mode.BEDROCK_ENABLED); w.set(5,3,Material.STONE); w.set(15,3,Material.STONE);
        var s=InteractiveSession.headlessLifecycle(w,false,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<1200 && s.lifecycle().settled().liveRocks()<2;i++) { s.tick(); assertNull(s.failure()); }
        assertEquals(2,s.lifecycle().settled().liveRocks());
        s.enqueue(()->{ w.set(5,3,Material.STONE); w.set(15,3,Material.STONE); });
        for(int i=0;i<1500 && s.lifecycle().settled().liveRocks()<4;i++) { s.tick(); assertNull(s.failure()); assertFalse(s.blocked()); }
        assertEquals(4,s.lifecycle().settled().liveRocks(),s.lifecycle().diagnostics().toString());
        for(int i=0;i<300 && !s.lifecycle().assemblyDiagnostics().complete();i++) { s.tick(); assertNull(s.failure()); }
        var assembly=s.lifecycle().assemblyDiagnostics();
        assertTrue(assembly.complete()); assertEquals(2,assembly.assemblies()); assertEquals(4,assembly.supported()); assertEquals(4,assembly.contacts());
        long otherBottom=s.lifecycle().settled().ownerAt(15,18),otherTop=s.lifecycle().settled().ownerAt(15,17);
        s.enqueue(()->w.set(5,18,Material.AIR));
        for(int i=0;i<300 && s.lifecycle().diagnostics().reactivated()==0;i++) { s.tick(); assertNull(s.failure()); }
        assertEquals(1,s.lifecycle().diagnostics().reactivated());
        assertEquals(otherBottom,s.lifecycle().settled().ownerAt(15,18)); assertEquals(otherTop,s.lifecycle().settled().ownerAt(15,17));
    }
    @Test void lateralContactDoesNotSupportOrdinaryStone() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); w.set(5,3,Material.STONE);
        var s=InteractiveSession.headlessLifecycle(w,false,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<900 && s.lifecycle().settled().liveRocks()<1;i++) { s.tick(); assertNull(s.failure()); }
        s.enqueue(()->w.set(5,3,Material.STONE));
        for(int i=0;i<1200 && s.lifecycle().settled().liveRocks()<2;i++) { s.tick(); assertNull(s.failure()); }
        assertEquals(2,s.lifecycle().settled().liveRocks());
        s.enqueue(()->w.set(6,17,Material.STONE)); boolean detached=false;
        for(int i=0;i<200;i++) { s.tick(); assertNull(s.failure()); if(!s.physics().bodies().isEmpty()) { detached=true; break; } }
        assertTrue(detached,s.automatic().diagnostics().toString());
    }
    @Test void prospectiveReplayIsDeterministic() {
        var a=new World(20,20,World.Mode.BEDROCK_ENABLED); var b=new World(20,20,World.Mode.BEDROCK_ENABLED);
        a.set(5,3,Material.STONE); b.set(5,3,Material.STONE);
        var x=InteractiveSession.headlessLifecycle(a,false,SettledSupportCoordinator.Limits.measuredDefaults());
        var y=InteractiveSession.headlessLifecycle(b,false,SettledSupportCoordinator.Limits.measuredDefaults());
        int added=1; boolean done=false;
        for(int tick=0;tick<2400;tick++) {
            x.tick(); y.tick(); assertNull(x.failure()); assertNull(y.failure());
            assertEquals(x.lifecycle().diagnostics(),y.lifecycle().diagnostics(),"tick "+tick);
            assertEquals(x.physics().bodies().stream().map(body->java.util.List.of(body.id(),body.xCells(),body.yCells(),body.angleRadians(),body.sleeping())).toList(),
                    y.physics().bodies().stream().map(body->java.util.List.of(body.id(),body.xCells(),body.yCells(),body.angleRadians(),body.sleeping())).toList());
            for(int row=0;row<20;row++) for(int col=0;col<20;col++) {
                assertEquals(a.get(col,row),b.get(col,row));
                assertEquals(x.lifecycle().settled().ownerAt(col,row),y.lifecycle().settled().ownerAt(col,row));
            }
            if(x.lifecycle().settled().liveRocks()==added && x.physics().bodies().isEmpty()) {
                if(added==3) { done=true; break; }
                x.enqueue(()->a.set(5,3,Material.STONE)); y.enqueue(()->b.set(5,3,Material.STONE)); added++;
            }
        }
        assertTrue(done);
    }
    @Test void ordinaryStoneRestsOnSettledWithoutBondAndDetachesAfterRemoval() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE);
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<900 && session.lifecycle().settled().liveRocks()<1;i++) { session.tick(); assertNull(session.failure()); }
        assertEquals(1,session.lifecycle().settled().liveRocks());
        session.enqueue(()->world.set(5,17,Material.STONE));
        for(int i=0;i<200;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        assertEquals(Material.STONE,world.get(5,17)); assertEquals(0,session.lifecycle().settled().ownerAt(5,17));
        assertEquals(miningsim.structural.RockConnectivity.BondState.ABSENT,session.automatic().topology().bondState(5,17,5,18));
        assertTrue(session.physics().bodies().isEmpty());
        session.enqueue(()->world.set(5,18,Material.AIR));
        boolean detached=false;
        for(int i=0;i<200;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked());
            if(!session.physics().bodies().isEmpty()) { detached=true; break; } }
        assertTrue(detached,session.automatic().diagnostics().toString());
    }
    @Test void sequentialFallingRocksSettleOnExistingSettledRock() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE);
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<900 && session.lifecycle().settled().liveRocks()<1;i++) { session.tick(); assertNull(session.failure()); }
        assertEquals(1,session.lifecycle().settled().liveRocks());
        long first=session.lifecycle().settled().ownerAt(5,18); assertTrue(first>0);
        session.enqueue(()->world.set(5,3,Material.STONE));
        for(int i=0;i<1200 && session.lifecycle().settled().liveRocks()<2;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        assertEquals(2,session.lifecycle().settled().liveRocks(),session.lifecycle().diagnostics().toString());
        long second=session.lifecycle().settled().ownerAt(5,17); assertTrue(second>0); assertNotEquals(first,second);
        session.enqueue(()->world.set(5,3,Material.STONE));
        for(int i=0;i<1200 && session.lifecycle().settled().liveRocks()<3;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        assertEquals(3,session.lifecycle().settled().liveRocks(),session.lifecycle().diagnostics().toString());
        long third=session.lifecycle().settled().ownerAt(5,16); assertTrue(third>0); assertNotEquals(second,third);
        long cellsBefore=session.cellularSteps();
        for(int batch=0;batch<3;batch++) {
            final int column=10+batch; session.enqueue(()->world.set(column,1,Material.SAND));
            for(int i=0;i<40;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        }
        assertEquals(cellsBefore+120,session.cellularSteps());
        for(int column=10;column<13;column++) assertEquals(Material.SAND,world.get(column,18));
        assertEquals(3,session.lifecycle().settled().liveRocks());
        session.enqueue(()->world.set(5,18,Material.AIR));
        for(int i=0;i<300 && session.lifecycle().diagnostics().reactivated()<2;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        assertEquals(2,session.lifecycle().diagnostics().reactivated());
    }
}

package miningsim.app;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.structural.SettledSupportCoordinator;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.physics.LargeCompoundBenchmark;
import org.junit.jupiter.api.Test;

class HeadlessRockLifecycleTest {
    private static void assertStoneAccounting(InteractiveSession session,int expectedCells) {
        var world=session.world(); var settled=session.lifecycle().settled();
        int worldCells=0,ownedCells=0,rigidCells=0;
        for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) {
            if(world.get(x,y)==Material.STONE) worldCells++;
            if(settled.ownerAt(x,y)>0) { ownedCells++; assertEquals(Material.STONE,world.get(x,y)); }
        }
        long rigidMass=0,settledMass=0;
        for(var body:session.physics().bodies()) {
            assertEquals(miningsim.resource.SubstanceRegistry.STONE,body.fragment().substance());
            rigidCells+=body.fragment().descriptor().cells().size(); rigidMass+=body.fragment().massMilligrams();
        }
        for(var rock:settled.snapshot()) if(rock.remainingCells()>0) {
            assertEquals(miningsim.resource.SubstanceRegistry.STONE,rock.descriptor().substance().id());
            settledMass+=rock.massMilligrams();
        }
        long cellMass=miningsim.resource.MassAccounting.cellMilligrams(miningsim.resource.SubstanceRegistry.reference().get(miningsim.resource.SubstanceRegistry.STONE));
        assertEquals(expectedCells,worldCells+rigidCells);
        assertEquals(ownedCells*cellMass,settledMass);
        assertEquals(rigidCells*cellMass,rigidMass);
    }
    @Test void ordinaryStoneFoundationCannotCurrentlySettleRigidRock() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED);
        world.set(5,18,Material.STONE); world.set(5,3,Material.STONE);
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<1000;i++) { session.tick(); assertNull(session.failure(),"tick "+i); assertFalse(session.blocked()); }
        assertEquals(0,session.lifecycle().diagnostics().settled());
        assertEquals(1,session.physics().bodies().size());
        assertTrue(session.physics().bodies().getFirst().sleeping());
        assertTrue(session.lifecycle().diagnostics().lastReason().contains("support path to BEDROCK"),session.lifecycle().diagnostics().toString());
    }
    @Test void marbleSubstanceAndMassSurviveRepeatedLifecycle() {
        var fixture=LargeCompoundBenchmark.fixture("large"); var old=fixture.session(); var registry=old.structural().registry();
        for(var fragment:registry.snapshot()) assertTrue(registry.remove(fragment.id()));
        var substances=miningsim.resource.SubstanceRegistry.reference();
        var cells=java.util.List.of(new miningsim.resource.FragmentDescriptor.Cell(0,0));
        registry.insert(new miningsim.resource.FragmentDescriptor(substances.get(miningsim.resource.SubstanceRegistry.STONE),cells),10,61,0);
        var marble=registry.insert(new miningsim.resource.FragmentDescriptor(substances.get(miningsim.resource.SubstanceRegistry.MARBLE),cells),10,60,0);
        var s=InteractiveSession.headlessLifecycle(old.world(),old.structural(),false,false,SettledSupportCoordinator.Limits.measuredDefaults());
        old.retire(); s.physics().synchronize(); s.occupancy().rebuild(s.physics(),0);
        boolean removed=false,sawRigidAgain=false,done=false;
        for(int i=0;i<1500;i++) {
            s.tick(); assertNull(s.failure(),"tick "+i); assertFalse(s.blocked());
            var marbleBodies=s.physics().bodies().stream().filter(b->b.fragment().substance().equals(miningsim.resource.SubstanceRegistry.MARBLE)).toList();
            var marbleRocks=s.lifecycle().settled().snapshot().stream().filter(r->r.remainingCells()>0 && r.descriptor().substance().id().equals(miningsim.resource.SubstanceRegistry.MARBLE)).toList();
            assertEquals(1,marbleBodies.size()+marbleRocks.size(),"exactly one marble owner at tick "+i+" records="+s.lifecycle().settled().snapshot()+" bodies="+s.physics().bodies());
            if(!marbleBodies.isEmpty()) assertEquals(marble.massMilligrams(),marbleBodies.getFirst().fragment().massMilligrams());
            if(!marbleRocks.isEmpty()) {
                var rock=marbleRocks.getFirst(); assertEquals(marble.massMilligrams(),rock.massMilligrams());
                for(var c:s.lifecycle().settled().cells(rock.id())) { assertEquals(rock.id(),s.lifecycle().settled().ownerAt(c.x(),c.y())); assertEquals(Material.STONE,s.world().get(c.x(),c.y())); }
            }
            if(!removed && s.lifecycle().settled().liveRocks()==2 && s.physics().bodies().isEmpty()) {
                s.enqueue(()->s.world().set(10,62,Material.AIR)); removed=true;
            }
            if(removed && s.lifecycle().diagnostics().reactivated()>0 && !marbleBodies.isEmpty()) sawRigidAgain=true;
            if(sawRigidAgain && !marbleRocks.isEmpty() && s.physics().bodies().isEmpty()) { done=true; break; }
        }
        assertTrue(done,s.lifecycle().diagnostics().toString());
    }
    private static java.util.List<Object> snapshot(InteractiveSession s) {
        var result=new java.util.ArrayList<Object>(); var d=s.lifecycle().diagnostics();
        result.add(java.util.List.of(d.rigidBodies(),d.settledRocks(),d.pendingSupport(),d.settled(),d.reactivated(),d.rejected(),d.retries(),d.rejectionReasons()));
        for(var b:s.physics().bodies()) result.add(java.util.List.of(b.id(),b.xCells(),b.yCells(),b.angleRadians(),b.velocityXMetresPerSecond(),b.velocityYMetresPerSecond(),b.angularVelocityRadiansPerSecond(),b.sleeping()));
        for(var r:s.lifecycle().settled().snapshot()) result.add(java.util.List.of(r.id(),r.sourceFragmentId(),r.remainingCells(),r.massMilligrams(),r.generation(),r.integrity(),r.reactivatedBodyId()));
        for(int y=0;y<s.world().height();y++) for(int x=0;x<s.world().width();x++) result.add(s.world().get(x,y));
        return result;
    }
    @Test void completeLifecycleReplayIsDeterministic() {
        var a=new World(20,20,World.Mode.BEDROCK_ENABLED); var b=new World(20,20,World.Mode.BEDROCK_ENABLED);
        for(var world:java.util.List.of(a,b)) { world.set(5,3,Material.STONE); world.set(5,8,Material.STONE); }
        var x=InteractiveSession.headlessLifecycle(a,false,SettledSupportCoordinator.Limits.measuredDefaults());
        var y=InteractiveSession.headlessLifecycle(b,false,SettledSupportCoordinator.Limits.measuredDefaults());
        boolean removed=false,completed=false;
        for(int i=0;i<1600;i++) {
            x.tick(); y.tick(); assertNull(x.failure()); assertNull(y.failure()); assertEquals(snapshot(x),snapshot(y),"tick "+i);
            if(!removed && x.lifecycle().settled().liveRocks()==2 && x.physics().bodies().isEmpty()) {
                x.enqueue(()->a.set(5,18,Material.AIR)); y.enqueue(()->b.set(5,18,Material.AIR)); removed=true;
            }
            if(removed && x.lifecycle().diagnostics().reactivated()>0 && x.lifecycle().diagnostics().settled()>=3 && x.physics().bodies().isEmpty()) { completed=true; break; }
        }
        assertTrue(completed);
    }
    @Test void largeStackSettlesThroughSessionTicks() {
        var fixture=LargeCompoundBenchmark.fixture("large"); var original=fixture.session();
        var session=InteractiveSession.headlessLifecycle(original.world(),original.structural(),false,false,SettledSupportCoordinator.Limits.measuredDefaults());
        original.retire(); session.physics().synchronize(); session.occupancy().rebuild(session.physics(),0);
        assertEquals(java.util.List.of(134,134,526),fixture.sizes());
        boolean done=false;
        for(int i=0;i<1200;i++) { session.tick(); assertNull(session.failure(),"tick "+i); assertFalse(session.blocked());
            if(session.lifecycle().diagnostics().settled()==3) { done=true; break; } }
        assertTrue(done,session.lifecycle().diagnostics().toString()); assertTrue(session.physics().bodies().isEmpty());
        assertEquals(794,session.lifecycle().settled().snapshot().stream().mapToInt(r->r.remainingCells()).sum());
        assertEquals(3,session.lifecycle().settled().liveRocks());
    }
    @Test void completeRepeatedLifecycleNeedsNoManualTransitions() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE); world.set(5,8,Material.STONE);
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        boolean twoSettled=false;
        for(int i=0;i<1500;i++) { session.tick(); assertNull(session.failure(),"tick "+i); assertFalse(session.blocked()); assertStoneAccounting(session,2);
            if(session.lifecycle().diagnostics().settledRocks()==2 && session.physics().bodies().isEmpty()) { twoSettled=true; break; } }
        assertTrue(twoSettled,session.lifecycle().diagnostics().toString());
        assertTrue(session.lifecycle().settled().ownerAt(5,18)>0); assertTrue(session.lifecycle().settled().ownerAt(5,17)>0);
        session.enqueue(()->world.set(5,18,Material.AIR));
        boolean fell=false,resettled=false;
        for(int i=0;i<1500;i++) {
            session.tick(); assertNull(session.failure(),"repeat tick "+i); assertFalse(session.blocked()); assertStoneAccounting(session,1);
            fell|=session.lifecycle().diagnostics().reactivated()>0 && !session.physics().bodies().isEmpty();
            if(fell && session.lifecycle().diagnostics().settled()>=3 && session.physics().bodies().isEmpty()) { resettled=true; break; }
        }
        assertTrue(fell); assertTrue(resettled,session.lifecycle().diagnostics().toString());
        assertEquals(Material.STONE,world.get(5,18)); assertTrue(session.lifecycle().settled().ownerAt(5,18)>0);
        assertEquals(1,session.lifecycle().settled().liveRocks()); assertEquals(1,session.lifecycle().diagnostics().settledRocks());
    }
    @Test void unsupportedStoneAutomaticallyFallsAndSettles() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED);
        for(int y=5;y<19;y++) world.set(5,y,Material.STONE);
        world.set(6,5,Material.STONE);
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        session.enqueue(()->world.set(5,5,Material.AIR));
        boolean rigid=false,done=false;
        for(int i=0;i<900;i++) {
            session.tick(); assertNull(session.failure(),"tick "+i); assertFalse(session.blocked());
            rigid|=!session.physics().bodies().isEmpty();
            if(session.lifecycle().diagnostics().settled()>0) { done=true; break; }
        }
        assertTrue(rigid); assertTrue(done,session.lifecycle().diagnostics().toString());
        assertTrue(session.physics().bodies().isEmpty()); assertEquals(1,session.lifecycle().diagnostics().settledRocks());
        assertEquals(Material.STONE,world.get(6,18)); assertEquals(1,session.lifecycle().settled().ownerAt(6,18));
    }
    @Test void sandStillFallsAfterSettling() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(8,3,Material.STONE); world.set(12,3,Material.STONE);
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<900 && session.lifecycle().diagnostics().settled()<2;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        assertEquals(2,session.lifecycle().diagnostics().settledRocks());
        long before=session.cellularSteps();
        for(int batch=0;batch<3;batch++) {
            world.set(4+batch,1,Material.SAND);
            for(int t=0;t<30;t++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        }
        assertTrue(session.cellularSteps()>=before+90); assertTrue(session.physics().bodies().isEmpty());
        assertEquals(Material.SAND,world.get(4,18)); assertEquals(Material.SAND,world.get(5,18)); assertEquals(Material.SAND,world.get(6,18));
        assertEquals(Material.STONE,world.get(8,18));
        assertEquals(Material.STONE,world.get(12,18)); assertStoneAccounting(session,2);
    }
    @Test void supportLimitDoesNotRetryUntilInputsOrLimitsChange() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE); world.set(5,8,Material.STONE);
        var limits=new SettledSupportCoordinator.Limits(10,1,100,8,3,100,1);
        var session=InteractiveSession.headlessLifecycle(world,false,limits);
        for(int i=0;i<1500 && !session.lifecycle().diagnostics().rejectionReasons().containsKey("support.LIMIT");i++) {
            session.tick(); assertNull(session.failure()); assertFalse(session.blocked());
        }
        assertEquals(1,session.lifecycle().diagnostics().rejectionReasons().get("support.LIMIT"));
        long before=session.cellularSteps();
        for(int i=0;i<30;i++) { session.tick(); assertNull(session.failure()); }
        assertEquals(1,session.lifecycle().diagnostics().rejectionReasons().get("support.LIMIT"));
        assertEquals(before+30,session.cellularSteps());
        session.lifecycle().setSupportLimits(SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<200 && !session.lifecycle().settled().supportTransitionsAllowed();i++) session.tick();
        assertTrue(session.lifecycle().settled().supportTransitionsAllowed());
    }
    @Test void independentStackIsNotReactivated() {
        var world=new World(24,20,World.Mode.BEDROCK_ENABLED);
        for(int x:new int[]{5,15}) { world.set(x,3,Material.STONE); world.set(x,8,Material.STONE); }
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        boolean ready=false;
        for(int i=0;i<2500;i++) { session.tick(); assertNull(session.failure(),"tick "+i); assertFalse(session.blocked());
            if(session.lifecycle().settled().liveRocks()==4 && session.physics().bodies().isEmpty()) { ready=true; break; } }
        assertTrue(ready,session.lifecycle().diagnostics().toString());
        long a=session.lifecycle().settled().ownerAt(15,18),b=session.lifecycle().settled().ownerAt(15,17);
        session.enqueue(()->world.set(5,18,Material.AIR));
        boolean fell=false; for(int i=0;i<600;i++) { session.tick(); assertNull(session.failure(),"tick "+i); assertFalse(session.blocked());
            if(session.lifecycle().diagnostics().reactivated()>0) { fell=true; break; } }
        assertTrue(fell); assertEquals(a,session.lifecycle().settled().ownerAt(15,18)); assertEquals(b,session.lifecycle().settled().ownerAt(15,17));
    }
}

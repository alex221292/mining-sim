package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class RestingStackTest {
    private static void tick(RestingStackBenchmark.Scene scene) { scene.session().tick(); assertNull(scene.session().failure()); }
    private static List<String> replay() {
        var s=RestingStackBenchmark.scene(); var result=new ArrayList<String>(); double maxVelocity=0;
        for(int t=0;t<1000;t++) {
            tick(s);
            for(var b:s.physics().bodies()) {
                maxVelocity=Math.max(maxVelocity,Math.hypot(b.velocityXMetresPerSecond(),b.velocityYMetresPerSecond()));
                assertTrue(b.geometry().bounds(b.xCells(),b.yCells(),b.angleRadians()).maxY()<39.2,"floor penetration tick "+t);
            }
            if(t>=600) assertEquals(3,s.physics().interactionDiagnostics().sleepingBodies(),"tick "+t);
            if(t==399 || t==600 || t==999) for(var b:s.physics().bodies())
                result.add(b.id()+":"+b.xCells()+":"+b.yCells()+":"+b.angleRadians()+":"+b.velocityXMetresPerSecond()+":"+b.velocityYMetresPerSecond()+":"+b.angularVelocityRadiansPerSecond()+":"+b.sleeping());
        }
        assertTrue(maxVelocity<6); assertTrue(s.physics().interactionDiagnostics().maxPenetrationCells()<0.03);
        assertTrue(s.physics().interactionDiagnostics().checks()<300,"sleeping stack must not repeat identical SAT queries");
        assertEquals(70_000_000,s.registry().totalMassMilligrams());
        for(int i=0;i<3;i++) assertEquals(result.get(3+i),result.get(6+i),"sleeping transforms changed");
        return result;
    }
    @Test void stableSleepingStackAndDeterministicReplay() { assertEquals(replay(),replay()); }
    @Test void removalOfStaticSupportWakesStack() {
        var s=RestingStackBenchmark.scene(); for(int x=18;x<=34;x++) s.world().set(x,38,Material.WOOD);
        for(int t=0;t<1000;t++) tick(s); assertEquals(3,s.physics().interactionDiagnostics().sleepingBodies());
        double before=s.physics().body(3).yCells();
        for(int x=18;x<=34;x++) s.world().set(x,38,Material.AIR);
        tick(s); assertTrue(s.physics().interactionDiagnostics().wokenBodies()>0);
        for(int t=0;t<15;t++) tick(s); assertTrue(s.physics().body(3).yCells()>before);
    }
    @Test void incomingBodyWakesContactedSleepers() {
        var s=RestingStackBenchmark.scene(); for(int t=0;t<1000;t++) tick(s);
        assertEquals(3,s.physics().interactionDiagnostics().sleepingBodies());
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        s.registry().insert(shape,23,28,0); boolean woke=false;
        for(int t=0;t<100;t++) { tick(s); woke|=s.physics().interactionDiagnostics().wokenBodies()>0; }
        assertTrue(woke,"incoming body did not wake the sleeping stack"); assertEquals(4,s.physics().bodies().size());
    }
    @Test void fallingBodiesDoNotSleepInFlight() {
        var s=RestingStackBenchmark.scene();
        for(int t=0;t<15;t++) {
            tick(s);
            for(var body:s.physics().bodies()) {
                assertFalse(body.sleeping()); assertTrue(body.velocityYMetresPerSecond()>0);
            }
        }
    }
    @Test void failedPhysicsRestoresPublishedProfileAndStack() {
        var s=RestingStackBenchmark.scene(); tick(s); var profile=s.physics().profile();
        var states=s.physics().bodies().stream().map(RigidBody::state).toList(); var snapshot=s.occupancy().snapshot();
        s.physics().setTestCheckpoint(point->{ if(point==RigidBodySystem.Checkpoint.AFTER_PAIR_QUERY) throw new IllegalStateException("injected stack failure"); });
        assertThrows(IllegalStateException.class,()->s.session().tick());
        assertEquals(profile,s.physics().profile()); assertEquals(states,s.physics().bodies().stream().map(RigidBody::state).toList());
        assertSame(snapshot,s.occupancy().snapshot());
    }
}

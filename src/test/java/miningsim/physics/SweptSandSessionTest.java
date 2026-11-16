package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.app.Brush;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import org.junit.jupiter.api.Test;

class SweptSandSessionTest {
    private static int count(RestingStackBenchmark.Scene scene,Material material) {
        int count=0;
        for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) {
            if(scene.world().get(x,y)==material) { assertFalse(scene.occupancy().snapshot().occupied(x,y)); count++; }
        }
        return count;
    }
    @Test void sweptObstructionRollsBackAndCellularRecoveryAllowsFollowingBrushAndRetry() {
        for(var material:List.of(Material.SAND,Material.WATER,Material.GRAVEL,Material.SOIL)) verifyRecovery(material);
    }
    private String verifyRecovery(Material material) {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) { session.tick(); assertNull(session.failure()); }
        assertEquals(3,scene.physics().interactionDiagnostics().sleepingBodies());
        session.enqueue(()->Brush.paintWorld(scene.world(),50,1,0,Material.SAND,scene.occupancy().snapshot()));
        session.tick(); assertEquals(Material.AIR,scene.world().get(50,1));
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        scene.registry().insert(shape,3,1,0); scene.physics().synchronize(); scene.occupancy().rebuild(scene.physics(),session.coupled().diagnostics().ticks());
        var body=scene.physics().body(4); body.setVelocity(0,60,0); var state=body.state();
        session.enqueue(()->Brush.paintWorld(scene.world(),3,5,0,material,scene.occupancy().snapshot()));
        long successes=session.coupled().diagnostics().ticks();
        long cellular=session.cellularSteps(); var snapshot=scene.occupancy().snapshot();
        session.tick(); assertEquals(CoupledSimulationStep.FailureStage.SWEEP,session.coupled().lastFailureStage());
        assertEquals(state,body.state()); assertSame(snapshot,scene.occupancy().snapshot());
        assertEquals(successes,session.coupled().diagnostics().ticks()); assertEquals(cellular+1,session.cellularSteps());
        assertEquals(CoupledSimulationStep.RecoveryOutcome.OBSTRUCTION_CLEARED,session.coupled().recoveryOutcome());
        assertEquals(1,session.consecutiveFailures());
        assertEquals(Material.AIR,scene.world().get(3,5)); assertFalse(session.blocked());
        assertEquals(material==Material.SAND?2:1,count(scene,Material.SAND));
        if(material!=Material.SAND) assertEquals(1,count(scene,material));
        session.enqueue(()->Brush.paintWorld(scene.world(),40,1,0,Material.SAND,scene.occupancy().snapshot()));
        session.tick(); assertEquals(Material.AIR,scene.world().get(40,1)); assertTrue(session.cellularSteps()>=cellular+2);
        assertEquals(CoupledSimulationStep.FailureStage.SWEEP,session.coupled().lastFailureStage());
        assertEquals(successes,session.coupled().diagnostics().ticks());
        assertEquals(material==Material.SAND?3:2,count(scene,Material.SAND));
        if(material!=Material.SAND) assertEquals(1,count(scene,material));
        session.resume(); session.tick();
        assertEquals(CoupledSimulationStep.FailureStage.SWEEP,session.coupled().lastFailureStage());
        assertEquals(successes,session.coupled().diagnostics().ticks()); assertEquals(cellular+3,session.cellularSteps());
        for(int i=0;i<30 && session.coupled().diagnostics().ticks()==successes;i++) {
            assertFalse(session.blocked(),session.failure()); session.tick();
            assertEquals(material==Material.SAND?3:2,count(scene,Material.SAND));
            if(material!=Material.SAND) assertEquals(1,count(scene,material));
        }
        assertTrue(session.coupled().diagnostics().ticks()>successes,"coupled physics never recovered: "+session.failure());
        int sand=0; for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) if(scene.world().get(x,y)==Material.SAND) sand++;
        assertEquals(material==Material.SAND?3:2,sand);
        if(material!=Material.SAND) {
            int count=0; for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) if(scene.world().get(x,y)==material) count++;
            assertEquals(1,count);
        }
        var result=new StringBuilder();
        for(int y=0;y<scene.world().height();y++) for(int x=0;x<scene.world().width();x++) result.append((char)('A'+scene.world().get(x,y).ordinal()));
        return result.append(scene.physics().bodies().stream().map(RigidBody::state).toList()).append(session.cellularSteps()).toString();
    }
    @Test void sweptRecoveryReplaysDeterministically() { assertEquals(verifyRecovery(Material.SAND),verifyRecovery(Material.SAND)); }
    @Test void unsafeOverlapDoesNotRunCellularRecovery() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        scene.registry().insert(shape,3,1,0); scene.physics().synchronize(); scene.occupancy().rebuild(scene.physics(),session.coupled().diagnostics().ticks());
        scene.physics().body(4).setVelocity(0,60,0);
        // Deliberately bypass the brush's occupancy guard to model inconsistent callers.
        scene.world().set(3,1,Material.SAND); scene.world().set(3,5,Material.SAND);
        long before=session.cellularSteps();
        for(int i=0;i<3;i++) session.tick();
        assertTrue(session.blocked()); assertEquals(before,session.cellularSteps());
        assertEquals(CoupledSimulationStep.RecoveryOutcome.OCCUPANCY_CONFLICT,session.coupled().recoveryOutcome());
        assertEquals(Material.SAND,scene.world().get(3,1)); assertEquals(Material.SAND,scene.world().get(3,5));
    }
    @Test void staleOccupancyDoesNotRunCellularRecovery() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        scene.registry().insert(shape,3,1,0); scene.physics().synchronize(); scene.occupancy().rebuild(scene.physics(),session.coupled().diagnostics().ticks());
        var body=scene.physics().body(4); var old=body.state();
        body.restore(new RigidBody.State(old.x()+1,old.y(),old.angle(),0,60,0,false,0));
        scene.world().set(4,5,Material.SAND); long before=session.cellularSteps();
        for(int i=0;i<3;i++) session.tick();
        assertTrue(session.blocked()); assertEquals(before,session.cellularSteps());
        assertEquals(CoupledSimulationStep.RecoveryOutcome.OCCUPANCY_MISMATCH,session.coupled().recoveryOutcome());
        assertEquals(Material.SAND,scene.world().get(4,5));
    }
    @Test void stationaryObstructionRemainsVisibleAndRetryNeedsObstructionRemoval() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        for(int x=0;x<20;x++) scene.world().set(x,6,Material.WOOD);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        scene.registry().insert(shape,3,5,0); scene.physics().synchronize(); scene.occupancy().rebuild(scene.physics(),session.coupled().diagnostics().ticks());
        var body=scene.physics().body(4); body.setVelocity(60,0,0); var before=body.state();
        session.enqueue(()->Brush.paintWorld(scene.world(),7,5,0,Material.SAND,scene.occupancy().snapshot()));
        session.enqueue(()->Brush.paintWorld(scene.world(),40,1,0,Material.WATER,scene.occupancy().snapshot()));
        for(int i=0;i<3;i++) session.tick();
        assertTrue(session.blocked(),session.failure()); assertEquals(CoupledSimulationStep.FailureStage.SWEEP,session.coupled().lastFailureStage());
        assertEquals(3,session.consecutiveFailures());
        assertEquals(3,session.stalledRecoveryAttempts());
        assertEquals(Material.AIR,scene.world().get(40,1),"unrelated water should not conceal the persistent blockage");
        assertEquals(Material.SAND,scene.world().get(7,5)); assertEquals(before,body.state());
        session.resume(); session.tick(); assertNotNull(session.failure(),"retry alone must not conceal unresolved obstruction");
        session.enqueue(()->Brush.paintWorld(scene.world(),7,5,0,Material.AIR,scene.occupancy().snapshot()));
        session.resume(); session.tick(); assertNull(session.failure()); assertFalse(session.blocked());
        assertTrue(body.xCells()>before.x());
    }
}

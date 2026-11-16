package miningsim.simulation;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.physics.RestingStackBenchmark;
import miningsim.physics.CoupledSimulationStep;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryAtomicityTest {
    private static String snapshot(World world) {
        var out=new StringBuilder();
        for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) out.append((char)('A'+world.get(x,y).ordinal()));
        return out.toString();
    }
    private static World world() {
        var world=new World(12,12,World.Mode.BEDROCK_ENABLED);
        world.set(5,1,Material.SAND); world.set(6,1,Material.WATER); world.set(3,3,Material.GRAVEL);
        return world;
    }
    @Test void injectedMidStepFailureDoesNotPublishAndPreservesRng() {
        var actual=world(); var expected=world();
        var simulation=new Simulation(actual,17); var control=new Simulation(expected,17);
        String before=snapshot(actual); long revision=actual.mutationRevision(); long terrain=actual.rigidTerrainRevision();
        simulation.setRecoveryTestCheckpoint(()->{ throw new IllegalStateException("injected after shadow move"); });
        assertFalse(simulation.stepRecoveryAtomically(null));
        assertEquals(before,snapshot(actual)); assertEquals(revision,actual.mutationRevision()); assertEquals(terrain,actual.rigidTerrainRevision());
        simulation.setRecoveryTestCheckpoint(null);
        assertTrue(simulation.stepRecoveryAtomically(null)); control.step();
        assertEquals(snapshot(expected),snapshot(actual));
        for(int i=0;i<20;i++) { simulation.step(); control.step(); assertEquals(snapshot(expected),snapshot(actual)); }
    }
    @Test void rejectedBatchDoesNotPublishOrConsumeRandomness() {
        var actual=world(); var expected=world(); var allowed=new boolean[]{false};
        actual.setChangeSink(new World.ChangeSink() {
            public void changed(int x,int y,byte a,byte b) { fail("rejected batch must not notify"); }
            public void invalidated() { }
            public boolean allowBatch() { return allowed[0]; }
        });
        var simulation=new Simulation(actual,31); var control=new Simulation(expected,31);
        var before=snapshot(actual); long revision=actual.mutationRevision();
        assertFalse(simulation.stepRecoveryAtomically(null)); assertEquals(before,snapshot(actual)); assertEquals(revision,actual.mutationRevision());
        allowed[0]=true; actual.setChangeSink(null);
        assertTrue(simulation.stepRecoveryAtomically(null)); control.step(); assertEquals(snapshot(expected),snapshot(actual));
    }
    @Test void oversizedAndActiveRegionRecoveryRefusesWithoutMutation() {
        var oversized=new World(2001,1000,World.Mode.BEDROCK_ENABLED); oversized.set(5,1,Material.SAND);
        var large=new Simulation(oversized,7); long revision=oversized.mutationRevision();
        assertFalse(large.stepRecoveryAtomically(null)); assertEquals(revision,oversized.mutationRevision());
        assertEquals(Material.SAND,oversized.get(5,1));
        var world=world(); var active=new Simulation(world,7,Simulation.Mode.ACTIVE_REGIONS,false);
        var before=snapshot(world); assertFalse(active.stepRecoveryAtomically(null)); assertEquals(before,snapshot(world));
    }
    @Test void injectedRecoveryFailureAfterRealSweepPreservesPhysicsAndWorld() {
        var scene=RestingStackBenchmark.scene(); var session=scene.session();
        for(int i=0;i<500;i++) session.tick();
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        scene.registry().insert(shape,3,1,0); scene.physics().synchronize(); scene.occupancy().rebuild(scene.physics(),scene.coupling().diagnostics().ticks());
        var body=scene.physics().body(4); body.setVelocity(0,60,0);
        scene.world().set(3,5,Material.SAND); scene.world().set(40,1,Material.SAND);
        var cellSimulation=new Simulation(scene.world(),12345);
        var occupancy=scene.occupancy().snapshot(); var initial=snapshot(scene.world());
        long revision=scene.world().mutationRevision(),terrain=scene.world().rigidTerrainRevision();
        double x=body.xCells(),y=body.yCells(),vx=body.velocityXMetresPerSecond(),vy=body.velocityYMetresPerSecond();
        assertThrows(IllegalStateException.class,scene.coupling()::step);
        assertEquals(CoupledSimulationStep.FailureStage.SWEEP,scene.coupling().lastFailureStage());
        cellSimulation.setRecoveryTestCheckpoint(()->{ throw new IllegalStateException("injected mid-step"); });
        assertFalse(scene.coupling().stepCellsAfterSweepRollback(cellSimulation));
        assertEquals(CoupledSimulationStep.RecoveryOutcome.CELLULAR_FAILED,scene.coupling().recoveryOutcome());
        assertEquals(initial,snapshot(scene.world())); assertEquals(revision,scene.world().mutationRevision()); assertEquals(terrain,scene.world().rigidTerrainRevision());
        assertSame(occupancy,scene.occupancy().snapshot()); assertEquals(x,body.xCells()); assertEquals(y,body.yCells());
        assertEquals(vx,body.velocityXMetresPerSecond()); assertEquals(vy,body.velocityYMetresPerSecond());
        cellSimulation.setRecoveryTestCheckpoint(null);
        assertThrows(IllegalStateException.class,scene.coupling()::step);
        assertTrue(scene.coupling().stepCellsAfterSweepRollback(cellSimulation));
        assertEquals(Material.AIR,scene.world().get(3,5));
    }
}

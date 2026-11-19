package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettlingPublicationTest {
    @Test void vetoAndPublicationExposeWholeOldAndNewStates() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10); while(topology.pending()) topology.advance(100);
        var registry=new FragmentRegistry(2); var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0)));
        var fragment=registry.insert(descriptor,3,14,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults()); var eligibility=new SettlingEligibility(120,1000);
        boolean eligible=false;
        for(int i=0;i<600 && !eligible;i++) { coupled.step(); eligible=eligibility.observe(physics.body(1),physics,coupled,world,topology,settled)==SettlingEligibility.Status.ELIGIBLE; }
        assertTrue(eligible);
        var body=physics.body(1); var old=occupancy.snapshot(); long worldRevision=world.mutationRevision(),mass=registry.totalMassMilligrams();
        var result=SettlingCommit.prepare(1,physics,coupled,world,occupancy,topology,settled,eligibility,new SettledRockPlacementPlanner.Limits(10,10000,0.3,0.9));
        assertEquals(SettlingCommit.Status.READY,result.status(),result.reason()); var plan=result.prepared();
        var change=plan.changes().getFirst(); boolean[] veto={true}; int[] calls={0,0};
        topology.setTestBatchObserver(()->{
            calls[0]++; assertSame(body,physics.body(1)); assertSame(old,occupancy.snapshot());
            assertEquals(List.of(fragment),registry.snapshot()); assertEquals(Material.AIR,world.get(change.x(),change.y()));
            assertEquals(0,settled.ownerAt(change.x(),change.y())); return !veto[0];
        },()->{
            calls[1]++; assertNull(physics.body(1)); assertTrue(registry.snapshot().isEmpty());
            assertNotSame(old,occupancy.snapshot()); assertEquals(Material.STONE,world.get(change.x(),change.y()));
            assertEquals(plan.settledId(),settled.ownerAt(change.x(),change.y()));
            assertEquals(mass,settled.rock(plan.settledId()).massMilligrams());
        });
        assertEquals(World.BatchResult.REJECTED,world.commitSettling(plan));
        assertEquals(worldRevision,world.mutationRevision()); assertEquals(0,calls[1]); assertSame(body,physics.body(1));
        veto[0]=false; assertEquals(World.BatchResult.CHANGED,world.commitSettling(plan));
        assertArrayEquals(new int[]{2,1},calls); topology.setTestBatchObserver(null,null);
        assertEquals(World.BatchResult.REJECTED,world.commitSettling(plan));
    }
}

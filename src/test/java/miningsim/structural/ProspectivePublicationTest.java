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

class ProspectivePublicationTest {
    private record Fixture(World world,RockConnectivity topology,SettledRockRegistry settled,FragmentRegistry registry,
                           RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,CoupledSimulationStep coupled,GroupSettlingCommit.Prepared plan) { }
    private static Fixture ready() {
        var w=new World(20,20,World.Mode.BEDROCK_ENABLED); var t=new RockConnectivity(w); var settled=new SettledRockRegistry(w,t,10,20);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        w.set(5,18,Material.STONE); settled.registerExisting(99,shape,5,18); while(t.pending()) t.advance(1000);
        var registry=new FragmentRegistry(2); registry.insert(shape,5,13,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(w,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(w,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(w,physics,occupancy,CellularDisplacementPlanner.Limits.defaults());
        var observer=new GroupSettlingEligibility(List.of(1L)); boolean eligible=false;
        for(int i=0;i<700 && !eligible;i++) { coupled.step(); eligible=observer.observe(physics,coupled,w,t,settled); }
        assertTrue(eligible); var result=GroupSettlingCommit.prepareGroupSettling(List.of(1L),physics,coupled,w,occupancy,t,settled,observer,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,result.status(),result.reason());
        return new Fixture(w,t,settled,registry,physics,occupancy,coupled,result.prepared());
    }
    @Test void vetoLeavesWholeProspectiveStateUnchanged() {
        var f=ready(); var snapshot=f.occupancy().snapshot(); var body=f.physics().body(1); var diag=f.coupled().diagnostics();
        long revision=f.world().mutationRevision(),owner=f.settled().ownerAt(5,18),mass=f.registry().totalMassMilligrams();
        f.topology().setTestBatchObserver(()->false,null);
        assertEquals(World.BatchResult.REJECTED,f.world().commitGroupSettling(f.plan()));
        assertEquals(revision,f.world().mutationRevision()); assertEquals(owner,f.settled().ownerAt(5,18));
        assertEquals(0,f.settled().ownerAt(5,17)); assertEquals(Material.AIR,f.world().get(5,17));
        assertSame(body,f.physics().body(1)); assertSame(snapshot,f.occupancy().snapshot());
        assertEquals(mass,f.registry().totalMassMilligrams()); assertEquals(diag,f.coupled().diagnostics());
        f.topology().setTestBatchObserver(null,null);
    }
    @Test void changingSettledSupportMakesPreparedPlanStale() {
        var f=ready(); var snapshot=f.occupancy().snapshot(); var body=f.physics().body(1); var diag=f.coupled().diagnostics();
        assertEquals(World.Mutation.CHANGED,f.world().set(5,18,Material.AIR)); long revision=f.world().mutationRevision();
        assertEquals(World.BatchResult.REJECTED,f.world().commitGroupSettling(f.plan()));
        assertEquals(revision,f.world().mutationRevision()); assertEquals(0,f.settled().liveRocks());
        assertEquals(Material.AIR,f.world().get(5,17)); assertEquals(0,f.settled().ownerAt(5,17));
        assertSame(body,f.physics().body(1)); assertSame(snapshot,f.occupancy().snapshot());
        assertEquals(1,f.registry().size()); assertEquals(diag,f.coupled().diagnostics());
    }
}

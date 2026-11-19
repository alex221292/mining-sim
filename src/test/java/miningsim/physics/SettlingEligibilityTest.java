package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettlingEligibilityTest {
    @Test void sleepingBodyRequiresContinuousSuccessfulQuietTicks() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10); while(topology.pending()) topology.advance(100);
        var registry=new FragmentRegistry(2);
        registry.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0))),3,14,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults());
        var eligibility=new SettlingEligibility(120,1000); boolean slept=false,eligible=false;
        for(int i=0;i<600 && !eligible;i++) {
            coupled.step(); var body=physics.body(1);
            var status=eligibility.observe(body,physics,coupled,world,topology,settled);
            if(body.sleeping()) slept=true;
            if(status==SettlingEligibility.Status.ELIGIBLE) { assertTrue(slept); assertEquals(120,eligibility.observedQuietTicks()); eligible=true; }
            else assertTrue(eligibility.observedQuietTicks()<120);
        }
        assertTrue(eligible,"body never reached the supported quiet window");
        physics.body(1).setVelocity(1,0,0); coupled.step();
        assertEquals(SettlingEligibility.Status.NOT_SLEEPING,eligibility.observe(physics.body(1),physics,coupled,world,topology,settled));
        assertEquals(0,eligibility.observedQuietTicks());
    }
    @Test void sleepingBodyTransfersToWorldWithExactOwnershipAndMass() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10); while(topology.pending()) topology.advance(100);
        var registry=new FragmentRegistry(2);
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0)));
        var fragment=registry.insert(descriptor,3,14,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults());
        var eligibility=new SettlingEligibility(120,1000); boolean eligible=false;
        for(int i=0;i<600 && !eligible;i++) {
            coupled.step(); eligible=eligibility.observe(physics.body(1),physics,coupled,world,topology,settled)==SettlingEligibility.Status.ELIGIBLE;
        }
        assertTrue(eligible);
        var before=physics.body(1).state(); var snapshot=occupancy.snapshot();
        var result=SettlingCommit.prepare(1,physics,coupled,world,occupancy,topology,settled,eligibility,new SettledRockPlacementPlanner.Limits(10,10000,0.3,0.90));
        assertEquals(SettlingCommit.Status.READY,result.status(),result.reason());
        assertEquals(before,physics.body(1).state()); assertSame(snapshot,occupancy.snapshot());
        assertEquals(World.BatchResult.CHANGED,world.commitSettling(result.prepared()));
        assertNull(physics.body(1)); assertTrue(registry.snapshot().isEmpty()); assertEquals(0,registry.totalMassMilligrams());
        assertEquals(0,occupancy.snapshot().diagnostics().indexedBodies());
        var rock=settled.rock(result.prepared().settledId());
        assertEquals(fragment.id(),rock.sourceFragmentId()); assertEquals(descriptor.massMilligrams(),rock.massMilligrams());
        assertEquals(SubstanceRegistry.MARBLE,rock.descriptor().substance().id()); assertEquals(1,rock.remainingCells());
        var cell=settled.cells(rock.id()).getFirst(); assertEquals(miningsim.world.Material.STONE,world.get(cell.x(),cell.y()));
        assertEquals(rock.id(),settled.ownerAt(cell.x(),cell.y())); assertFalse(occupancy.snapshot().occupied(cell.x(),cell.y()));
        assertEquals(World.BatchResult.REJECTED,world.commitSettling(result.prepared()));
    }
}

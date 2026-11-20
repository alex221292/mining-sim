package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.simulation.CellularDisplacementPlanner;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class LargeSupportCascadeTest {
    @Test void settledStackLosesRemovableFoundationAndFalls() {
        var original=LargeCompoundBenchmark.fixture("large").session(); var originalTopology=original.structural().connectivity();
        var originalSettled=new SettledRockRegistry(original.world(),originalTopology,10,1024); var ids=List.of(1L,2L,3L);
        var eligibility=new GroupSettlingEligibility(ids);
        for(int i=0;i<500;i++) { original.tick(); assertNull(original.failure()); eligibility.observe(original.physics(),original.coupled(),original.world(),originalTopology,originalSettled); }
        var plan=GroupSettlingCommit.prepareGroupSettling(ids,original.physics(),original.coupled(),original.world(),original.occupancy(),originalTopology,originalSettled,eligibility,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,plan.status(),plan.reason()); assertEquals(World.BatchResult.CHANGED,original.world().commitGroupSettling(plan.prepared()));
        var world=new World(64,64,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,1024);
        var foundations=new java.util.TreeSet<Integer>();
        for(var rock:originalSettled.snapshot()) for(var cell:originalSettled.cells(rock.id())) {
            if(cell.y()==62) foundations.add(cell.x());
            world.set(cell.x(),cell.y()-1,Material.STONE);
        }
        for(int x:foundations) world.set(x,62,Material.STONE);
        for(var rock:originalSettled.snapshot()) settled.registerExisting(rock.sourceFragmentId(),rock.descriptor(),rock.originX(),rock.originY()-1);
        assertEquals(794,settled.snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum());
        long mass=settled.snapshot().stream().mapToLong(SettledRockRegistry.Rock::massMilligrams).sum();
        var registry=new FragmentRegistry(8);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,100000,1000000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(1_000_000,10_000_000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coordinator=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,new SettledSupportCoordinator.Limits(4,10,5000,64,3,1000,1));
        coordinator.enableProfiling(true); long initialStart=System.nanoTime();
        boolean initialized=false;
        for(int i=0;i<1000;i++) { var state=coordinator.step(0); if(state.status()==SettledSupportCoordinator.Status.IDLE) { initialized=true; break; }
            assertNotEquals(SettledSupportCoordinator.Status.COMMITTED,state.status()); assertNotEquals(SettledSupportCoordinator.Status.BLOCKED,state.status(),state.reason()); }
        long initialNanos=System.nanoTime()-initialStart;
        assertTrue(initialized,"Initial support analysis did not finish");
        assertTrue(physics.bodies().isEmpty());
        var changes=new ArrayList<World.CellChange>(); for(int x:foundations) changes.add(new World.CellChange(x,62,Material.STONE,Material.AIR));
        assertEquals(World.BatchResult.CHANGED,world.replaceAllIfMatches(changes));
        boolean committed=false; int analysisTicks=0; long incrementalStart=System.nanoTime();
        for(;analysisTicks<1000;analysisTicks++) {
            var state=coordinator.step(0);
            if(state.status()==SettledSupportCoordinator.Status.COMMITTED) { assertEquals(ids,state.reactivated()); committed=true; break; }
            assertTrue(physics.bodies().isEmpty()); assertNotEquals(SettledSupportCoordinator.Status.BLOCKED,state.status(),state.reason());
            assertNotEquals(SettledSupportCoordinator.Status.LIMIT,state.status(),state.reason());
        }
        assertTrue(committed); assertTrue(analysisTicks>1); assertEquals(3,physics.bodies().size()); assertEquals(mass,registry.totalMassMilligrams());
        long incrementalNanos=System.nanoTime()-incrementalStart; var profile=coordinator.profile();
        assertEquals(0,settled.snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum());
        var start=physics.bodies().stream().map(RigidBody::yCells).toList();
        var coupled=new CoupledSimulationStep(world,physics,occupancy,CellularDisplacementPlanner.Limits.defaults(),true);
        coupled.step(); assertNull(coupled.lastFailure()); long firstPhysics=coupled.phaseTimes().physicsNanos(),checks=physics.interactionDiagnostics().checks();
        boolean bedrockContact=physics.diagnostics().contacts()>0;
        for(int i=1;i<60;i++) { coupled.step(); assertNull(coupled.lastFailure()); bedrockContact|=physics.diagnostics().contacts()>0; }
        System.out.printf(java.util.Locale.ROOT,"stack 64x64 134/134/526 initial=%.3fms incremental=%.3fms build=%.3fms stability=%.3fms cascade=%.3fms prepare=%.3fms commit=%.3fms firstPhysics=%.3fms SAT-before/after=0/%d rocks=%d cells=%d work=%d analysisTicks=%d%n",
                initialNanos/1e6,incrementalNanos/1e6,profile.buildNanos()/1e6,profile.stabilityNanos()/1e6,profile.cascadeNanos()/1e6,
                profile.preparationNanos()/1e6,profile.commitNanos()/1e6,firstPhysics/1e6,checks,profile.rocks(),profile.cells(),profile.graphWork(),analysisTicks+1);
        assertTrue(java.util.stream.IntStream.range(0,3).anyMatch(i->physics.bodies().get(i).yCells()>start.get(i)+0.01));
        assertTrue(bedrockContact,"Falling bodies never contacted the remaining BEDROCK");
    }
}

package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.*;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettledSupportCoordinatorTest {
    private record Fixture(World world,RockConnectivity topology,SettledRockRegistry settled,FragmentRegistry registry,
                           RigidBodySystem physics,RigidBodyOccupancyIndex occupancy,SettledSupportCoordinator coordinator) { }
    private static long rock(World world,SettledRockRegistry settled,int x,int y,long source) {
        world.set(x,y,Material.STONE);
        var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        return settled.registerExisting(source,descriptor,x,y);
    }
    private static Fixture fixture(int work) {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,20,10);
        world.set(3,18,Material.STONE); rock(world,settled,3,17,1); rock(world,settled,3,16,2); rock(world,settled,3,15,3);
        rock(world,settled,12,18,4);
        var registry=new FragmentRegistry(10);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coordinator=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,new SettledSupportCoordinator.Limits(2,20,100,work,10,100,1));
        return new Fixture(world,topology,settled,registry,physics,occupancy,coordinator);
    }
    private static void untilIdle(Fixture f) {
        for(int i=0;i<2000;i++) {
            var result=f.coordinator().step(0);
            assertNotEquals(SettledSupportCoordinator.Status.BLOCKED,result.status(),result.reason());
            assertNotEquals(SettledSupportCoordinator.Status.LIMIT,result.status(),result.reason());
            if(result.status()==SettledSupportCoordinator.Status.IDLE) return;
        }
        fail("Coordinator did not finish");
    }
    @Test void removingSupportReactivatesOnlyDependentStack() {
        var f=fixture(1); untilIdle(f); assertTrue(f.physics().bodies().isEmpty());
        assertEquals(World.Mutation.CHANGED,f.world().set(3,18,Material.AIR));
        boolean committed=false; int steps=0;
        for(;steps<1000;steps++) {
            var result=f.coordinator().step(0);
            if(result.status()==SettledSupportCoordinator.Status.COMMITTED) {
                assertEquals(List.of(1L,2L,3L),result.reactivated()); committed=true; break;
            }
            assertEquals(0,f.physics().bodies().size());
            assertNotEquals(SettledSupportCoordinator.Status.BLOCKED,result.status(),result.reason());
        }
        assertTrue(committed); assertTrue(steps>3); assertEquals(3,f.physics().bodies().size());
        assertEquals(4,f.settled().ownerAt(12,18)); assertEquals(Material.STONE,f.world().get(12,18));
        assertEquals(3,f.registry().size()); untilIdle(f);
    }
    @Test void reanalysisRestartsAfterMutationAndIgnoresSandMovement() {
        var f=fixture(1); untilIdle(f); f.world().set(3,18,Material.AIR);
        var first=f.coordinator().step(0); assertEquals(SettledSupportCoordinator.Status.IN_PROGRESS,first.status());
        f.world().set(1,1,Material.SAND);
        var second=f.coordinator().step(0); assertNotEquals(SettledSupportCoordinator.Status.BLOCKED,second.status());
        f.world().set(3,18,Material.STONE);
        untilIdle(f); assertTrue(f.physics().bodies().isEmpty());
        assertEquals(1,f.settled().ownerAt(3,17));
    }
    @Test void pendingAnalysisQuarantinesTransitionsButNotCellularTicks() {
        var f=fixture(1); untilIdle(f); f.world().set(3,18,Material.AIR);
        var single=SettledRockReactivation.prepareReactivation(1,f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),0);
        assertEquals(SettledRockReactivation.Status.PREPARATION_FAILED,single.status());
        f.world().set(1,1,Material.SAND); var cellular=new miningsim.simulation.Simulation(f.world(),42);
        cellular.step(); assertEquals(Material.AIR,f.world().get(1,1)); assertEquals(Material.SAND,f.world().get(1,2));
        assertEquals(0,f.physics().bodies().size());
    }
    @Test void directReactivationCannotSplitASettledStackEvenWhenIdle() {
        var f=fixture(100); untilIdle(f); assertTrue(f.settled().supportTransitionsAllowed());
        var single=SettledRockReactivation.prepareReactivation(1,f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),0);
        assertEquals(SettledRockReactivation.Status.PREPARATION_FAILED,single.status());
        var group=GroupRockReactivation.prepareGroupReactivation(List.of(1L),f.world(),f.topology(),f.settled(),f.physics(),f.occupancy(),0,new GroupRockReactivation.Limits(3,10));
        assertEquals(GroupRockReactivation.Status.STALE,group.status());
        assertEquals(4,f.settled().snapshot().stream().mapToInt(SettledRockRegistry.Rock::remainingCells).sum());
        assertTrue(f.physics().bodies().isEmpty());
    }
    @Test void hardLimitRetainsDirtyWorkAndCanBeRaised() {
        var f=fixture(2); untilIdle(f);
        f.coordinator().setLimits(new SettledSupportCoordinator.Limits(2,20,100,2,2,100,1));
        f.world().set(3,18,Material.AIR); SettledSupportCoordinator.Result limited=null;
        for(int i=0;i<1000;i++) { limited=f.coordinator().step(0); if(limited.status()==SettledSupportCoordinator.Status.LIMIT) break; }
        assertNotNull(limited); assertEquals(SettledSupportCoordinator.Status.LIMIT,limited.status()); assertTrue(limited.pendingIds()>0);
        assertTrue(f.physics().bodies().isEmpty()); assertFalse(f.settled().supportTransitionsAllowed());
        f.world().set(1,1,Material.SAND); new miningsim.simulation.Simulation(f.world(),1).step(); assertEquals(Material.SAND,f.world().get(1,2));
        f.coordinator().setLimits(new SettledSupportCoordinator.Limits(2,20,100,2,3,100,1));
        boolean committed=false; for(int i=0;i<1000;i++) if(f.coordinator().step(0).status()==SettledSupportCoordinator.Status.COMMITTED) { committed=true; break; }
        assertTrue(committed); assertEquals(3,f.physics().bodies().size());
    }
    @Test void resultsDoNotDependOnAnalysisTickBudget() {
        var slow=fixture(1); var fast=fixture(1000); untilIdle(slow); untilIdle(fast);
        slow.world().set(3,18,Material.AIR); fast.world().set(3,18,Material.AIR);
        List<Long> a=List.of(),b=List.of();
        for(int i=0;i<1000;i++) { var r=slow.coordinator().step(0); if(r.status()==SettledSupportCoordinator.Status.COMMITTED) { a=r.reactivated(); break; } }
        for(int i=0;i<1000;i++) { var r=fast.coordinator().step(0); if(r.status()==SettledSupportCoordinator.Status.COMMITTED) { b=r.reactivated(); break; } }
        assertEquals(List.of(1L,2L,3L),a); assertEquals(a,b);
        assertEquals(slow.registry().snapshot().stream().map(f->f.id()+":"+f.massMilligrams()+":"+f.originX()+":"+f.originY()).toList(),
                fast.registry().snapshot().stream().map(f->f.id()+":"+f.massMilligrams()+":"+f.originX()+":"+f.originY()).toList());
    }
    @Test void legacyCoordinatorLeavesWorldUntouched() {
        var world=new World(10,10,World.Mode.LEGACY); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,2,2);
        long id=rock(world,settled,3,4,1); var registry=new FragmentRegistry(2); var physics=new RigidBodySystem(registry);
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coordinator=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,new SettledSupportCoordinator.Limits(1,2,2,1,2,2,1));
        for(int i=0;i<10;i++) assertEquals(SettledSupportCoordinator.Status.IDLE,coordinator.step(0).status());
        assertEquals(id,settled.ownerAt(3,4)); assertEquals(Material.STONE,world.get(3,4)); assertTrue(physics.bodies().isEmpty());
        assertTrue(settled.supportTransitionsAllowed());
    }
    @Test void independentlySupportedBridgeRemainsStatic() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        for(int x=2;x<=4;x++) world.set(x,18,Material.STONE);
        long a=rock(world,settled,2,17,1);
        var bShape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0)));
        world.set(3,17,Material.STONE); world.set(4,17,Material.STONE); long b=settled.registerExisting(2,bShape,3,17);
        var bridge=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.MARBLE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0)));
        for(int x=2;x<=4;x++) world.set(x,16,Material.STONE); long upper=settled.registerExisting(3,bridge,2,16);
        var registry=new FragmentRegistry(5); var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coordinator=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,new SettledSupportCoordinator.Limits(5,10,100,3,5,100,1));
        var f=new Fixture(world,topology,settled,registry,physics,occupancy,coordinator); untilIdle(f);
        world.set(2,18,Material.AIR); List<Long> activated=List.of();
        for(int i=0;i<500;i++) { var result=coordinator.step(0); if(result.status()==SettledSupportCoordinator.Status.COMMITTED) { activated=result.reactivated(); break; } }
        assertEquals(List.of(a),activated); assertEquals(0,settled.rock(a).remainingCells());
        assertEquals(2,settled.rock(b).remainingCells()); assertEquals(3,settled.rock(upper).remainingCells()); assertEquals(1,physics.bodies().size());
    }
    @Test void vetoAndExceptionRetainCascadeAndCallbacksSeeCompleteState() {
        var f=fixture(100); untilIdle(f); f.world().set(3,18,Material.AIR);
        f.topology().setTestBatchObserver(()->{ assertFalse(f.settled().supportTransitionsAllowed()); throw new IllegalStateException("injected"); },null);
        SettledSupportCoordinator.Result result=null;
        for(int i=0;i<100;i++) { result=f.coordinator().step(0); if(result.status()==SettledSupportCoordinator.Status.BLOCKED) break; }
        assertNotNull(result); assertEquals(SettledSupportCoordinator.Status.BLOCKED,result.status());
        assertEquals(0,f.physics().bodies().size()); assertEquals(1,f.settled().ownerAt(3,17));
        f.topology().setTestBatchObserver(()->{ assertFalse(f.settled().supportTransitionsAllowed()); return false; },null);
        for(int i=0;i<100;i++) { result=f.coordinator().step(0); if(result.status()==SettledSupportCoordinator.Status.BLOCKED) break; }
        assertEquals(SettledSupportCoordinator.Status.BLOCKED,result.status()); assertEquals(0,f.physics().bodies().size());
        boolean[] called={false}; f.topology().setTestBatchObserver(()->{assertEquals(0,f.physics().bodies().size()); return true;},()->{
            called[0]=true; assertEquals(3,f.physics().bodies().size()); assertEquals(0,f.settled().ownerAt(3,17));
            assertEquals(4,f.settled().ownerAt(12,18)); assertFalse(f.settled().supportTransitionsAllowed());
        });
        for(int i=0;i<100;i++) { result=f.coordinator().step(0); if(result.status()==SettledSupportCoordinator.Status.COMMITTED) break; }
        assertEquals(SettledSupportCoordinator.Status.COMMITTED,result.status()); assertTrue(called[0]);
        f.topology().setTestBatchObserver(null,null); untilIdle(f);
    }
    @Test void dirtyUpperIdBeforeLowerDoesNotSplitCascade() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        world.set(3,18,Material.STONE); world.set(4,18,Material.STONE);
        var pair=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0)));
        world.set(3,16,Material.STONE); world.set(4,16,Material.STONE); long upper=settled.registerExisting(1,pair,3,16);
        world.set(3,17,Material.STONE); world.set(4,17,Material.STONE); long lower=settled.registerExisting(2,pair,3,17);
        var registry=new FragmentRegistry(5); var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coordinator=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,new SettledSupportCoordinator.Limits(5,10,100,2,5,100,1));
        var f=new Fixture(world,topology,settled,registry,physics,occupancy,coordinator); untilIdle(f);
        assertEquals(World.BatchResult.CHANGED,world.replaceAllIfMatches(List.of(
                new World.CellChange(3,17,Material.STONE,Material.AIR),new World.CellChange(4,18,Material.STONE,Material.AIR))));
        List<Long> activated=List.of();
        for(int i=0;i<500;i++) { var result=coordinator.step(0); if(result.status()==SettledSupportCoordinator.Status.COMMITTED) { activated=result.reactivated(); break; }
            assertTrue(physics.bodies().isEmpty()); }
        assertEquals(List.of(upper,lower),activated); assertEquals(2,physics.bodies().size());
    }
    @Test void cascadeBodiesCanResettleAndBeReactivatedAgain() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        world.set(3,18,Material.STONE); rock(world,settled,3,17,1); rock(world,settled,3,16,2);
        var registry=new FragmentRegistry(6); var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,miningsim.simulation.CellularDisplacementPlanner.Limits.defaults());
        var coordinator=new SettledSupportCoordinator(world,topology,settled,physics,occupancy,new SettledSupportCoordinator.Limits(5,10,100,20,5,100,1));
        var f=new Fixture(world,topology,settled,registry,physics,occupancy,coordinator); untilIdle(f);
        world.set(3,18,Material.AIR); boolean committed=false;
        for(int i=0;i<500;i++) if(coordinator.step(0).status()==SettledSupportCoordinator.Status.COMMITTED) { committed=true; break; }
        assertTrue(committed); assertEquals(2,physics.bodies().size()); untilIdle(f);
        var ids=physics.bodies().stream().map(RigidBody::id).toList(); var eligibility=new GroupSettlingEligibility(ids); boolean quiet=false;
        for(int i=0;i<500 && !quiet;i++) { coupled.step(); assertNull(coupled.lastFailure()); quiet=eligibility.observe(physics,coupled,world,topology,settled); }
        assertTrue(quiet); var plan=GroupSettlingCommit.prepareGroupSettling(ids,physics,coupled,world,occupancy,topology,settled,eligibility,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,plan.status(),plan.reason()); assertEquals(World.BatchResult.CHANGED,world.commitGroupSettling(plan.prepared()));
        assertTrue(physics.bodies().isEmpty()); untilIdle(f);
        var lower=settled.cells(3).getFirst(); assertEquals(World.Mutation.CHANGED,world.set(lower.x(),lower.y(),Material.AIR));
        List<Long> second=List.of(); for(int i=0;i<500;i++) { var result=coordinator.step(0); if(result.status()==SettledSupportCoordinator.Status.COMMITTED) { second=result.reactivated(); break; } }
        assertEquals(List.of(4L),second); assertEquals(1,physics.bodies().size()); assertEquals(physics.bodies().getFirst().id(),settled.rock(4).reactivatedBodyId());
    }
}

package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class GroupSettlingSpikeTest {
    private static final long BUDGET=2_000_000;
    private static GroupSettlingPlanner.Result stack() {
        var fixture=LargeCompoundBenchmark.fixture("large"); var session=fixture.session();
        var settled=new SettledRockRegistry(session.world(),session.structural().connectivity(),10,1024);
        for(int i=0;i<400;i++) { session.tick(); assertNull(session.failure()); }
        assertEquals(List.of(134,134,526),fixture.sizes());
        assertEquals(3,session.physics().interactionDiagnostics().sleepingBodies());
        var bodies=session.physics().bodies(); var occupancy=session.occupancy().snapshot(); var world=session.world();
        var originalStates=bodies.stream().map(RigidBody::state).toList(); long worldRevision=world.mutationRevision();
        long independentNanos=0,independentWork=0; int independentlyPlaced=0,cells=0; var conflicts=new ArrayList<Integer>();
        for(var body:bodies) {
            var pose=new SettledRockPlacementPlanner.Pose(body.id(),body.fragment().descriptor(),body.xCells(),body.yCells(),body.angleRadians());
            long start=System.nanoTime(); var candidate=SettledRockPlacementPlanner.prepare(pose,world,occupancy,settled,new SettledRockPlacementPlanner.Limits(1024,1_000_000,0.3,0.9));
            independentNanos+=System.nanoTime()-start; independentWork+=candidate.checks();
            if(candidate.status()==SettledRockPlacementPlanner.Status.READY) { independentlyPlaced++; cells+=candidate.plan().cells().size(); }
            int x0=(int)Math.rint(body.xCells()-body.geometry().localCenterX()),y0=(int)Math.rint(body.yCells()-body.geometry().localCenterY()),foreign=0;
            for(var c:body.fragment().descriptor().cells()) for(long id:occupancy.bodyIdsAt(x0+c.x(),y0+c.y())) if(id!=body.id()) { foreign++; break; }
            conflicts.add(foreign);
        }
        long start=System.nanoTime(); var result=GroupSettlingPlanner.plan(bodies,world,occupancy,settled,BUDGET,0.3,0.9); long nanos=System.nanoTime()-start;
        System.out.printf(Locale.ROOT,"independent ready=%d/3 cells=%d/794 nearest-conflicts=%s time=%.3fms checks=%d%n",independentlyPlaced,cells,conflicts,independentNanos/1e6,independentWork);
        System.out.printf(Locale.ROOT,"group success=%s reason=%s bodies=%d cells=%d contested=%d time=%.3fms work=%d%n",result.success(),result.reason(),result.bodies().size(),result.bodies().stream().mapToInt(b->b.cells().size()).sum(),result.competingCells(),nanos/1e6,result.work());
        for(var b:result.bodies()) System.out.printf(Locale.ROOT,"body=%d count=%d mass=%d coverage=%.9f shift=%.9f%n",b.id(),b.cells().size(),b.mass(),b.coverage(),b.centerShift());
        assertEquals(0,independentlyPlaced); assertEquals(0,cells); assertTrue(result.success(),result.reason());
        assertTrue(result.work()<=BUDGET); assertEquals(3,session.physics().bodies().size());
        assertEquals(originalStates,bodies.stream().map(RigidBody::state).toList()); assertEquals(worldRevision,world.mutationRevision());
        assertSame(occupancy,session.occupancy().snapshot()); assertTrue(settled.snapshot().isEmpty());
        if(result.success()) {
            assertEquals(794,result.bodies().stream().mapToInt(b->b.cells().size()).sum());
            var all=new java.util.HashSet<GroupSettlingPlanner.Cell>();
            for(var p:result.bodies()) { var body=session.physics().body(p.id()); assertEquals(body.fragment().descriptor().cellCount(),p.cells().size());
                assertEquals(body.fragment().substance(),p.substance()); assertEquals(body.fragment().massMilligrams(),p.mass());
                var remaining=new java.util.HashSet<>(p.cells()); var queue=new java.util.ArrayDeque<GroupSettlingPlanner.Cell>();
                queue.add(p.cells().getFirst()); remaining.remove(p.cells().getFirst());
                while(!queue.isEmpty()) { var c=queue.removeFirst(); for(var d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                    var n=new GroupSettlingPlanner.Cell(c.x()+d[0],c.y()+d[1]); if(remaining.remove(n)) queue.add(n);
                } }
                assertTrue(remaining.isEmpty()); assertTrue(p.coverage()>=0.9); assertTrue(p.centerShift()<=0.3);
                for(var c:p.cells()) { assertTrue(all.add(c)); assertEquals(Material.AIR,world.get(c.x(),c.y()));
                    for(long occupant:occupancy.bodyIdsAt(c.x(),c.y())) assertTrue(occupant>=1 && occupant<=3); } }
        }
        return result;
    }
    @Test void sleepingStackIsDeterministic() { assertEquals(stack(),stack()); }
    private static FragmentDescriptor shape(FragmentDescriptor.Cell... cells) {
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(cells));
    }
    @Test void separatedAndConcaveControls() {
        var world=new World(20,10,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,5,20);
        var registry=new FragmentRegistry(3);
        registry.insert(shape(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(0,1)),2,7,0);
        registry.insert(shape(new FragmentDescriptor.Cell(0,0)),8,8,0); var physics=new RigidBodySystem(registry); physics.synchronize();
        var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var result=GroupSettlingPlanner.plan(physics.bodies(),world,occupancy.snapshot(),settled,BUDGET,0.3,0.9);
        assertTrue(result.success(),result.reason()); assertEquals(4,result.bodies().stream().mapToInt(p->p.cells().size()).sum());
        assertFalse(result.bodies().getFirst().cells().contains(new GroupSettlingPlanner.Cell(3,8)));
    }
    @Test void impossibleObstacleAndBudgetAreRejected() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,5,5);
        var registry=new FragmentRegistry(1); registry.insert(shape(new FragmentDescriptor.Cell(0,0)),3,8,0); var physics=new RigidBodySystem(registry); physics.synchronize();
        var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        world.set(3,8,Material.SAND);
        var blocked=GroupSettlingPlanner.plan(physics.bodies(),world,occupancy.snapshot(),settled,BUDGET,0.3,0.9);
        assertFalse(blocked.success()); assertEquals(Material.SAND,world.get(3,8));
        world.set(3,8,Material.AIR);
        var limited=GroupSettlingPlanner.plan(physics.bodies(),world,occupancy.snapshot(),settled,1,0.3,0.9);
        assertFalse(limited.success()); assertEquals("work budget exceeded",limited.reason());
    }
    @Test void externalRigidBodyAndUnsupportedGroupAreRejected() {
        var world=new World(10,10,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,5,5);
        var registry=new FragmentRegistry(2); var one=shape(new FragmentDescriptor.Cell(0,0));
        registry.insert(one,3,8,0); registry.insert(one,3,8,0); var physics=new RigidBodySystem(registry); physics.synchronize();
        var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        assertFalse(GroupSettlingPlanner.plan(List.of(physics.body(1)),world,index.snapshot(),settled,BUDGET,0.3,0.9).success());
        var freeWorld=new World(10,10,World.Mode.BEDROCK_ENABLED); var freeTopology=new RockConnectivity(freeWorld); var freeSettled=new SettledRockRegistry(freeWorld,freeTopology,5,5);
        var freeRegistry=new FragmentRegistry(1); freeRegistry.insert(one,3,3,0); var freePhysics=new RigidBodySystem(freeRegistry); freePhysics.synchronize();
        var freeIndex=new RigidBodyOccupancyIndex(freeWorld,RigidBodyOccupancyIndex.Limits.defaults()); freeIndex.rebuild(freePhysics,0);
        assertEquals("no conservative vertical equilibrium to BEDROCK",GroupSettlingPlanner.plan(freePhysics.bodies(),freeWorld,freeIndex.snapshot(),freeSettled,BUDGET,0.3,0.9).reason());
    }
    @Test void separateSubstancesRemainDistinct() {
        var world=new World(12,10,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,4,4);
        var registry=new FragmentRegistry(2);
        for(var substance:List.of(SubstanceRegistry.MARBLE,SubstanceRegistry.GRANITE)) {
            var descriptor=new FragmentDescriptor(SubstanceRegistry.reference().get(substance),List.of(new FragmentDescriptor.Cell(0,0)));
            registry.insert(descriptor,registry.size()==0?2:7,8,0);
        }
        var physics=new RigidBodySystem(registry); physics.synchronize(); var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        var result=GroupSettlingPlanner.plan(physics.bodies(),world,index.snapshot(),settled,BUDGET,0.3,0.9);
        assertTrue(result.success(),result.reason());
        assertEquals(List.of(SubstanceRegistry.MARBLE,SubstanceRegistry.GRANITE),result.bodies().stream().map(GroupSettlingPlanner.BodyPlan::substance).toList());
        assertEquals(List.of(2_700_000L,2_700_000L),result.bodies().stream().map(GroupSettlingPlanner.BodyPlan::mass).toList());
    }
    @Test void prospectiveBridgeUsesTwoIndependentSettledSupports() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        var one=shape(new FragmentDescriptor.Cell(0,0));
        for(int x:new int[]{3,5}) { world.set(x,18,Material.STONE); miningsim.structural.SettledTestFixtures.register(settled,x,one,x,18); }
        var registry=new FragmentRegistry(1);
        registry.insert(shape(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0)),3,17,0);
        var physics=new RigidBodySystem(registry); physics.synchronize(); var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        long revision=world.mutationRevision();
        var result=GroupSettlingPlanner.plan(physics.bodies(),world,index.snapshot(),settled,topology,BUDGET,0.3,0.9);
        assertTrue(result.success(),result.reason()); assertEquals(revision,world.mutationRevision()); assertEquals(2,settled.liveRocks());
        boolean limited=false;
        for(int budget=1;budget<200;budget++) {
            var failure=GroupSettlingPlanner.plan(physics.bodies(),world,index.snapshot(),settled,topology,budget,0.3,0.9);
            if(failure.reason().startsWith("prospective support analysis IN_PROGRESS")) {
                limited=true; assertFalse(failure.success()); assertTrue(failure.work()<=budget); break;
            }
        }
        assertTrue(limited,"expected bounded prospective graph refusal");
        assertEquals(revision,world.mutationRevision()); assertEquals(2,settled.liveRocks()); assertEquals(1,physics.bodies().size());
    }
    @Test void prospectiveLoadThatDestabilizesLowerRockIsRejected() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        world.set(3,18,Material.STONE); world.set(4,18,Material.STONE);
        for(int x=3;x<=5;x++) world.set(x,17,Material.STONE);
        miningsim.structural.SettledTestFixtures.register(settled,1,shape(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0)),3,17);
        var registry=new FragmentRegistry(1); registry.insert(shape(new FragmentDescriptor.Cell(0,0)),5,16,0);
        var physics=new RigidBodySystem(registry); physics.synchronize(); var index=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); index.rebuild(physics,0);
        long revision=world.mutationRevision(); var old=index.snapshot();
        var result=GroupSettlingPlanner.plan(physics.bodies(),world,old,settled,topology,BUDGET,0.3,0.9);
        assertFalse(result.success()); assertEquals("prospective load destabilizes settled support",result.reason());
        assertEquals(revision,world.mutationRevision()); assertSame(old,index.snapshot()); assertEquals(1,physics.bodies().size()); assertEquals(1,settled.liveRocks());
    }
}

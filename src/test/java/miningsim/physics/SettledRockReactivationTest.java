package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettledRockReactivationTest {
    @Test void rotatedRoundTripUsesSettledNotOriginalGeometry() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new miningsim.structural.RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10); while(topology.pending()) topology.advance(1000);
        var registry=new miningsim.fragment.FragmentRegistry(2);
        var shape=new miningsim.resource.FragmentDescriptor(miningsim.resource.SubstanceRegistry.reference().get(miningsim.resource.SubstanceRegistry.MARBLE),List.of(new miningsim.resource.FragmentDescriptor.Cell(0,0),new miningsim.resource.FragmentDescriptor.Cell(1,0)));
        registry.insert(shape,3,10,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); physics.body(1).restore(new RigidBody.State(5.5,18,Math.PI/2,0,0,0,false,0));
        var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,miningsim.simulation.CellularDisplacementPlanner.Limits.defaults());
        var eligible=new GroupSettlingEligibility(List.of(1L)); boolean ready=false;
        for(int i=0;i<500 && !ready;i++) { coupled.step(); assertNull(coupled.lastFailure()); ready=eligible.observe(physics,coupled,world,topology,settled); }
        assertTrue(ready);
        var planned=GroupSettlingCommit.prepareGroupSettling(List.of(1L),physics,coupled,world,occupancy,topology,settled,eligible,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,planned.status(),planned.reason()); assertEquals(World.BatchResult.CHANGED,world.commitGroupSettling(planned.prepared()));
        var cells=settled.cells(1); var result=SettledRockReactivation.prepareReactivation(1,world,topology,settled,physics,occupancy,coupled.diagnostics().ticks());
        assertEquals(SettledRockReactivation.Status.READY,result.status(),result.reason());
        assertNotEquals(shape.cells(),result.plan().descriptor().cells());
        assertEquals(World.BatchResult.CHANGED,world.commitReactivation(result.plan()));
        var fragment=physics.body(result.plan().bodyId()).fragment();
        assertEquals(cells,fragment.descriptor().cells().stream().map(c->new SettledRockRegistry.WorldCell(fragment.worldX(c),fragment.worldY(c))).toList());
        assertEquals(shape.massMilligrams(),fragment.massMilligrams()); assertEquals(shape.substance().id(),fragment.substance());
    }
    private record Fixture(miningsim.app.InteractiveSession session,SettledRockRegistry settled) { }
    private static Fixture stack() {
        var s=LargeCompoundBenchmark.fixture("large").session(); var topology=s.structural().connectivity();
        var settled=new SettledRockRegistry(s.world(),topology,10,1024); var ids=List.of(1L,2L,3L);
        var eligibility=new GroupSettlingEligibility(ids);
        for(int i=0;i<500;i++) { s.tick(); assertNull(s.failure()); eligibility.observe(s.physics(),s.coupled(),s.world(),topology,settled); }
        var plan=GroupSettlingCommit.prepareGroupSettling(ids,s.physics(),s.coupled(),s.world(),s.occupancy(),topology,settled,eligibility,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,plan.status(),plan.reason());
        assertEquals(World.BatchResult.CHANGED,s.world().commitGroupSettling(plan.prepared()));
        return new Fixture(s,settled);
    }
    private static SettledRockReactivation.Result prepare(Fixture f,long id) {
        var s=f.session(); return SettledRockReactivation.prepareReactivation(id,s.world(),s.structural().connectivity(),f.settled(),s.physics(),s.occupancy(),s.coupled().diagnostics().ticks());
    }
    @Test void deterministicReplay() {
        var a=stack(); var b=stack();
        for(long id=1;id<=3;id++) {
            var x=prepare(a,id); var y=prepare(b,id);
            assertEquals(SettledRockReactivation.Status.READY,x.status(),x.reason()); assertEquals(SettledRockReactivation.Status.READY,y.status(),y.reason());
            assertEquals(x.plan().bodyId(),y.plan().bodyId()); assertEquals(x.plan().changes(),y.plan().changes());
            assertEquals(x.plan().descriptor().cells(),y.plan().descriptor().cells());
            assertEquals(World.BatchResult.CHANGED,a.session().world().commitReactivation(x.plan()));
            assertEquals(World.BatchResult.CHANGED,b.session().world().commitReactivation(y.plan()));
        }
        assertEquals(a.session().physics().bodies().stream().map(RigidBody::state).toList(),b.session().physics().bodies().stream().map(RigidBody::state).toList());
    }
    @Test void allThreeReactivateWithoutMatterLoss() {
        var f=stack(); var s=f.session(); long mass=f.settled().snapshot().stream().mapToLong(SettledRockRegistry.Rock::massMilligrams).sum();
        int count=0;
        for(long id=1;id<=3;id++) {
            var original=f.settled().rock(id); var cells=f.settled().cells(id); var result=prepare(f,id);
            assertEquals(SettledRockReactivation.Status.READY,result.status(),result.reason()); var p=result.plan();
            assertEquals(original.remainingCells(),p.descriptor().cellCount()); assertEquals(original.massMilligrams(),p.descriptor().massMilligrams());
            assertEquals(original.descriptor().substance(),p.descriptor().substance());
            assertEquals(World.BatchResult.CHANGED,s.world().commitReactivation(p));
            assertEquals(World.BatchResult.REJECTED,s.world().commitReactivation(p));
            assertEquals(p.bodyId(),f.settled().rock(id).reactivatedBodyId()); assertNotNull(s.physics().body(p.bodyId()));
            assertEquals(0,s.physics().body(p.bodyId()).angleRadians()); assertEquals(0,s.physics().body(p.bodyId()).velocityYMetresPerSecond());
            for(long other=id+1;other<=3;other++) for(var c:f.settled().cells(other)) {
                assertEquals(Material.STONE,s.world().get(c.x(),c.y())); assertEquals(other,f.settled().ownerAt(c.x(),c.y()));
            }
            var fragment=s.physics().body(p.bodyId()).fragment();
            var restored=fragment.descriptor().cells().stream().map(c->new SettledRockRegistry.WorldCell(fragment.worldX(c),fragment.worldY(c))).toList();
            assertEquals(cells,restored);
            for(var c:cells) { assertEquals(Material.AIR,s.world().get(c.x(),c.y())); assertEquals(0,f.settled().ownerAt(c.x(),c.y()));
                assertArrayEquals(new long[]{p.bodyId()},s.occupancy().snapshot().bodyIdsAt(c.x(),c.y())); }
            count+=cells.size();
        }
        assertEquals(794,count); assertEquals(3,s.physics().bodies().size()); assertEquals(mass,s.structural().registry().totalMassMilligrams());
        assertEquals(3,new BodyCollisionQuery(new BodyCollisionQuery.Limits(100,1_000_000,4096,4096)).query(s.physics().bodies()).candidatePairs());
        assertEquals(SettledRockReactivation.Status.NOT_FOUND,prepare(f,1).status());
    }
}

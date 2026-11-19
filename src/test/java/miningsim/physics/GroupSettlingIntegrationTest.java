package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class GroupSettlingIntegrationTest {
    @Test void twoContactingBodiesSettleSeparately() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new miningsim.structural.RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10); while(topology.pending()) topology.advance(1000);
        var registry=new miningsim.fragment.FragmentRegistry(2);
        var descriptor=new miningsim.resource.FragmentDescriptor(miningsim.resource.SubstanceRegistry.reference().get(miningsim.resource.SubstanceRegistry.STONE),List.of(new miningsim.resource.FragmentDescriptor.Cell(0,0)));
        registry.insert(descriptor,3,18,0); registry.insert(descriptor,3,17,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,miningsim.simulation.CellularDisplacementPlanner.Limits.defaults());
        var eligibility=new GroupSettlingEligibility(List.of(1L,2L)); boolean eligible=false;
        for(int i=0;i<500 && !eligible;i++) { coupled.step(); eligible=eligibility.observe(physics,coupled,world,topology,settled); }
        assertTrue(eligible);
        var result=GroupSettlingCommit.prepareGroupSettling(List.of(1L,2L),physics,coupled,world,occupancy,topology,settled,eligibility,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,result.status(),result.reason()); assertEquals(World.BatchResult.CHANGED,world.commitGroupSettling(result.prepared()));
        assertEquals(2,settled.snapshot().size()); assertNotEquals(settled.ownerAt(3,17),settled.ownerAt(3,18));
        assertEquals(miningsim.structural.RockConnectivity.BondState.ABSENT,topology.bondState(3,17,3,18));
    }
    @Test void singleGroupLeavesForeignBodyUnchanged() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new miningsim.structural.RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,4,10); while(topology.pending()) topology.advance(1000);
        var registry=new miningsim.fragment.FragmentRegistry(2);
        var descriptor=new miningsim.resource.FragmentDescriptor(miningsim.resource.SubstanceRegistry.reference().get(miningsim.resource.SubstanceRegistry.MARBLE),List.of(new miningsim.resource.FragmentDescriptor.Cell(0,0)));
        registry.insert(descriptor,3,18,0); registry.insert(descriptor,10,18,0);
        var solver=new StaticContactSolver(new StaticCollisionQuery(world,10000,100000,StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR),StaticContactSolver.Config.defaults());
        var physics=new RigidBodySystem(registry,4,solver,RigidBodySystem.MotionConfig.defaults(),new BodyCollisionQuery(new BodyCollisionQuery.Limits(10000,100000,4096,4096)),new BodyContactSolver(BodyContactSolver.Config.defaults()));
        physics.synchronize(); var occupancy=new RigidBodyOccupancyIndex(world,RigidBodyOccupancyIndex.Limits.defaults()); occupancy.rebuild(physics,0);
        var coupled=new CoupledSimulationStep(world,physics,occupancy,miningsim.simulation.CellularDisplacementPlanner.Limits.defaults());
        var eligibility=new GroupSettlingEligibility(List.of(1L)); boolean eligible=false;
        for(int i=0;i<400 && !eligible;i++) { coupled.step(); eligible=eligibility.observe(physics,coupled,world,topology,settled); }
        assertTrue(eligible); var foreign=physics.body(2).state();
        var result=GroupSettlingCommit.settleGroup(List.of(1L),physics,coupled,world,occupancy,topology,settled,eligibility,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,result.status(),result.reason());
        assertEquals(World.BatchResult.CHANGED,result.publication());
        assertEquals(foreign,physics.body(2).state()); assertEquals(1,registry.size()); assertEquals(1,occupancy.snapshot().diagnostics().indexedBodies());
        assertEquals(miningsim.resource.SubstanceRegistry.MARBLE,settled.snapshot().getFirst().descriptor().substance().id());
    }
    private record Ready(miningsim.app.InteractiveSession session,SettledRockRegistry settled,GroupSettlingEligibility eligibility) { }
    private static Ready ready() {
        var s=LargeCompoundBenchmark.fixture("large").session(); var topology=s.structural().connectivity();
        var settled=new SettledRockRegistry(s.world(),topology,10,1024); var eligibility=new GroupSettlingEligibility(List.of(1L,2L,3L));
        for(int i=0;i<500;i++) { s.tick(); assertNull(s.failure()); eligibility.observe(s.physics(),s.coupled(),s.world(),topology,settled); }
        return new Ready(s,settled,eligibility);
    }
    private static GroupSettlingCommit.Result prepare(Ready f,long budget) {
        var s=f.session(); return GroupSettlingCommit.prepareGroupSettling(List.of(1L,2L,3L),s.physics(),s.coupled(),s.world(),s.occupancy(),s.structural().connectivity(),f.settled(),f.eligibility(),budget);
    }
    @Test void staleWorldPoseAndBudgetDoNotPublish() {
        var f=ready(); var s=f.session();
        var limited=prepare(f,1); assertNotEquals(GroupSettlingCommit.Status.READY,limited.status());
        var badOccupancy=new RigidBodyOccupancyIndex(s.world(),new RigidBodyOccupancyIndex.Limits(0,0,0,0,0,0,0));
        var failed=GroupSettlingCommit.prepareGroupSettling(List.of(1L,2L,3L),s.physics(),s.coupled(),s.world(),badOccupancy,s.structural().connectivity(),f.settled(),f.eligibility(),2_000_000);
        assertEquals(GroupSettlingCommit.Status.PREPARATION_FAILED,failed.status());
        assertEquals(3,s.physics().bodies().size()); assertTrue(f.settled().snapshot().isEmpty());
        var pose=prepare(f,2_000_000); assertEquals(GroupSettlingCommit.Status.READY,pose.status(),pose.reason());
        s.physics().body(1).translate(0.01,0);
        assertEquals(World.BatchResult.REJECTED,s.world().commitGroupSettling(pose.prepared()));
        assertEquals(3,s.physics().bodies().size()); assertTrue(f.settled().snapshot().isEmpty());
        s.physics().body(2).setVelocity(1,0,0);
        assertEquals(GroupSettlingCommit.Status.NOT_ELIGIBLE,prepare(f,2_000_000).status());
        var other=ready(); var prepared=prepare(other,2_000_000); assertEquals(GroupSettlingCommit.Status.READY,prepared.status(),prepared.reason());
        assertEquals(World.Mutation.CHANGED,other.session().world().set(0,0,Material.SAND));
        assertEquals(World.BatchResult.REJECTED,other.session().world().commitGroupSettling(prepared.prepared()));
        assertEquals(3,other.session().physics().bodies().size()); assertTrue(other.settled().snapshot().isEmpty());
        var third=ready(); var stale=prepare(third,2_000_000); assertEquals(GroupSettlingCommit.Status.READY,stale.status(),stale.reason());
        assertTrue(third.session().structural().registry().remove(1));
        assertEquals(World.BatchResult.REJECTED,third.session().world().commitGroupSettling(stale.prepared()));
        assertEquals(3,third.session().physics().bodies().size()); assertEquals(2,third.session().structural().registry().size());
        assertTrue(third.settled().snapshot().isEmpty());
    }
    @Test void preparedStackReplayIsDeterministic() {
        var a=ready(); var b=ready(); var pa=prepare(a,2_000_000); var pb=prepare(b,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,pa.status(),pa.reason()); assertEquals(GroupSettlingCommit.Status.READY,pb.status(),pb.reason());
        assertEquals(pa.prepared().changes(),pb.prepared().changes()); assertEquals(pa.prepared().settledIds(),pb.prepared().settledIds());
        assertEquals(World.BatchResult.CHANGED,a.session().world().commitGroupSettling(pa.prepared()));
        assertEquals(World.BatchResult.CHANGED,b.session().world().commitGroupSettling(pb.prepared()));
        for(var c:pa.prepared().changes()) assertEquals(a.settled().ownerAt(c.x(),c.y()),b.settled().ownerAt(c.x(),c.y()));
    }
    @Test void stackTransfersAllMatter() {
        var fixture=LargeCompoundBenchmark.fixture("large"); var s=fixture.session();
        var topology=s.structural().connectivity(); var settled=new SettledRockRegistry(s.world(),topology,10,1024);
        var eligibility=new GroupSettlingEligibility(List.of(1L,2L,3L));
        for(int i=0;i<500;i++) { s.tick(); assertNull(s.failure()); eligibility.observe(s.physics(),s.coupled(),s.world(),topology,settled); }
        assertEquals(120,eligibility.quietTicks());
        var result=GroupSettlingCommit.prepareGroupSettling(List.of(1L,2L,3L),s.physics(),s.coupled(),s.world(),s.occupancy(),topology,settled,eligibility,2_000_000);
        assertEquals(GroupSettlingCommit.Status.READY,result.status(),result.reason());
        var plan=result.prepared(); assertEquals(794,plan.changes().size()); assertTrue(plan.workUnits()<=2_000_000);
        assertEquals(List.of(1L,2L,3L),plan.bodyIds()); assertEquals(3,plan.diagnostics().size());
        var fragments=s.physics().bodies().stream().map(RigidBody::fragment).toList();
        long mass=s.structural().registry().totalMassMilligrams();
        assertEquals(World.BatchResult.CHANGED,s.world().commitGroupSettling(plan));
        assertEquals(World.BatchResult.REJECTED,s.world().commitGroupSettling(plan));
        assertTrue(s.physics().bodies().isEmpty()); assertEquals(0,s.structural().registry().size());
        assertEquals(0,s.occupancy().snapshot().diagnostics().indexedBodies());
        assertEquals(3,settled.snapshot().size()); assertEquals(mass,settled.snapshot().stream().mapToLong(SettledRockRegistry.Rock::massMilligrams).sum());
        for(int i=0;i<3;i++) {
            var rock=settled.snapshot().get(i); var fragment=fragments.get(i);
            assertEquals(fragment.id(),rock.sourceFragmentId()); assertEquals(fragment.substance(),rock.descriptor().substance().id());
            assertEquals(fragment.massMilligrams(),rock.massMilligrams()); assertEquals(fragment.descriptor().cellCount(),rock.remainingCells());
            for(var c:settled.cells(rock.id())) assertEquals(rock.id(),settled.ownerAt(c.x(),c.y()));
        }
        for(var c:plan.changes()) {
            assertEquals(Material.STONE,s.world().get(c.x(),c.y())); long owner=settled.ownerAt(c.x(),c.y()); assertNotEquals(0,owner);
            for(int[] d:new int[][]{{1,0},{0,1}}) { int x=c.x()+d[0],y=c.y()+d[1];
                if(s.world().contains(x,y) && s.world().get(x,y)==Material.STONE) {
                    var expected=owner==settled.ownerAt(x,y)?miningsim.structural.RockConnectivity.BondState.INTACT:miningsim.structural.RockConnectivity.BondState.ABSENT;
                    assertEquals(expected,topology.bondState(c.x(),c.y(),x,y));
                }
            }
        }
        s.tick(); assertNull(s.failure()); assertEquals(0,s.physics().interactionDiagnostics().pairs()); assertEquals(0,s.physics().interactionDiagnostics().checks());
    }
}

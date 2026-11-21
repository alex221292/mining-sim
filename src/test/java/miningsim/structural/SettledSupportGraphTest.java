package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettledSupportGraphTest {
    private static long rock(World world,SettledRockRegistry registry,int x,int y,long source) {
        world.set(x,y,Material.STONE);
        return registry.registerExisting(source,new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0))),x,y);
    }
    private static SettledSupportGraph.Result solve(World world,RockConnectivity topology,SettledRockRegistry settled,long id,int budget) {
        var graph=new SettledSupportGraph(world,topology,settled,List.of(id),new SettledSupportGraph.Limits(20,100,budget));
        SettledSupportGraph.Result result; int steps=0;
        do { result=graph.step(); assertTrue(++steps<1000); } while(result.status()==SettledSupportGraph.Status.IN_PROGRESS);
        return result;
    }
    @Test void stackAndIncrementalReplay() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        world.set(3,18,Material.STONE); long a=rock(world,settled,3,17,1),b=rock(world,settled,3,16,2),c=rock(world,settled,3,15,3);
        var supported=solve(world,topology,settled,a,1); assertEquals(SettledSupportGraph.Status.COMPLETE,supported.status()); assertTrue(supported.unsupported().isEmpty());
        world.set(3,18,Material.AIR);
        var slow=solve(world,topology,settled,a,1); var fast=solve(world,topology,settled,a,100);
        assertEquals(List.of(a,b,c),slow.unsupported()); assertEquals(slow,fast);
    }
    @Test void sideContactIsNotSupportAndStaleIsExplicit() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        world.set(3,17,Material.STONE); world.set(3,18,Material.STONE);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(0,1)));
        long bottom=settled.registerExisting(1,shape,3,17);
        assertTrue(solve(world,topology,settled,bottom,10).unsupported().isEmpty());
        long side=rock(world,settled,4,17,3);
        assertEquals(List.of(side),solve(world,topology,settled,side,10).unsupported());
        var graph=new SettledSupportGraph(world,topology,settled,List.of(bottom),new SettledSupportGraph.Limits(10,20,1));
        assertEquals(SettledSupportGraph.Status.IN_PROGRESS,graph.step().status()); world.set(0,0,Material.STONE);
        assertEquals(SettledSupportGraph.Status.STALE,graph.step().status());
    }
    @Test void unverifiedStoneIsNotFoundation() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,4,10);
        world.set(3,15,Material.STONE); long id=rock(world,settled,3,14,1);
        assertEquals(List.of(id),solve(world,topology,settled,id,2).unsupported());
    }
    @Test void certificateIsDeterministicAndRejectsStaleRevisions() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        world.set(3,18,Material.STONE); long a=rock(world,settled,3,17,1),b=rock(world,settled,3,16,2);
        var graph=new SettledSupportGraph(world,topology,settled,List.of(b),new SettledSupportGraph.Limits(10,100,4));
        assertThrows(IllegalStateException.class,graph::certificate);
        while(graph.step().status()==SettledSupportGraph.Status.IN_PROGRESS) { }
        var certificate=graph.certificate(); assertEquals(a,certificate.assemblyId());
        assertEquals(List.of(a,b),certificate.supportedIds()); assertEquals(2,certificate.supportContacts());
        world.set(3,18,Material.AIR);
        assertThrows(IllegalStateException.class,graph::certificate);
    }
    @Test void addingUpperLoadCanInvalidatePreviouslyStableFoundation() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,10);
        world.set(3,18,Material.STONE); world.set(4,18,Material.STONE);
        for(int x=3;x<=5;x++) world.set(x,17,Material.STONE);
        var shape=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(
                new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0)));
        long lower=settled.registerExisting(1,shape,3,17);
        assertTrue(solve(world,topology,settled,lower,100).unsupported().isEmpty());
        long revision=world.mutationRevision();
        var prospective=new SettledSupportGraph(world,topology,settled,List.of(lower),new SettledSupportGraph.Limits(10,100,100),java.util.Set.of(16L*world.width()+5));
        assertEquals(SettledSupportGraph.Status.COMPLETE,prospective.step().status());
        assertFalse(prospective.certificate().supportedIds().contains(lower));
        assertEquals(revision,world.mutationRevision()); assertEquals(Material.AIR,world.get(5,16));
        for(int x=4;x<=6;x++) world.set(x,16,Material.STONE);
        long upper=settled.registerExisting(2,shape,4,16);
        assertTrue(solve(world,topology,settled,lower,100).unsupported().containsAll(List.of(lower,upper)));
    }
    @Test void offsetThreeRockStackRetainsItsBalancedSupport() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world); var settled=new SettledRockRegistry(world,topology,10,20);
        for(int x=3;x<=5;x++) { world.set(x,18,Material.STONE); world.set(x,17,Material.STONE); }
        var three=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0),new FragmentDescriptor.Cell(1,0),new FragmentDescriptor.Cell(2,0)));
        long bottom=settled.registerExisting(1,three,3,17);
        for(int x=4;x<=6;x++) world.set(x,16,Material.STONE);
        long middle=settled.registerExisting(2,three,4,16),top=rock(world,settled,6,15,3);
        var result=solve(world,topology,settled,bottom,100);
        assertTrue(result.unsupported().isEmpty(),"balanced stack rejected: "+result.unsupported()+" (middle="+middle+", top="+top+")");
    }
}

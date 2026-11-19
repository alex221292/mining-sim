package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.physics.RigidBody;
import miningsim.physics.RigidBodySystem;
import miningsim.physics.SettlingSupport;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettlingSupportChainTest {
    private static final FragmentDescriptor ONE=shape(new int[][]{{0,0}});
    private static FragmentDescriptor shape(int[][] points) {
        var cells=new java.util.ArrayList<FragmentDescriptor.Cell>();
        for(var p:points) cells.add(new FragmentDescriptor.Cell(p[0],p[1]));
        return new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),cells);
    }
    private static void add(World world,SettledRockRegistry registry,int id,FragmentDescriptor descriptor,int x,int y) {
        for(var cell:descriptor.cells()) world.set(x+cell.x(),y+cell.y(),Material.STONE);
        registry.registerExisting(id,descriptor,x,y);
    }
    private static RigidBody body(int x,int y) {
        var registry=new FragmentRegistry(1); registry.insert(ONE,x,y,0);
        var system=new RigidBodySystem(registry,1); system.synchronize(); system.step(); return system.body(1);
    }
    private static void finish(RockConnectivity topology) { while(topology.pending()) topology.advance(100); }
    @Test void chainMustReachBedrock() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,10,100);
        add(world,settled,1,ONE,3,18); add(world,settled,2,ONE,3,17); finish(topology);
        assertEquals(SettlingSupport.Status.SUPPORTED,SettlingSupport.check(body(3,16),world,topology,settled,100).status());
        world.set(3,18,Material.AIR); finish(topology);
        assertEquals(SettlingSupport.Status.NOT_SUPPORTED,SettlingSupport.check(body(3,16),world,topology,settled,100).status());
    }
    @Test void cyclicContactsWithoutFoundationAreNotSupport() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,10,100);
        var a=shape(new int[][]{{0,0},{1,0},{2,0},{3,0},{3,1},{0,1},{0,2},{1,2}});
        var b=shape(new int[][]{{0,0},{1,0},{1,1},{2,1}});
        add(world,settled,1,a,2,13); add(world,settled,2,b,3,14); finish(topology);
        assertEquals(SettlingSupport.Status.NOT_SUPPORTED,SettlingSupport.check(body(2,12),world,topology,settled,100).status());
    }
}

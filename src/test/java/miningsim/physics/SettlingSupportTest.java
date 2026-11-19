package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import miningsim.fragment.FragmentRegistry;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceRegistry;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class SettlingSupportTest {
    private static RigidBody body(int x,int y) {
        var registry=new FragmentRegistry(1);
        registry.insert(new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0))),x,y,0);
        var physics=new RigidBodySystem(registry); physics.synchronize(); var body=physics.body(1); var state=body.state();
        body.restore(new RigidBody.State(state.x(),state.y()+0.01,state.angle(),0,0,0,true,0)); return body;
    }
    private static void finish(RockConnectivity topology) { while(topology.pending()) topology.advance(100); }
    @Test void bedrockSupportsWhileWoodDoesNot() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(6,10,Material.WOOD);
        var topology=new RockConnectivity(world); var registry=new SettledRockRegistry(world,topology,10,10); finish(topology);
        assertEquals(SettlingSupport.Status.SUPPORTED,SettlingSupport.check(body(3,18),world,topology,registry,100).status());
        assertEquals(SettlingSupport.Status.NOT_SUPPORTED,SettlingSupport.check(body(6,9),world,topology,registry,100).status());
        assertEquals(SettlingSupport.Status.NOT_SUPPORTED,SettlingSupport.check(body(9,4),world,topology,registry,100).status());
    }
    @Test void foundationConnectivityAloneDoesNotCertifyStoneSupport() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED);
        world.set(6,18,Material.STONE); world.set(10,10,Material.STONE);
        var topology=new RockConnectivity(world); var registry=new SettledRockRegistry(world,topology,10,10); finish(topology);
        assertEquals(SettlingSupport.Status.SUPPORTED,SettlingSupport.check(body(6,17),world,topology,registry,100).status());
        assertEquals(SettlingSupport.Status.SUPPORT_UNVERIFIED,SettlingSupport.check(body(10,9),world,topology,registry,100).status());
    }
    @Test void bodyAboveAnotherDynamicBodyHasNoStaticSupport() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); var topology=new RockConnectivity(world);
        var settled=new SettledRockRegistry(world,topology,10,10); finish(topology);
        var registry=new FragmentRegistry(2);
        var cell=new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),List.of(new FragmentDescriptor.Cell(0,0)));
        registry.insert(cell,5,5,0); registry.insert(cell,5,6,0);
        var physics=new RigidBodySystem(registry); physics.synchronize();
        assertEquals(SettlingSupport.Status.NOT_SUPPORTED,SettlingSupport.check(physics.body(1),world,topology,settled,100).status());
    }
}

package miningsim.physics;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.InteractiveSession;
import miningsim.structural.SettledSupportCoordinator;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class HeadlessLifecycleRollbackTest {
    @Test void failedPhysicsAfterReactivationKeepsPublishedOwnershipAndOccupancy() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE); world.set(5,8,Material.STONE);
        var s=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        for(int i=0;i<1500 && s.lifecycle().settled().liveRocks()<2;i++) { s.tick(); assertNull(s.failure()); }
        assertEquals(2,s.lifecycle().settled().liveRocks());
        s.enqueue(()->world.set(5,18,Material.AIR));
        for(int i=0;i<100 && s.lifecycle().diagnostics().reactivated()==0;i++) { s.tick(); assertNull(s.failure()); }
        assertEquals(1,s.lifecycle().diagnostics().reactivated());
        assertEquals(1,s.physics().bodies().size()); assertEquals(0,s.lifecycle().settled().liveRocks());
        var body=s.physics().bodies().getFirst(); body.setVelocity(100_000,0,0);
        var state=body.state(); var occupancy=s.occupancy().snapshot(); long tick=s.coupled().diagnostics().ticks();
        s.tick();
        assertNotNull(s.failure()); assertEquals(CoupledSimulationStep.FailureStage.PHYSICS,s.coupled().lastFailureStage());
        assertEquals(state,body.state()); assertSame(occupancy,s.occupancy().snapshot());
        assertEquals(tick,s.coupled().diagnostics().ticks()); assertEquals(0,s.lifecycle().settled().liveRocks());
        assertEquals(Material.AIR,world.get(5,17)); assertEquals(Material.AIR,world.get(5,18));
        body.setVelocity(0,0,0); s.tick(); assertNull(s.failure()); assertFalse(s.blocked());
    }
}

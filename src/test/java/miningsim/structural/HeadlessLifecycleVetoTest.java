package miningsim.structural;

import static org.junit.jupiter.api.Assertions.*;
import miningsim.app.InteractiveSession;
import miningsim.world.Material;
import miningsim.world.World;
import org.junit.jupiter.api.Test;

class HeadlessLifecycleVetoTest {
    @Test void vetoDoesNotPublishOrRetryUnchangedState() {
        var world=new World(20,20,World.Mode.BEDROCK_ENABLED); world.set(5,3,Material.STONE);
        var session=InteractiveSession.headlessLifecycle(world,false,SettledSupportCoordinator.Limits.measuredDefaults());
        var topology=session.automatic().topology();
        topology.setTestBatchObserver(()->!session.lifecycle().inSettlingPublication(),null);
        for(int i=0;i<1000 && session.lifecycle().diagnostics().rejected()==0;i++) { session.tick(); assertNull(session.failure()); }
        assertEquals(1,session.lifecycle().diagnostics().rejected());
        assertEquals(1,session.physics().bodies().size()); assertEquals(0,session.lifecycle().settled().liveRocks());
        long ticks=session.cellularSteps();
        for(int i=0;i<60;i++) { session.tick(); assertNull(session.failure()); assertFalse(session.blocked()); }
        assertEquals(1,session.lifecycle().diagnostics().rejected()); assertEquals(0,session.lifecycle().diagnostics().retries());
        assertEquals(ticks+60,session.cellularSteps()); assertEquals(1,session.physics().bodies().size());
        topology.setTestBatchObserver(null,null);
    }
}

package miningsim.app;

import miningsim.structural.SettledSupportCoordinator;
import miningsim.world.World;

/** Ordinary sandbox sessions; reference scenes retain their explicit constructors. */
public final class SandboxSessionFactory {
    private SandboxSessionFactory() { }
    public static InteractiveSession create(World world,boolean profiling) {
        return world.mode()==World.Mode.BEDROCK_ENABLED
                ? InteractiveSession.headlessLifecycle(world,profiling,SettledSupportCoordinator.Limits.measuredDefaults())
                : new InteractiveSession(world,null,profiling);
    }
    /** World-only persistence omits rigid matter and settled provenance. */
    public static boolean losesLifecycleState(InteractiveSession session) {
        return session.isCoupled() && (!session.physics().bodies().isEmpty()
                || session.lifecycle()!=null && session.lifecycle().settled().liveRocks()>0);
    }
}

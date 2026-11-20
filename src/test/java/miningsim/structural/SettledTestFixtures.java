package miningsim.structural;

import miningsim.resource.FragmentDescriptor;

/** Test-only access to registration of existing fixture cells. */
public final class SettledTestFixtures {
    private SettledTestFixtures() { }
    public static long register(SettledRockRegistry registry,long source,FragmentDescriptor descriptor,int x,int y) {
        return registry.registerExisting(source,descriptor,x,y);
    }
}

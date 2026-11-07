package miningsim.physics;

import java.util.List;
import java.util.Objects;
import java.util.TreeMap;
import miningsim.fragment.FragmentRegistry;

/** Headless fixed-step free-motion layer. Collision handling is not implemented yet. */
public final class RigidBodySystem {
    public static final double TICK_SECONDS = 1.0 / 60.0;
    public static final double GRAVITY_METRES_PER_SECOND_SQUARED = 9.81;
    private final FragmentRegistry registry;
    private final int substeps;
    private final TreeMap<Long, RigidBody> bodies = new TreeMap<>();

    public RigidBodySystem(FragmentRegistry registry) { this(registry, 4); }
    public RigidBodySystem(FragmentRegistry registry, int substeps) {
        this.registry = Objects.requireNonNull(registry);
        if (substeps < 1 || substeps > 1024) throw new IllegalArgumentException("Invalid substep count");
        this.substeps = substeps;
    }

    /** Call at the simulation boundary; IDs belong to this system's fixed registry. */
    public void synchronize() {
        var fragments = registry.snapshot();
        var ids = new java.util.HashSet<Long>();
        for (var fragment : fragments) {
            ids.add(fragment.id());
            bodies.computeIfAbsent(fragment.id(), id -> new RigidBody(fragment));
        }
        bodies.keySet().removeIf(id -> !ids.contains(id));
    }

    public List<RigidBody> bodies() { return List.copyOf(bodies.values()); }
    public RigidBody body(long id) { return bodies.get(id); }

    /** Explicit tick, independent of wall time and rendering. */
    public void step() {
        synchronize();
        double dt = TICK_SECONDS / substeps;
        for (int i = 0; i < substeps; i++)
            for (var body : bodies.values()) body.integrate(dt, GRAVITY_METRES_PER_SECOND_SQUARED);
    }
}

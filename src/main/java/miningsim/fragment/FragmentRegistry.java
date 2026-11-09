package miningsim.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.MassAccounting;

/** Single-owner registry, monotonically assigned IDs and immutable ordered snapshots. */
public final class FragmentRegistry {
    private final int capacity;
    private long nextId = 1, mass, revision;
    private List<RockFragment> fragments = List.of();
    /** Preallocated ownership state; valid only until the registry next changes. */
    public final class Prepared {
        private final List<RockFragment> expected, next;
        private final RockFragment fragment;
        private final long nextMass, nextSequence, expectedRevision, nextRevision;
        private Prepared(List<RockFragment> expected, List<RockFragment> next, RockFragment fragment, long nextMass, long nextSequence, long expectedRevision, long nextRevision) {
            this.expected=expected; this.next=next; this.fragment=fragment; this.nextMass=nextMass; this.nextSequence=nextSequence;
            this.expectedRevision=expectedRevision; this.nextRevision=nextRevision;
        }
        public boolean valid() { return fragments == expected && revision == expectedRevision; }
        public boolean belongsTo(FragmentRegistry registry) { return FragmentRegistry.this==registry; }
        public RockFragment fragment() { return fragment; }
        /** Only after valid() under the single-threaded, non-reentrant ownership contract. */
        public void publish() {
            if (!valid()) throw new IllegalStateException("Registry changed after preparation");
            fragments=next; mass=nextMass; nextId=nextSequence; revision=nextRevision;
        }
    }
    public FragmentRegistry(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Negative capacity");
        this.capacity = capacity;
    }
    public int size() { return fragments.size(); }
    public int capacity() { return capacity; }
    public long totalMassMilligrams() { return mass; }
    public List<RockFragment> snapshot() { return List.copyOf(fragments); }
    /** Adds a new stationary fragment, or throws without modifying this registry. */
    public RockFragment insert(FragmentDescriptor descriptor, int originX, int originY, int componentId) {
        Prepared prepared = prepare(descriptor,originX,originY,componentId); prepared.publish(); return prepared.fragment();
    }
    public Prepared prepare(FragmentDescriptor descriptor, int originX, int originY, int componentId) {
        Objects.requireNonNull(descriptor);
        if (size() >= capacity) throw new IllegalStateException("Fragment registry capacity exhausted");
        long after = MassAccounting.add(mass, descriptor.massMilligrams());
        long id = nextId, next = Math.addExact(nextId, 1), nextRevision = Math.addExact(revision,1);
        RockFragment fragment = new RockFragment(id, descriptor, originX, originY, componentId);
        var updated = new ArrayList<>(fragments); updated.add(fragment);
        return new Prepared(fragments,List.copyOf(updated),fragment,after,next,revision,nextRevision);
    }
    public boolean remove(long id) {
        for (int i = 0; i < fragments.size(); i++) if (fragments.get(i).id() == id) {
            long nextRevision = Math.addExact(revision,1);
            var updated = new ArrayList<>(fragments); var removed=updated.remove(i);
            fragments=List.copyOf(updated); mass -= removed.massMilligrams(); revision=nextRevision; return true;
        }
        return false;
    }
}

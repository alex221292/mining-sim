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
    /** Prevalidated removal; preparing never changes published membership. */
    public final class PreparedRemoval {
        private final List<RockFragment> expected,next;
        private final RockFragment fragment;
        private final long expectedRevision,nextRevision,nextMass;
        private PreparedRemoval(List<RockFragment> expected,List<RockFragment> next,RockFragment fragment,
                                long expectedRevision,long nextRevision,long nextMass) {
            this.expected=expected; this.next=next; this.fragment=fragment;
            this.expectedRevision=expectedRevision; this.nextRevision=nextRevision; this.nextMass=nextMass;
        }
        public boolean valid() { return fragments==expected && revision==expectedRevision; }
        public boolean belongsTo(FragmentRegistry registry) { return FragmentRegistry.this==registry; }
        public RockFragment fragment() { return fragment; }
        /** Only inside a validated single-owner publication boundary. */
        void publish() {
            if(!valid()) throw new IllegalStateException("Registry changed after removal preparation");
            fragments=next; revision=nextRevision; mass=nextMass;
        }
        public void publishSettling(Object token) {
            if(!miningsim.world.World.isSettlingPublicationToken(token)) throw new IllegalStateException("Not a settling publication");
            publish();
        }
    }
    public FragmentRegistry(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("Negative capacity");
        this.capacity = capacity;
    }
    public int size() { return fragments.size(); }
    public int capacity() { return capacity; }
    public long revision() { return revision; }
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
    public PreparedRemoval prepareRemoval(long id) {
        for(int i=0;i<fragments.size();i++) if(fragments.get(i).id()==id) {
            var next=new ArrayList<>(fragments); var removed=next.remove(i);
            return new PreparedRemoval(fragments,List.copyOf(next),removed,revision,Math.incrementExact(revision),Math.subtractExact(mass,removed.massMilligrams()));
        }
        throw new IllegalArgumentException("Unknown fragment ID: "+id);
    }

    /** Prepared removal of a complete group; independent single removals cannot be composed atomically. */
    public final class PreparedGroupRemoval {
        private final List<RockFragment> expected, next, removed;
        private final long expectedRevision, nextRevision, nextMass;
        private PreparedGroupRemoval(List<RockFragment> expected,List<RockFragment> next,List<RockFragment> removed,long nextMass) {
            this.expected=expected; this.next=next; this.removed=removed; this.nextMass=nextMass;
            expectedRevision=revision; nextRevision=Math.incrementExact(revision);
        }
        public List<RockFragment> fragments() { return removed; }
        public boolean valid() { return fragments==expected && revision==expectedRevision; }
        public void publishSettling(Object token) {
            if(!miningsim.world.World.isSettlingPublicationToken(token) || !valid())
                throw new IllegalStateException("Invalid group removal publication");
            fragments=next; mass=nextMass; revision=nextRevision;
        }
    }
    public PreparedGroupRemoval prepareGroupRemoval(java.util.Set<Long> ids) {
        Objects.requireNonNull(ids);
        if(ids.isEmpty() || ids.stream().anyMatch(Objects::isNull)) throw new IllegalArgumentException("Empty or invalid group");
        var next=new ArrayList<RockFragment>(); var removed=new ArrayList<RockFragment>(); long remaining=mass;
        for(var fragment:fragments) {
            if(ids.contains(fragment.id())) { removed.add(fragment); remaining=Math.subtractExact(remaining,fragment.massMilligrams()); }
            else next.add(fragment);
        }
        if(removed.size()!=ids.size()) throw new IllegalArgumentException("Unknown fragment ID");
        return new PreparedGroupRemoval(fragments,List.copyOf(next),List.copyOf(removed),remaining);
    }
}

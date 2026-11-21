package miningsim.structural;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.MassAccounting;
import miningsim.world.Material;
import miningsim.world.World;

/** Ownership metadata for existing World STONE; it does not create or transfer matter. */
public final class SettledRockRegistry {
    public enum Integrity { INTACT, DAMAGED, REMOVED }
    public record WorldCell(int x,int y) { }
    public record Rock(long id,long sourceFragmentId,FragmentDescriptor descriptor,int originX,int originY,
                       int remainingCells,long massMilligrams,long generation,Integrity integrity,long reactivatedBodyId) { }
    private static final class Entry {
        final long id,source; final FragmentDescriptor descriptor; final int x,y;
        int count; long mass,generation,reactivatedBodyId; Integrity integrity=Integrity.INTACT;
        List<Long> reactivatedBodyIds=List.of();
        Entry(long id,long source,FragmentDescriptor descriptor,int x,int y) {
            this.id=id; this.source=source; this.descriptor=descriptor; this.x=x; this.y=y;
            count=descriptor.cellCount(); mass=descriptor.massMilligrams();
        }
        Rock snapshot() { return new Rock(id,source,descriptor,x,y,count,mass,generation,integrity,reactivatedBodyId); }
    }
    private final World world;
    private final RockConnectivity connectivity;
    private final long[] owners;
    private final ArrayList<Entry> entries=new ArrayList<>();
    private final int maxRocks,maxCellsPerRock;
    private long revision;
    private int liveRocks;
    private boolean exhausted;
    private java.util.function.BooleanSupplier supportTransitionGate;
    private final Object supportPublicationPermit=new Object();
    void attachSupportTransitionGate(java.util.function.BooleanSupplier gate) {
        if(supportTransitionGate!=null) throw new IllegalStateException("Support gate already attached");
        supportTransitionGate=Objects.requireNonNull(gate);
    }
    /** Pending support analysis quarantines settled transitions, not cellular simulation. */
    public boolean supportTransitionsAllowed() { return supportTransitionGate==null || supportTransitionGate.getAsBoolean(); }
    /** Once a coordinator is attached, all reactivation must pass its cascade analysis. */
    public boolean directReactivationAllowed() { return supportTransitionGate==null; }
    Object supportPublicationPermit() { return supportPublicationPermit; }
    /** Only the owner coordinator can obtain its unpublished authorization token. */
    public boolean supportTransitionsAllowed(Object permit) { return permit==supportPublicationPermit || directReactivationAllowed(); }
    /** Prepared ownership metadata, published only with the matching World insertion. */
    public final class PreparedInsertion {
        private final long expectedRevision,expectedWorldRevision,expectedTopologyRevision,nextRevision;
        private final Entry entry;
        private final int[] indices;
        private PreparedInsertion(Entry entry,int[] indices) {
            this.entry=entry; this.indices=indices;
            expectedRevision=revision; expectedWorldRevision=world.mutationRevision(); expectedTopologyRevision=connectivity.revision();
            nextRevision=Math.incrementExact(revision);
        }
        public long id() { return entry.id; }
        public long sourceFragmentId() { return entry.source; }
        public FragmentDescriptor descriptor() { return entry.descriptor; }
        public int originX() { return entry.x; }
        public int originY() { return entry.y; }
        public boolean owns(World candidate) { return world==candidate; }
        public boolean valid() {
            validRegistry();
            if(revision!=expectedRevision || world.mutationRevision()!=expectedWorldRevision || connectivity.revision()!=expectedTopologyRevision
                    || connectivity.traversing() || entries.size()+1L!=entry.id) return false;
            for(int i:indices) if(owners[i]!=0 || world.get(i%world.width(),i/world.width())!=Material.AIR) return false;
            return true;
        }
        /** Called only after World validates the exact insertion under its single-owner contract. */
        public void publish(Object token) {
            if(!World.isSettlingPublicationToken(token)) throw new IllegalStateException("Not a settling publication");
            entries.add(entry); for(int i:indices) owners[i]=entry.id; liveRocks++;
            revision=nextRevision; connectivity.ownershipChanged();
        }
    }

    /** Dense index costs exactly eight bytes per World cell, excluding array header. */
    public SettledRockRegistry(World world,RockConnectivity connectivity,int maxRocks,int maxCellsPerRock) {
        this.world=Objects.requireNonNull(world); this.connectivity=Objects.requireNonNull(connectivity);
        if(maxRocks<1 || maxCellsPerRock<1) throw new IllegalArgumentException("Invalid settled limits");
        if(!connectivity.owns(world)) throw new IllegalArgumentException("Connectivity belongs to another world");
        this.maxRocks=maxRocks; this.maxCellsPerRock=maxCellsPerRock;
        owners=new long[Math.multiplyExact(world.width(),world.height())];
        connectivity.attachSettled(this);
    }
    private void valid() { if(exhausted) throw new IllegalStateException("Settled ownership revision exhausted; create a new session"); }
    private void validRegistry() { valid(); }
    public long revision() { valid(); return revision; }
    /** Number of historical entries, for bounded incremental scans by the support coordinator. */
    public int recordCount() { valid(); return entries.size(); }
    /** Constant-time count of nonempty settled records. */
    public int liveRocks() { valid(); return liveRocks; }
    public boolean owns(World candidate,RockConnectivity topology) { return world==candidate && connectivity==topology; }
    public boolean owns(World candidate) { return world==candidate; }
    public long ownerAt(int x,int y) {
        valid();
        if(!world.contains(x,y)) throw new IndexOutOfBoundsException();
        return owners[y*world.width()+x];
    }
    public List<Rock> snapshot() {
        valid();
        var result=new ArrayList<Rock>(entries.size()); for(var entry:entries) result.add(entry.snapshot());
        return List.copyOf(result);
    }
    public Rock rock(long id) {
        valid();
        if(id<=0 || id>entries.size()) throw new IllegalArgumentException("Unknown settled ID");
        return entries.get(Math.toIntExact(id-1)).snapshot();
    }
    /** Current ownership, sorted by row then column; descriptor describes original placement. */
    public List<WorldCell> cells(long id) {
        valid();
        if(id<=0 || id>entries.size()) throw new IllegalArgumentException("Unknown settled ID");
        var entry=entries.get(Math.toIntExact(id-1)); var result=new ArrayList<WorldCell>(entry.count);
        for(var cell:entry.descriptor.cells()) {
            int x=entry.x+cell.x(),y=entry.y+cell.y();
            if(ownerAt(x,y)==id) result.add(new WorldCell(x,y));
        }
        return List.copyOf(result);
    }
    /** All body IDs transferred from this settled record, in publication order. */
    public List<Long> reactivatedBodyIds(long id) {
        valid(); if(id<=0 || id>entries.size()) throw new IllegalArgumentException("Unknown settled ID");
        return entries.get(Math.toIntExact(id-1)).reactivatedBodyIds;
    }
    /** Current orthogonally connected ownership components, ordered by their first row-major cell. */
    public List<List<WorldCell>> connectedComponents(long id) {
        var all=cells(id); var remaining=new java.util.HashSet<WorldCell>(all);
        var components=new ArrayList<List<WorldCell>>();
        var queue=new java.util.ArrayDeque<WorldCell>();
        int[] dx={0,-1,1,0},dy={-1,0,0,1};
        for(var start:all) if(remaining.remove(start)) {
            var component=new ArrayList<WorldCell>(); queue.add(start);
            while(!queue.isEmpty()) {
                var cell=queue.removeFirst(); component.add(cell);
                for(int direction=0;direction<dx.length;direction++) {
                    int x=cell.x()+dx[direction],y=cell.y()+dy[direction];
                    if(world.contains(x,y)) { var next=new WorldCell(x,y); if(remaining.remove(next)) queue.addLast(next); }
                }
            }
            component.sort(java.util.Comparator.comparingInt(WorldCell::y).thenComparingInt(WorldCell::x));
            components.add(List.copyOf(component));
        }
        return List.copyOf(components);
    }
    /** Prevalidated complete ownership transfer, published only by the reactivation boundary. */
    public final class PreparedReactivationRemoval {
        private final Entry entry;
        private final long expectedRevision,expectedWorldRevision,expectedTopologyRevision,expectedGeneration,nextRevision,nextGeneration,bodyId;
        private final int[] indices;
        private final List<Long> nextBodyIds;
        private PreparedReactivationRemoval(Entry entry,int[] indices,long bodyId) {
            this.entry=entry; this.indices=indices; this.bodyId=bodyId;
            expectedRevision=revision; expectedWorldRevision=world.mutationRevision(); expectedTopologyRevision=connectivity.revision();
            expectedGeneration=entry.generation; nextRevision=Math.incrementExact(revision); nextGeneration=Math.incrementExact(entry.generation);
            var history=new ArrayList<>(entry.reactivatedBodyIds); history.add(bodyId); nextBodyIds=List.copyOf(history);
        }
        public boolean owns(World candidate) { return world==candidate; }
        public boolean valid() {
            validRegistry();
            if(revision!=expectedRevision || world.mutationRevision()!=expectedWorldRevision || connectivity.revision()!=expectedTopologyRevision
                    || connectivity.traversing() || entry.generation!=expectedGeneration || entry.count!=indices.length
                    || entry.reactivatedBodyId!=0) return false;
            for(int i:indices) if(owners[i]!=entry.id || world.get(i%world.width(),i/world.width())!=Material.STONE) return false;
            return true;
        }
        public void publish(Object token) {
            if(!World.isReactivationPublicationToken(token)) throw new IllegalStateException("Not a reactivation publication");
            for(int i:indices) owners[i]=0;
            entry.count=0; entry.mass=0; entry.generation=nextGeneration; entry.integrity=Integrity.REMOVED; entry.reactivatedBodyId=bodyId; entry.reactivatedBodyIds=nextBodyIds; liveRocks--;
            revision=nextRevision; connectivity.ownershipChanged();
        }
    }
    public PreparedReactivationRemoval prepareReactivationRemoval(long id,long bodyId) {
        valid(); connectivity.checkOwnershipMutation();
        if(id<=0 || id>entries.size() || bodyId<=0) throw new IllegalArgumentException("Invalid reactivation identity");
        var entry=entries.get(Math.toIntExact(id-1));
        if(entry.count<=0 || entry.reactivatedBodyId!=0) throw new IllegalStateException("Settled rock is not live");
        Math.addExact(connectivity.revision(),2);
        var actual=cells(id); if(actual.size()!=entry.count) throw new IllegalStateException("Ownership count mismatch");
        int[] indices=new int[actual.size()]; int i=0;
        for(var c:actual) {
            if(world.get(c.x(),c.y())!=Material.STONE) throw new IllegalStateException("Owned cell is not STONE");
            indices[i++]=Math.addExact(Math.multiplyExact(c.y(),world.width()),c.x());
        }
        return new PreparedReactivationRemoval(entry,indices,bodyId);
    }
    public record ReactivationMapping(long settledId,long bodyId) { }
    /** A body receives exactly these current cells; several components may share a settled ID. */
    public record ComponentReactivationMapping(long settledId,long bodyId,List<WorldCell> cells) {
        public ComponentReactivationMapping { cells=List.copyOf(cells); }
    }
    /** Prevalidated selective removal; remaining cells retain their existing owner. */
    public final class PreparedComponentReactivationRemoval {
        private final List<Entry> selected;
        private final int[][] indices;
        private final long[] generations,nextGenerations,masses,bodyIds;
        private final List<List<Long>> nextBodyIds;
        private final int[] expectedCounts;
        private final long expectedRevision,expectedWorldRevision,expectedTopologyRevision,nextRevision;
        private PreparedComponentReactivationRemoval(List<Entry> selected,int[][] indices,long[] bodyIds,List<List<Long>> transferredBodyIds) {
            this.selected=selected; this.indices=indices; this.bodyIds=bodyIds;
            generations=new long[selected.size()]; nextGenerations=new long[selected.size()]; masses=new long[selected.size()]; expectedCounts=new int[selected.size()];
            var histories=new ArrayList<List<Long>>(selected.size());
            for(int i=0;i<selected.size();i++) {
                var e=selected.get(i); generations[i]=e.generation; nextGenerations[i]=Math.incrementExact(e.generation);
                expectedCounts[i]=e.count; masses[i]=Math.multiplyExact(MassAccounting.cellMilligrams(e.descriptor.substance()),indices[i].length);
                if(masses[i]>e.mass) throw new IllegalStateException("Partial transfer mass exceeds settled mass");
                var history=new ArrayList<>(e.reactivatedBodyIds); history.addAll(transferredBodyIds.get(i)); histories.add(List.copyOf(history));
            }
            nextBodyIds=List.copyOf(histories);
            expectedRevision=revision; nextRevision=Math.incrementExact(revision);
            expectedWorldRevision=world.mutationRevision(); expectedTopologyRevision=connectivity.revision();
        }
        public boolean owns(World candidate) { return world==candidate; }
        public boolean valid() {
            validRegistry();
            if(revision!=expectedRevision || world.mutationRevision()!=expectedWorldRevision || connectivity.revision()!=expectedTopologyRevision || connectivity.traversing()) return false;
            for(int j=0;j<selected.size();j++) {
                var e=selected.get(j);
                if(e.generation!=generations[j] || e.count!=expectedCounts[j] || e.reactivatedBodyId!=0) return false;
                for(int i:indices[j]) if(owners[i]!=e.id || world.get(i%world.width(),i/world.width())!=Material.STONE) return false;
            }
            return true;
        }
        public void publish(Object token) {
            if(!World.isReactivationPublicationToken(token)) throw new IllegalStateException("Not a reactivation publication");
            for(int j=0;j<selected.size();j++) {
                var e=selected.get(j); for(int i:indices[j]) owners[i]=0;
                e.count-=indices[j].length; e.mass-=masses[j]; e.generation=nextGenerations[j]; e.reactivatedBodyIds=nextBodyIds.get(j);
                if(e.count==0) { e.integrity=Integrity.REMOVED; e.reactivatedBodyId=bodyIds[j]; liveRocks--; }
                else e.integrity=Integrity.DAMAGED;
            }
            revision=nextRevision; connectivity.ownershipChanged();
        }
    }
    public PreparedComponentReactivationRemoval prepareComponentReactivationRemoval(List<ComponentReactivationMapping> mappings) {
        Objects.requireNonNull(mappings); valid(); connectivity.checkOwnershipMutation();
        if(mappings.isEmpty()) throw new IllegalArgumentException("Empty component group");
        Math.addExact(connectivity.revision(),2);
        var grouped=new java.util.TreeMap<Long,java.util.ArrayList<Integer>>();
        var bodies=new java.util.TreeMap<Long,java.util.ArrayList<Long>>(); var seenCells=new java.util.HashSet<Integer>(); var seenBodies=new java.util.HashSet<Long>();
        var componentsById=new java.util.HashMap<Long,java.util.Set<List<WorldCell>>>();
        for(var mapping:mappings) {
            Objects.requireNonNull(mapping); long id=mapping.settledId();
            if(id<=0 || id>entries.size() || mapping.bodyId()<=0 || mapping.cells().isEmpty() || !seenBodies.add(mapping.bodyId()))
                throw new IllegalArgumentException("Invalid component mapping");
            var entry=entries.get(Math.toIntExact(id-1));
            if(entry.count<=0 || entry.reactivatedBodyId!=0) throw new IllegalStateException("Settled rock is not live");
            var validComponents=componentsById.computeIfAbsent(id,key->new java.util.HashSet<>(connectedComponents(key)));
            if(!validComponents.contains(mapping.cells())) throw new IllegalArgumentException("Mapping is not a complete current component");
            var indices=grouped.computeIfAbsent(id,ignored->new java.util.ArrayList<>()); bodies.computeIfAbsent(id,ignored->new java.util.ArrayList<>()).add(mapping.bodyId());
            for(var cell:mapping.cells()) {
                if(!world.contains(cell.x(),cell.y()) || ownerAt(cell.x(),cell.y())!=id || world.get(cell.x(),cell.y())!=Material.STONE)
                    throw new IllegalArgumentException("Invalid component ownership");
                int index=Math.addExact(Math.multiplyExact(cell.y(),world.width()),cell.x());
                if(!seenCells.add(index)) throw new IllegalArgumentException("Overlapping component cells");
                indices.add(index);
            }
        }
        var selected=new ArrayList<Entry>(); var histories=new ArrayList<List<Long>>(); int[][] indices=new int[grouped.size()][]; long[] bodyIds=new long[grouped.size()]; int j=0;
        for(var group:grouped.entrySet()) {
            selected.add(entries.get(Math.toIntExact(group.getKey()-1))); indices[j]=group.getValue().stream().mapToInt(Integer::intValue).toArray();
            var ids=bodies.get(group.getKey()); bodyIds[j]=ids.getFirst(); histories.add(List.copyOf(ids)); j++;
        }
        return new PreparedComponentReactivationRemoval(List.copyOf(selected),indices,bodyIds,List.copyOf(histories));
    }
    /** One preallocated transfer of an entire group; each entry keeps its own provenance. */
    public final class PreparedGroupReactivationRemoval {
        private final List<Entry> selected;
        private final long[] generations,nextGenerations,bodyIds;
        private final List<List<Long>> nextBodyIds;
        private final int[][] indices;
        private final long expectedRevision,expectedWorldRevision,expectedTopologyRevision,nextRevision;
        private PreparedGroupReactivationRemoval(List<Entry> selected,int[][] indices,long[] bodyIds) {
            this.selected=selected; this.indices=indices; this.bodyIds=bodyIds;
            generations=new long[selected.size()]; nextGenerations=new long[selected.size()];
            var histories=new ArrayList<List<Long>>(selected.size());
            for(int i=0;i<selected.size();i++) {
                generations[i]=selected.get(i).generation; nextGenerations[i]=Math.incrementExact(generations[i]);
                var history=new ArrayList<>(selected.get(i).reactivatedBodyIds); history.add(bodyIds[i]); histories.add(List.copyOf(history));
            }
            nextBodyIds=List.copyOf(histories);
            expectedRevision=revision; nextRevision=Math.incrementExact(revision);
            expectedWorldRevision=world.mutationRevision(); expectedTopologyRevision=connectivity.revision();
        }
        public boolean owns(World candidate) { return world==candidate; }
        public boolean valid() {
            validRegistry();
            if(revision!=expectedRevision || world.mutationRevision()!=expectedWorldRevision || connectivity.revision()!=expectedTopologyRevision || connectivity.traversing()) return false;
            for(int j=0;j<selected.size();j++) {
                var entry=selected.get(j);
                if(entry.generation!=generations[j] || entry.count!=indices[j].length || entry.reactivatedBodyId!=0) return false;
                for(int i:indices[j]) if(owners[i]!=entry.id || world.get(i%world.width(),i/world.width())!=Material.STONE) return false;
            }
            return true;
        }
        public void publish(Object token) {
            if(!World.isReactivationPublicationToken(token)) throw new IllegalStateException("Not a reactivation publication");
            for(int j=0;j<selected.size();j++) {
                var entry=selected.get(j); for(int i:indices[j]) owners[i]=0;
                entry.count=0; entry.mass=0; entry.generation=nextGenerations[j]; entry.integrity=Integrity.REMOVED; entry.reactivatedBodyId=bodyIds[j]; entry.reactivatedBodyIds=nextBodyIds.get(j);
            }
            liveRocks-=selected.size();
            revision=nextRevision; connectivity.ownershipChanged();
        }
    }
    public PreparedGroupReactivationRemoval prepareGroupReactivationRemoval(List<ReactivationMapping> mappings) {
        Objects.requireNonNull(mappings); valid(); connectivity.checkOwnershipMutation();
        if(mappings.isEmpty()) throw new IllegalArgumentException("Empty reactivation group");
        Math.addExact(connectivity.revision(),2);
        var selected=new ArrayList<Entry>(); var seen=new java.util.HashSet<Long>(); var bodyIds=new long[mappings.size()]; int[][] indices=new int[mappings.size()][];
        for(int j=0;j<mappings.size();j++) {
            var mapping=Objects.requireNonNull(mappings.get(j)); long id=mapping.settledId();
            if(id<=0 || id>entries.size() || mapping.bodyId()<=0 || !seen.add(id)) throw new IllegalArgumentException("Invalid group identity");
            var entry=entries.get(Math.toIntExact(id-1));
            if(entry.count<=0 || entry.reactivatedBodyId!=0) throw new IllegalStateException("Settled rock is not live");
            var actual=cells(id); if(actual.size()!=entry.count) throw new IllegalStateException("Ownership count mismatch");
            indices[j]=new int[actual.size()]; int i=0;
            for(var c:actual) {
                if(world.get(c.x(),c.y())!=Material.STONE) throw new IllegalStateException("Owned cell is not STONE");
                indices[j][i++]=Math.addExact(Math.multiplyExact(c.y(),world.width()),c.x());
            }
            selected.add(entry); bodyIds[j]=mapping.bodyId();
        }
        return new PreparedGroupReactivationRemoval(List.copyOf(selected),indices,bodyIds);
    }
    /**
     * Assign metadata to already-owned STONE. This is NOT a rigid-to-static transfer.
     * Callers must have separately established provenance and exclusive ownership.
     * No World cell is created, moved or removed by this operation.
     */
    long registerExisting(long sourceFragmentId,FragmentDescriptor descriptor,int originX,int originY) {
        Objects.requireNonNull(descriptor);
        valid();
        connectivity.checkOwnershipMutation();
        if(sourceFragmentId<=0 || entries.size()>=maxRocks || descriptor.cellCount()>maxCellsPerRock)
            throw new IllegalArgumentException("Invalid settled source or budget exceeded");
        Math.addExact(revision,1);
        for(var cell:descriptor.cells()) {
            int x=Math.addExact(originX,cell.x()),y=Math.addExact(originY,cell.y());
            if(!world.contains(x,y) || world.get(x,y)!=Material.STONE || ownerAt(x,y)!=0)
                throw new IllegalArgumentException("Settled ownership requires unowned STONE");
        }
        // Reject an already broken internal bond rather than silently heal it.
        for(var cell:descriptor.cells()) {
            int x=originX+cell.x(),y=originY+cell.y();
            if(cell.x()+1<Integer.MAX_VALUE && java.util.Collections.binarySearch(descriptor.cells(),new FragmentDescriptor.Cell(cell.x()+1,cell.y()))>=0
                    && connectivity.bondState(x,y,x+1,y)!=RockConnectivity.BondState.INTACT)
                throw new IllegalArgumentException("Settled shape has a broken internal bond");
            if(cell.y()+1<Integer.MAX_VALUE && java.util.Collections.binarySearch(descriptor.cells(),new FragmentDescriptor.Cell(cell.x(),cell.y()+1))>=0
                    && connectivity.bondState(x,y,x,y+1)!=RockConnectivity.BondState.INTACT)
                throw new IllegalArgumentException("Settled shape has a broken internal bond");
        }
        long id=entries.size()+1L; var entry=new Entry(id,sourceFragmentId,descriptor,originX,originY);
        entries.add(entry);
        for(var cell:descriptor.cells()) owners[(originY+cell.y())*world.width()+originX+cell.x()]=id;
        liveRocks++; revision++; connectivity.ownershipChanged();
        for(var cell:descriptor.cells()) connectivity.supportOwnershipRegistered(originX+cell.x(),originY+cell.y());
        return id;
    }
    public PreparedInsertion prepareInsertion(long sourceFragmentId,FragmentDescriptor descriptor,int originX,int originY) {
        Objects.requireNonNull(descriptor); valid(); connectivity.checkOwnershipMutation();
        if(sourceFragmentId<=0 || entries.size()>=maxRocks || descriptor.cellCount()>maxCellsPerRock)
            throw new IllegalArgumentException("Invalid settled source or budget exceeded");
        // One ownership invalidation and one subsequent World change notification.
        Math.addExact(connectivity.revision(),2);
        int[] indices=new int[descriptor.cellCount()]; int i=0;
        for(var cell:descriptor.cells()) {
            int x=Math.addExact(originX,cell.x()),y=Math.addExact(originY,cell.y());
            if(!world.contains(x,y) || world.get(x,y)!=Material.AIR || ownerAt(x,y)!=0)
                throw new IllegalArgumentException("Settled insertion requires unowned AIR");
            indices[i++]=Math.addExact(Math.multiplyExact(y,world.width()),x);
        }
        entries.ensureCapacity(Math.incrementExact(entries.size()));
        return new PreparedInsertion(new Entry(entries.size()+1L,sourceFragmentId,descriptor,originX,originY),indices);
    }
    public record Insertion(long sourceFragmentId,FragmentDescriptor descriptor,int originX,int originY) { }
    /** An entire group's metadata shares one revision and one topology invalidation. */
    public final class PreparedGroupInsertion {
        private final long expectedRevision,expectedWorldRevision,expectedTopologyRevision,nextRevision;
        private final List<Entry> additions;
        private final int[][] indices;
        private PreparedGroupInsertion(List<Entry> additions,int[][] indices) {
            this.additions=additions; this.indices=indices;
            expectedRevision=revision; expectedWorldRevision=world.mutationRevision(); expectedTopologyRevision=connectivity.revision();
            nextRevision=Math.incrementExact(revision);
        }
        public List<Long> ids() { return additions.stream().map(e->e.id).toList(); }
        public boolean owns(World candidate) { return world==candidate; }
        public boolean valid() {
            validRegistry();
            if(revision!=expectedRevision || world.mutationRevision()!=expectedWorldRevision || connectivity.revision()!=expectedTopologyRevision
                    || connectivity.traversing() || entries.size()+1L!=additions.getFirst().id) return false;
            for(var row:indices) for(int i:row) if(owners[i]!=0 || world.get(i%world.width(),i/world.width())!=Material.AIR) return false;
            return true;
        }
        public void publish(Object token) {
            if(!World.isSettlingPublicationToken(token)) throw new IllegalStateException("Not a settling publication");
            for(int j=0;j<additions.size();j++) { var entry=additions.get(j); entries.add(entry); for(int i:indices[j]) owners[i]=entry.id; }
            liveRocks+=additions.size();
            revision=nextRevision; connectivity.ownershipChanged();
        }
    }
    public PreparedGroupInsertion prepareGroupInsertion(List<Insertion> input) {
        Objects.requireNonNull(input); valid(); connectivity.checkOwnershipMutation();
        if(input.isEmpty() || input.size()>maxRocks-entries.size()) throw new IllegalArgumentException("Invalid group size");
        Math.addExact(connectivity.revision(),2); Math.incrementExact(revision);
        var additions=new ArrayList<Entry>(input.size()); int[][] indices=new int[input.size()][];
        var seen=new java.util.HashSet<Integer>(); var sources=new java.util.HashSet<Long>();
        for(int j=0;j<input.size();j++) {
            var insertion=Objects.requireNonNull(input.get(j)); var descriptor=Objects.requireNonNull(insertion.descriptor());
            if(insertion.sourceFragmentId()<=0 || !sources.add(insertion.sourceFragmentId()) || descriptor.cellCount()>maxCellsPerRock)
                throw new IllegalArgumentException("Invalid source or cell limit");
            indices[j]=new int[descriptor.cellCount()]; int k=0;
            for(var cell:descriptor.cells()) {
                int x=Math.addExact(insertion.originX(),cell.x()),y=Math.addExact(insertion.originY(),cell.y());
                if(!world.contains(x,y) || world.get(x,y)!=Material.AIR || ownerAt(x,y)!=0)
                    throw new IllegalArgumentException("Settled insertion requires unowned AIR");
                int index=Math.addExact(Math.multiplyExact(y,world.width()),x);
                if(!seen.add(index)) throw new IllegalArgumentException("Overlapping group cells");
                indices[j][k++]=index;
            }
            additions.add(new Entry(entries.size()+1L+j,insertion.sourceFragmentId(),descriptor,insertion.originX(),insertion.originY()));
        }
        entries.ensureCapacity(Math.addExact(entries.size(),input.size()));
        return new PreparedGroupInsertion(List.copyOf(additions),indices);
    }
    void stoneRemoved(int x,int y) {
        int index=y*world.width()+x; long id=owners[index]; if(id==0) return;
        var entry=entries.get(Math.toIntExact(id-1)); owners[index]=0;
        entry.count--; entry.mass-=MassAccounting.cellMilligrams(entry.descriptor.substance());
        if(entry.count==0) liveRocks--;
        if(entry.generation==Long.MAX_VALUE) exhausted=true; else entry.generation++;
        entry.integrity=entry.count==0?Integrity.REMOVED:Integrity.DAMAGED;
        if(revision==Long.MAX_VALUE) exhausted=true; else revision++;
    }
    void bondBroken(int x,int y) {
        long id=ownerAt(x,y); if(id==0) return;
        var entry=entries.get(Math.toIntExact(id-1));
        entry.integrity=Integrity.DAMAGED;
        if(entry.generation==Long.MAX_VALUE) exhausted=true; else entry.generation++;
        if(revision==Long.MAX_VALUE) exhausted=true; else revision++;
    }
    void worldInvalidated() {
        for(int i=0;i<owners.length;i++) if(owners[i]!=0 && world.get(i%world.width(),i/world.width())!=Material.STONE)
            stoneRemoved(i%world.width(),i/world.width());
    }
    boolean hasOwnedCells() { for(var entry:entries) if(entry.count!=0) return true; return false; }
}

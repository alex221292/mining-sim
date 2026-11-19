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
                       int remainingCells,long massMilligrams,long generation,Integrity integrity) { }
    private static final class Entry {
        final long id,source; final FragmentDescriptor descriptor; final int x,y;
        int count; long mass,generation; Integrity integrity=Integrity.INTACT;
        Entry(long id,long source,FragmentDescriptor descriptor,int x,int y) {
            this.id=id; this.source=source; this.descriptor=descriptor; this.x=x; this.y=y;
            count=descriptor.cellCount(); mass=descriptor.massMilligrams();
        }
        Rock snapshot() { return new Rock(id,source,descriptor,x,y,count,mass,generation,integrity); }
    }
    private final World world;
    private final RockConnectivity connectivity;
    private final long[] owners;
    private final ArrayList<Entry> entries=new ArrayList<>();
    private final int maxRocks,maxCellsPerRock;
    private long revision;
    private boolean exhausted;
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
            entries.add(entry); for(int i:indices) owners[i]=entry.id;
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
        revision++; connectivity.ownershipChanged(); return id;
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

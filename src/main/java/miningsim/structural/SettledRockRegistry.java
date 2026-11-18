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
    public long revision() { valid(); return revision; }
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

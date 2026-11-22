package miningsim.structural;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import miningsim.world.World;

/** Resumable, read-only component enumeration for one settled ownership record. */
public final class ComponentEnumeration {
    public enum Status { IN_PROGRESS, COMPLETE, STALE, LIMIT, INVALID }
    private final World world;
    private final RockConnectivity topology;
    private final SettledRockRegistry settled;
    private final SettledRockRegistry.Rock rock;
    private final long terrainRevision,topologyRevision,ownershipRevision;
    private final TreeSet<Long> remaining=new TreeSet<>(),component=new TreeSet<>();
    private final ArrayDeque<Long> queue=new ArrayDeque<>();
    private final ArrayList<List<SettledRockRegistry.WorldCell>> output=new ArrayList<>();
    private final List<List<SettledRockRegistry.WorldCell>> view=Collections.unmodifiableList(output);
    private ArrayList<SettledRockRegistry.WorldCell> emitted;
    private int scan,phase,direction,owned;
    private long current,work,lastWork;
    private Status status=Status.IN_PROGRESS;
    public ComponentEnumeration(World world,RockConnectivity topology,SettledRockRegistry settled,long id,int maxCells) {
        this.world=Objects.requireNonNull(world); this.topology=Objects.requireNonNull(topology); this.settled=Objects.requireNonNull(settled);
        if(!settled.owns(world,topology) || maxCells<1) throw new IllegalArgumentException("Invalid component enumeration owners or limit");
        rock=settled.rock(id); terrainRevision=world.rigidTerrainRevision(); topologyRevision=topology.revision(); ownershipRevision=settled.revision();
        if(rock.descriptor().cellCount()>maxCells) status=Status.LIMIT;
    }
    private boolean stale() { return world.rigidTerrainRevision()!=terrainRevision || topology.revision()!=topologyRevision || settled.revision()!=ownershipRevision; }
    public Status status() { return status; }
    public long work() { return work; }
    public long lastWork() { return lastWork; }
    /** Returns complete data only; an unfinished or stale analysis cannot expose a partial result. */
    public List<List<SettledRockRegistry.WorldCell>> components() {
        if(status!=Status.COMPLETE || stale()) throw new IllegalStateException("Component enumeration incomplete or stale");
        return view;
    }
    /** One unit is one cell enumeration, queue operation, neighbour check, or result cell. */
    public Status step(int budget) {
        if(budget<1) throw new IllegalArgumentException("Budget must be positive");
        lastWork=0;
        if(stale()) { status=Status.STALE; return status; }
        while(lastWork<budget && status==Status.IN_PROGRESS) {
            if(phase==0) {
                if(scan==rock.descriptor().cellCount()) { if(owned!=rock.remainingCells()) status=Status.INVALID; else phase=1; continue; }
                var c=rock.descriptor().cells().get(scan++); int x=rock.originX()+c.x(),y=rock.originY()+c.y();
                if(settled.ownerAt(x,y)==rock.id()) { remaining.add((long)y*world.width()+x); owned++; }
            } else if(phase==1) {
                if(remaining.isEmpty()) { status=Status.COMPLETE; continue; }
                long first=remaining.pollFirst(); queue.addLast(first); component.add(first); phase=2;
            } else if(phase==2) {
                if(queue.isEmpty()) { emitted=new ArrayList<>(); phase=4; continue; }
                current=queue.removeFirst(); direction=0; phase=3;
            } else if(phase==3) {
                int x=(int)(current%world.width()),y=(int)(current/world.width());
                switch(direction++) { case 0 -> y--; case 1 -> x--; case 2 -> x++; case 3 -> y++; default -> throw new IllegalStateException(); }
                if(world.contains(x,y)) { long next=(long)y*world.width()+x; if(remaining.remove(next)) { component.add(next); queue.addLast(next); } }
                if(direction==4) phase=2;
            } else {
                if(component.isEmpty()) { output.add(Collections.unmodifiableList(emitted)); emitted=null; phase=1; continue; }
                long index=component.pollFirst(); emitted.add(new SettledRockRegistry.WorldCell((int)(index%world.width()),(int)(index/world.width())));
            }
            lastWork++; work++;
        }
        if(status==Status.IN_PROGRESS && stale()) status=Status.STALE;
        return status;
    }
}

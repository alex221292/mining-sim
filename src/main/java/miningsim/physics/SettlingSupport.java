package miningsim.physics;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Objects;
import miningsim.structural.RockConnectivity;
import miningsim.structural.SettledRockRegistry;
import miningsim.world.Material;
import miningsim.world.World;

/** Bounded conservative headless support check; no simulation state is mutated. */
public final class SettlingSupport {
    public enum Status { SUPPORTED, NOT_SUPPORTED, SUPPORT_UNVERIFIED, TOPOLOGY_PENDING, BUDGET_EXCEEDED, UNSAFE_OVERLAP, STALE }
    public record Result(Status status,int inspectedCells) { }
    private SettlingSupport() { }
    public static Result check(RigidBody body,World world,RockConnectivity topology,SettledRockRegistry settled,int maxCells) {
        Objects.requireNonNull(body); Objects.requireNonNull(world); Objects.requireNonNull(topology); Objects.requireNonNull(settled);
        if(maxCells<1) throw new IllegalArgumentException("Support budget must be positive");
        if(!topology.owns(world) || !settled.owns(world,topology)) throw new IllegalArgumentException("Support owners differ");
        if(topology.pending()) return new Result(Status.TOPOLOGY_PENDING,0);
        long worldRevision=world.mutationRevision(),terrainRevision=world.rigidTerrainRevision();
        long topologyRevision=topology.revision(),settledRevision=settled.revision();
        java.util.List<StaticCollisionQuery.Contact> contacts;
        try {
            contacts=new StaticCollisionQuery(world,maxCells,Math.multiplyExact((long)maxCells,8),StaticCollisionQuery.MaterialPolicy.DISPLACE_GRANULAR)
                    .queryHeldValidation(body,RigidBodySystem.MAX_HELD_PENETRATION_CELLS).contacts();
        } catch(IllegalStateException ex) {
            if(ex.getMessage()!=null && ex.getMessage().contains("HELD static penetration exceeds")) return new Result(Status.UNSAFE_OVERLAP,0);
            if(ex.getMessage()!=null && ex.getMessage().contains("limit exceeded")) return new Result(Status.BUDGET_EXCEEDED,0);
            throw ex;
        }
        var queue=new ArrayDeque<Long>(); var visited=new HashSet<Long>(); int inspected=0; boolean supported=false,unverified=false;
        for(var contact:contacts) {
            if(contact.normalY()<=0.5 || contact.boundary()!=StaticCollisionQuery.Boundary.NONE) continue;
            int x=contact.worldX(),y=contact.worldY(); var material=world.get(x,y);
            if(material==Material.BEDROCK) { supported=true; break; }
            if(material!=Material.STONE) continue;
            long owner=settled.ownerAt(x,y);
            if(owner==0) {
                // RockDamageSystem fixes all degrees of freedom for a cell touching BEDROCK.
                if(touchesBedrock(world,x,y)) { supported=true; break; }
                unverified=true;
            } else if(visited.add(owner)) queue.addLast(owner);
        }
        while(!supported && !queue.isEmpty()) {
            long id=queue.removeFirst(); var rock=settled.rock(id);
            if(rock.integrity()!=SettledRockRegistry.Integrity.INTACT) continue;
            if(rock.descriptor().cellCount()>maxCells-inspected) return new Result(Status.BUDGET_EXCEEDED,inspected);
            for(var cell:settled.cells(id)) {
                inspected++;
                int x=cell.x(),y=cell.y()+1;
                if(!world.contains(x,y)) continue;
                var material=world.get(x,y);
                if(material==Material.BEDROCK) { supported=true; break; }
                if(material!=Material.STONE) continue;
                long below=settled.ownerAt(x,y);
                if(below==0) {
                    if(touchesBedrock(world,x,y)) { supported=true; break; }
                    unverified=true;
                } else if(below!=id && visited.add(below)) queue.addLast(below);
            }
        }
        if(world.mutationRevision()!=worldRevision || world.rigidTerrainRevision()!=terrainRevision || topology.pending()
                || topology.revision()!=topologyRevision || settled.revision()!=settledRevision) return new Result(Status.STALE,inspected);
        return new Result(supported?Status.SUPPORTED:unverified?Status.SUPPORT_UNVERIFIED:Status.NOT_SUPPORTED,inspected);
    }
    private static boolean touchesBedrock(World world,int x,int y) {
        return x>0 && world.get(x-1,y)==Material.BEDROCK || x+1<world.width() && world.get(x+1,y)==Material.BEDROCK
                || y>0 && world.get(x,y-1)==Material.BEDROCK || y+1<world.height() && world.get(x,y+1)==Material.BEDROCK;
    }
    /** Rechecks support for the proposed cell shape, independently of the original pose. */
    public static Result checkPlacement(java.util.List<SettledRockPlacementPlanner.Cell> cells,World world,RockConnectivity topology,
                                        SettledRockRegistry settled,int maxCells) {
        Objects.requireNonNull(cells); Objects.requireNonNull(world); Objects.requireNonNull(topology); Objects.requireNonNull(settled);
        if(maxCells<1) throw new IllegalArgumentException("Support budget must be positive");
        if(!topology.owns(world) || !settled.owns(world,topology)) throw new IllegalArgumentException("Support owners differ");
        if(topology.pending()) return new Result(Status.TOPOLOGY_PENDING,0);
        long w=world.mutationRevision(),t=world.rigidTerrainRevision(),r=topology.revision(),s=settled.revision();
        var queue=new ArrayDeque<Long>(); var visited=new HashSet<Long>(); int inspected=0; boolean unverified=false,supported=false;
        for(var cell:cells) {
            if(++inspected>maxCells) return new Result(Status.BUDGET_EXCEEDED,inspected);
            int x=cell.x(),y=cell.y()+1; if(!world.contains(x,y)) continue;
            var material=world.get(x,y);
            if(material==Material.BEDROCK) { supported=true; break; }
            if(material!=Material.STONE) continue;
            long id=settled.ownerAt(x,y);
            if(id==0) { if(touchesBedrock(world,x,y)) { supported=true; break; } unverified=true; }
            else if(visited.add(id)) queue.addLast(id);
        }
        while(!supported && !queue.isEmpty()) {
            var rock=settled.rock(queue.removeFirst());
            if(rock.integrity()!=SettledRockRegistry.Integrity.INTACT) continue;
            if(rock.descriptor().cellCount()>maxCells-inspected) return new Result(Status.BUDGET_EXCEEDED,inspected);
            for(var cell:settled.cells(rock.id())) {
                inspected++; int x=cell.x(),y=cell.y()+1; if(!world.contains(x,y)) continue;
                var material=world.get(x,y);
                if(material==Material.BEDROCK) { supported=true; break; }
                if(material!=Material.STONE) continue;
                long id=settled.ownerAt(x,y);
                if(id==0) { if(touchesBedrock(world,x,y)) { supported=true; break; } unverified=true; }
                else if(id!=rock.id() && visited.add(id)) queue.addLast(id);
            }
        }
        if(world.mutationRevision()!=w || world.rigidTerrainRevision()!=t || topology.pending() || topology.revision()!=r || settled.revision()!=s)
            return new Result(Status.STALE,inspected);
        return new Result(supported?Status.SUPPORTED:unverified?Status.SUPPORT_UNVERIFIED:Status.NOT_SUPPORTED,inspected);
    }
}

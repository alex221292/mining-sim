package miningsim.simulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import miningsim.physics.RigidBodyOccupancyIndex;
import miningsim.world.Material;
import miningsim.world.World;

/** Read-only deterministic displacement planning; commit is a separate operation. */
public final class CellularDisplacementPlanner {
    public enum Kind { EMPTY, SOLID, DISPLACEABLE }
    public static Kind classify(Material material) {
        return switch (Objects.requireNonNull(material)) {
            case AIR -> Kind.EMPTY;
            case STONE, BEDROCK, WOOD -> Kind.SOLID;
            case SAND, GRAVEL, SOIL, WATER -> Kind.DISPLACEABLE;
        };
    }
    public record Limits(int maxSources, long maxCandidates, long maxReachabilityChecks,
                         int maxMoves, long maxDistance, int maxRadius) {
        public Limits {
            if(maxSources<0 || maxCandidates<0 || maxReachabilityChecks<0 || maxMoves<0 || maxDistance<0 || maxRadius<1)
                throw new IllegalArgumentException("Invalid displacement limits");
        }
        public static Limits defaults() { return new Limits(10000,1000000,1000000,10000,100000,32); }
    }
    public record Move(int fromX,int fromY,int toX,int toY,Material material,int distance) { }
    public record Plan(List<Move> moves,List<World.CellChange> changes,long candidates,long reachabilityChecks,long totalDistance,int maxDistance) {
        public Plan { moves=List.copyOf(moves); changes=List.copyOf(changes); }
    }
    private record Position(int x,int y,int distance) { }
    private static final class Counters { long candidates,checks,distance; int maxDistance; }
    private CellularDisplacementPlanner() { }

    /** BFS order: sand/soil/gravel down,left,right,up; water left,right,down,up. */
    public static Plan plan(World world, RigidBodyOccupancyIndex.Snapshot occupancy, Limits limits) {
        Objects.requireNonNull(world); Objects.requireNonNull(occupancy); Objects.requireNonNull(limits);
        if(world.width()!=occupancy.width() || world.height()!=occupancy.height()) throw new IllegalArgumentException("Occupancy dimensions do not match world");
        var sources=new ArrayList<Position>();
        occupancy.forEachCell((x,y)-> {
            if(classify(world.get(x,y))==Kind.DISPLACEABLE) {
                if(sources.size()>=limits.maxSources()) throw new IllegalStateException("Displacement source limit exceeded at "+x+","+y+" ("+world.get(x,y)+")");
                sources.add(new Position(x,y,0));
            }
        });
        var moves=new ArrayList<Move>(); var changes=new ArrayList<World.CellChange>();
        Set<Long> reserved=new HashSet<>(), vacated=new HashSet<>(); var counts=new Counters();
        for(var source:sources) {
            Material material=world.get(source.x(),source.y());
            if(moves.size()>=limits.maxMoves()) throw fail(source,material,"move limit exceeded");
            var destination=search(world,occupancy,limits,source,material,reserved,vacated,counts);
            counts.distance=Math.addExact(counts.distance,destination.distance());
            if(counts.distance>limits.maxDistance()) throw fail(source,material,"total distance limit exceeded");
            counts.maxDistance=Math.max(counts.maxDistance,destination.distance());
            reserved.add(key(world,destination.x(),destination.y())); vacated.add(key(world,source.x(),source.y()));
            moves.add(new Move(source.x(),source.y(),destination.x(),destination.y(),material,destination.distance()));
            changes.add(new World.CellChange(source.x(),source.y(),material,Material.AIR));
            changes.add(new World.CellChange(destination.x(),destination.y(),Material.AIR,material));
        }
        return new Plan(moves,changes,counts.candidates,counts.checks,counts.distance,counts.maxDistance);
    }
    private static Position search(World world,RigidBodyOccupancyIndex.Snapshot occupancy,Limits limits,Position source,Material material,
                                   Set<Long> reserved,Set<Long> vacated,Counters counts) {
        var queue=new ArrayDeque<Position>(); var seen=new HashSet<Long>();
        queue.add(source); seen.add(key(world,source.x(),source.y()));
        int[][] directions=material==Material.WATER ? new int[][]{{-1,0},{1,0},{0,1},{0,-1}} : new int[][]{{0,1},{-1,0},{1,0},{0,-1}};
        while(!queue.isEmpty()) {
            var p=queue.removeFirst(); if(p.distance()>=limits.maxRadius()) continue;
            for(var d:directions) {
                int x=p.x()+d[0],y=p.y()+d[1]; counts.checks=Math.incrementExact(counts.checks);
                if(counts.checks>limits.maxReachabilityChecks()) throw fail(source,material,"reachability budget exceeded");
                if(!world.contains(x,y) || !seen.add(key(world,x,y))) continue;
                counts.candidates=Math.incrementExact(counts.candidates);
                if(counts.candidates>limits.maxCandidates()) throw fail(source,material,"candidate budget exceeded");
                long key=key(world,x,y);
                if(reserved.contains(key) || world.get(x,y)!=Material.AIR && !vacated.contains(key)) continue;
                var next=new Position(x,y,p.distance()+1);
                // Vacated sources may be traversed but never become destinations: the
                // batch is validated against the original World, not intermediate writes.
                if(!occupancy.occupied(x,y) && world.get(x,y)==Material.AIR) return next;
                queue.addLast(next);
            }
        }
        throw fail(source,material,"no reachable destination within radius "+limits.maxRadius());
    }
    private static long key(World world,int x,int y) { return (long)y*world.width()+x; }
    private static IllegalStateException fail(Position source,Material material,String reason) {
        return new IllegalStateException("Displacement "+reason+" at "+source.x()+","+source.y()+" ("+material+")");
    }
}

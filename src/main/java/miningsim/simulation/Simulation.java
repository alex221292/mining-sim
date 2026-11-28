package miningsim.simulation;

import java.util.Arrays;
import java.util.Random;
import java.util.ArrayList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import miningsim.world.Material;
import miningsim.world.World;
import miningsim.physics.RigidBodyOccupancyIndex;

public final class Simulation {
    public enum Mode { FULL_SCAN, ACTIVE_REGIONS, DIAGNOSTIC_FULL_TRACKED, DIAGNOSTIC_TILED_TRACKED }
    private static final int WATER_REACH = 8;
    /** Hard bound for exceptional, off-world recovery; normal ticks do not copy the world. */
    private static final long MAX_RECOVERY_CELLS = 2_000_000;
    private final World world;
    private Random random;
    private final boolean[] moved;
    private final boolean[] active;
    private final World.RowSegment rowSegment;
    private final int tileColumns;
    private final Mode mode;
    private final boolean instrument;
    private long visited, movements, collectNanos, resetNanos, tickNanos;
    private int activeTiles;
    private RigidBodyOccupancyIndex.Snapshot occupancy;
    private Runnable recoveryTestCheckpoint;

    public Simulation(World world, long seed) {
        this(world, seed, Mode.FULL_SCAN, false);
    }

    public Simulation(World world, long seed, Mode mode, boolean instrument) {
        this.world = world;
        this.mode = java.util.Objects.requireNonNull(mode);
        this.instrument = instrument;
        random = new Random(seed);
        moved = new boolean[world.width() * world.height()];
        tileColumns = world.activityTileColumns();
        active = new boolean[tileColumns * world.activityTileRows()];
        rowSegment = world.newRowSegment();
        world.setActivityTracking(mode != Mode.FULL_SCAN);
    }

    public int totalTiles() { return active.length; }
    public int activeTiles() { return activeTiles; }
    public long visitedCells() { return visited; }
    public long movements() { return movements; }
    public long collectNanos() { return collectNanos; }
    public long resetNanos() { return resetNanos; }
    public long tickNanos() { return tickNanos; }
    public boolean owns(World candidate) { return world == candidate; }

    /** Package-private fault injection for a prepared recovery step. */
    void setRecoveryTestCheckpoint(Runnable checkpoint) { recoveryTestCheckpoint=checkpoint; }

    /**
     * Prepare a FULL_SCAN step in an isolated World, then commit its complete
     * material delta and RNG state only after successful batch publication.
     * Returns false without publishing on budget, preparation or commit failure.
     */
    public boolean stepRecoveryAtomically(RigidBodyOccupancyIndex.Snapshot snapshot) {
        if(mode!=Mode.FULL_SCAN || (long)world.width()*world.height()>MAX_RECOVERY_CELLS) return false;
        long revision=world.mutationRevision();
        Simulation fork;
        ArrayList<World.CellChange> changes;
        try {
            var shadow=new World(world.width(),world.height(),world.mode());
            for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) {
                var material=world.get(x,y);
                // Historical rigid-body recovery has no metadata-aware batch contract.
                if(material.isGrit()) return false;
                if(material!=Material.AIR && material!=Material.BEDROCK && shadow.set(x,y,material)!=World.Mutation.CHANGED) return false;
                if(material==Material.BEDROCK && shadow.get(x,y)!=Material.BEDROCK) return false;
            }
            fork=new Simulation(shadow,0,Mode.FULL_SCAN,false);
            fork.random=copyRandom(random); fork.recoveryTestCheckpoint=recoveryTestCheckpoint;
            fork.setOccupancy(snapshot); fork.step();
            changes=new ArrayList<World.CellChange>();
            for(int y=0;y<world.height();y++) for(int x=0;x<world.width();x++) {
                var before=world.get(x,y); var after=shadow.get(x,y);
                if(before!=after) changes.add(new World.CellChange(x,y,before,after));
            }
            if(world.mutationRevision()!=revision) return false;
        } catch(RuntimeException ex) {
            // Preparation touches only the shadow and cannot publish a partial step.
            return false;
        }
        // As with all World batches, ChangeSink callbacks must not throw.
        if(world.replaceAllIfMatches(changes)==World.BatchResult.REJECTED) return false;
        random=fork.random; occupancy=snapshot;
        return true;
    }
    private static Random copyRandom(Random source) {
        try {
            var bytes=new ByteArrayOutputStream();
            try(var output=new ObjectOutputStream(bytes)) { output.writeObject(source); }
            try(var input=new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (Random)input.readObject(); }
        } catch(IOException | ClassNotFoundException ex) { throw new IllegalStateException("Cannot checkpoint cellular RNG",ex); }
    }

    /** Publishing a different snapshot wakes all activity tiles conservatively. */
    public void setOccupancy(RigidBodyOccupancyIndex.Snapshot snapshot) {
        if (snapshot != null && (snapshot.width() != world.width() || snapshot.height() != world.height()))
            throw new IllegalArgumentException("Occupancy dimensions do not match world");
        if (occupancy != snapshot && mode == Mode.ACTIVE_REGIONS) Arrays.fill(active, true);
        occupancy = snapshot;
    }
    private boolean blocked(int x, int y) { return occupancy != null && occupancy.occupied(x,y); }

    public void step() {
        long tickStart = instrument ? System.nanoTime() : 0;
        if (instrument) { visited = 0; movements = 0; activeTiles = 0; }
        long start = instrument ? System.nanoTime() : 0;
        if (mode == Mode.ACTIVE_REGIONS) world.collectActivity(active);
        if (instrument) {
            collectNanos = System.nanoTime() - start;
            if (mode != Mode.ACTIVE_REGIONS) activeTiles = active.length;
            else for (boolean tile : active) if (tile) activeTiles++;
        }
        start = instrument ? System.nanoTime() : 0;
        Arrays.fill(moved, false);
        if (instrument) resetNanos = System.nanoTime() - start;
        boolean reverse = random.nextBoolean();
        for (int y = world.height() - 1; y >= 0; y--) {
            if (mode == Mode.FULL_SCAN || mode == Mode.DIAGNOSTIC_FULL_TRACKED) {
                scanRange(y, 0, world.width(), reverse);
                continue;
            }
            for (int column = 0; column < tileColumns; column++) {
                int tileX = reverse ? tileColumns - 1 - column : column;
                if (mode == Mode.ACTIVE_REGIONS && !active[(y / World.ACTIVITY_TILE_SIZE) * tileColumns + tileX]) continue;
                int tileStart = tileX * World.ACTIVITY_TILE_SIZE;
                int end = Math.min(world.width(), tileStart + World.ACTIVITY_TILE_SIZE);
                scanRange(y, tileStart, end, reverse);
            }
        }
        start = instrument ? System.nanoTime() : 0;
        if (mode == Mode.ACTIVE_REGIONS) Arrays.fill(active, false);
        if (instrument) { resetNanos += System.nanoTime() - start; tickNanos = System.nanoTime() - tickStart; }
    }

    private void scanRange(int y, int start, int end, boolean reverse) {
        if (instrument) visited += end - start;
        int x = reverse ? end - 1 : start;
        while (x >= start && x < end) {
            rowSegment.bind(y, x);
            int segmentStart = Math.max(start, x / World.CHUNK_SIZE * World.CHUNK_SIZE);
            int segmentEnd = Math.min(end, segmentStart + World.CHUNK_SIZE - (segmentStart % World.CHUNK_SIZE));
            while (x >= segmentStart && x < segmentEnd) {
                int current = x;
                x += reverse ? -1 : 1;
                if (moved[y * world.width() + current]) continue;
                Material material = rowSegment.get(current);
                if (material.category() != Material.Category.GRANULAR && material.category() != Material.Category.LIQUID) continue;
                if (move(current, y, current, y + 1, material)) continue;
                if (material == Material.SOIL) continue;
                int direction = random.nextBoolean() ? 1 : -1;
                if (diagonal(current, y, direction, material) || diagonal(current, y, -direction, material)) continue;
                if (material == Material.WATER) flowSideways(current, y, direction);
            }
        }
    }

    private boolean diagonal(int x, int y, int direction, Material material) {
        int nx = x + direction;
        if (material == Material.GRAVEL && (!world.contains(nx, y) || blocked(nx,y) || world.get(nx, y) != Material.AIR)) return false;
        return move(x, y, nx, y + 1, material);
    }

    private void flowSideways(int x, int y, int direction) {
        int first = distanceToDrop(x, y, direction);
        int second = distanceToDrop(x, y, -direction);
        if (first > 0 || second > 0) {
            int offset = first > 0 && (second == 0 || first <= second) ? direction * first : -direction * second;
            move(x, y, x + offset, y, Material.WATER);
            return;
        }
        // When the drop is beyond the search horizon, travel toward the longer
        // open run. Water cannot cross other materials or an occupied cell.
        if (world.contains(x, y + 1) && world.get(x, y + 1) == Material.WATER) {
            int forward = openRun(x, y, direction), backward = openRun(x, y, -direction);
            if (forward > 0 || backward > 0) {
                int offset = forward >= backward ? direction * forward : -direction * backward;
                move(x, y, x + offset, y, Material.WATER);
            }
            return;
        }
        if (!move(x, y, x + direction, y, Material.WATER))
            move(x, y, x - direction, y, Material.WATER);
    }

    private int openRun(int x, int y, int direction) {
        int distance = 0;
        while (distance < WATER_REACH) {
            int nx = x + direction * (distance + 1);
            if (!world.contains(nx, y) || blocked(nx,y) || world.get(nx, y) != Material.AIR) break;
            distance++;
        }
        return distance;
    }

    // Only cross contiguous air; move at most once, to the first reachable drop.
    private int distanceToDrop(int x, int y, int direction) {
        for (int distance = 1; distance <= WATER_REACH; distance++) {
            int nx = x + direction * distance;
            if (!world.contains(nx, y) || blocked(nx,y) || world.get(nx, y) != Material.AIR) break;
            if (world.contains(nx, y + 1) && !blocked(nx,y+1) && world.get(nx, y + 1) == Material.AIR) return distance;
        }
        return 0;
    }

    private boolean move(int x, int y, int nx, int ny, Material material) {
        if (!world.contains(nx, ny) || blocked(x,y) || blocked(nx,ny)) return false;
        Material target = world.get(nx, ny);
        // Granular materials displace liquid, preserving both particles.
        if (target != Material.AIR && !material.canDisplace(target)) return false;
        if (target == Material.WATER && moved[ny * world.width() + nx]) return false;
        if (material.isGrit()) world.swapCells(x, y, nx, ny);
        else {
            world.set(nx, ny, material);
            world.set(x, y, target);
        }
        moved[ny * world.width() + nx] = true;
        if (target == Material.WATER) moved[y * world.width() + x] = true;
        if (instrument) movements++;
        if(recoveryTestCheckpoint!=null) recoveryTestCheckpoint.run();
        return true;
    }
}

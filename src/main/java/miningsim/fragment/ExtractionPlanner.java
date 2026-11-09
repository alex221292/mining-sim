package miningsim.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.FragmentDescriptor.Cell;
import miningsim.resource.SubstanceRegistry;
import miningsim.structural.RockConnectivity;
import miningsim.world.Material;
import miningsim.world.World;

/** Read-only preparation; ownership transfer is performed by ExtractionCommit. */
public final class ExtractionPlanner {
    public enum Status { READY, PENDING, NOT_STONE, SUPPORTED, TOO_LARGE, STALE, OVERFLOW }
    public record Plan(World sourceWorld, RockConnectivity connectivity, long revision, int componentId,
                       int originX, int originY, List<Cell> worldCells, FragmentDescriptor descriptor) {
        public Plan { worldCells = List.copyOf(worldCells); }
    }
    public record Result(Status status, Plan plan) { }
    private ExtractionPlanner() { }
    public static Result prepare(World world, RockConnectivity connectivity, int x, int y, int maxCells) {
        Objects.requireNonNull(world); Objects.requireNonNull(connectivity);
        if (maxCells <= 0) throw new IllegalArgumentException("Maximum cell count must be positive");
        if (!connectivity.owns(world)) throw new IllegalArgumentException("Connectivity belongs to another world");
        if (!world.contains(x,y)) throw new IndexOutOfBoundsException();
        if (connectivity.pending()) return new Result(Status.PENDING,null);
        long revision=connectivity.revision();
        if (world.get(x,y) != Material.STONE) return new Result(Status.NOT_STONE,null);
        int component=connectivity.componentAt(x,y), ordinal=connectivity.componentOrdinal(component);
        if (ordinal < 0) return new Result(Status.STALE,null);
        if (connectivity.foundationConnected(ordinal)) return new Result(Status.SUPPORTED,null);
        if (connectivity.componentSize(ordinal) > maxCells) return new Result(Status.TOO_LARGE,null);
        List<Cell> worldCells = new ArrayList<>(); int minX=world.width(), minY=world.height();
        for(int index:connectivity.componentCells(ordinal)) {
            int col=index%world.width(),row=index/world.width(); worldCells.add(new Cell(col,row)); minX=Math.min(minX,col); minY=Math.min(minY,row);
        }
        if (connectivity.pending() || connectivity.revision()!=revision || worldCells.size()!=connectivity.componentSize(ordinal)) return new Result(Status.STALE,null);
        List<Cell> local = new ArrayList<>(); for (Cell cell:worldCells) local.add(new Cell(cell.x()-minX,cell.y()-minY));
        try {
            FragmentDescriptor descriptor = new FragmentDescriptor(SubstanceRegistry.reference().get(SubstanceRegistry.STONE),local);
            return new Result(Status.READY,new Plan(world,connectivity,revision,component,minX,minY,worldCells,descriptor));
        } catch (ArithmeticException ex) { return new Result(Status.OVERFLOW,null); }
    }
}

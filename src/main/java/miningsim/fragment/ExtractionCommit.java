package miningsim.fragment;

import java.util.ArrayList;
import java.util.Objects;
import miningsim.world.Material;
import miningsim.world.World;

/** Single-threaded preflight and allocation-free ownership publication. */
public final class ExtractionCommit {
    public enum Status { COMMITTED, STALE, PENDING, INVALID, CAPACITY, OVERFLOW, TRAVERSING }
    public record Result(Status status, RockFragment fragment) { }
    private ExtractionCommit() { }
    public static Result commit(ExtractionPlanner.Plan plan, FragmentRegistry registry) {
        Objects.requireNonNull(plan); Objects.requireNonNull(registry);
        var world = plan.sourceWorld(); var c = plan.connectivity();
        if (world == null || c == null || plan.descriptor() == null) return new Result(Status.INVALID,null);
        if (!c.owns(world)) return new Result(Status.INVALID,null);
        if (c.traversing()) return new Result(Status.TRAVERSING,null);
        if (c.revision() != plan.revision()) return new Result(Status.STALE,null);
        if (c.revision() == Long.MAX_VALUE) return new Result(Status.OVERFLOW,null);
        if (c.pending()) return new Result(Status.PENDING,null);
        if (plan.worldCells().isEmpty()) return new Result(Status.INVALID,null);
        var first = plan.worldCells().getFirst();
        if (!world.contains(first.x(),first.y())) return new Result(Status.INVALID,null);
        var verified = ExtractionPlanner.prepare(world,c,first.x(),first.y(),plan.worldCells().size());
        if (verified.status() != ExtractionPlanner.Status.READY) return new Result(Status.STALE,null);
        var fresh=verified.plan();
        if (fresh.componentId()!=plan.componentId() || fresh.originX()!=plan.originX() || fresh.originY()!=plan.originY()
                || !fresh.worldCells().equals(plan.worldCells()) || !fresh.descriptor().cells().equals(plan.descriptor().cells())
                || !fresh.descriptor().substance().equals(plan.descriptor().substance()) || fresh.descriptor().massMilligrams()!=plan.descriptor().massMilligrams())
            return new Result(Status.INVALID,null);
        FragmentRegistry.Prepared reservation;
        try { reservation = registry.prepare(fresh.descriptor(),fresh.originX(),fresh.originY(),fresh.componentId()); }
        catch (ArithmeticException ex) { return new Result(Status.OVERFLOW,null); }
        catch (IllegalStateException ex) { return new Result(Status.CAPACITY,null); }
        var changes = new ArrayList<World.CellChange>();
        for (var cell : fresh.worldCells()) changes.add(new World.CellChange(cell.x(),cell.y(),Material.STONE,Material.AIR));
        if (c.pending() || c.revision()!=plan.revision()) return new Result(Status.STALE,null);
        if (world.replaceAllIfMatches(changes,reservation) != World.BatchResult.CHANGED) return new Result(Status.INVALID,null);
        return new Result(Status.COMMITTED,reservation.fragment());
    }
}

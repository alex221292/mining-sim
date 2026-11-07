package miningsim.resource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pure seeded removal of original boundary cells; no World or rigid-body mutation. */
public record FragmentationPlan(FragmentDescriptor input, List<FragmentDescriptor> remainder,
                                List<FragmentDescriptor.Cell> removed, MaterialSample debris, long debrisMilligrams) {
    public FragmentationPlan {
        remainder = List.copyOf(remainder); removed = List.copyOf(removed);
        java.util.Objects.requireNonNull(input); java.util.Objects.requireNonNull(debris);
        if (!debris.substance().equals(input.substance().id()) || debris.form() != MaterialForm.GRIT)
            throw new IllegalArgumentException("Debris must be same-substance grit");
        var all = new HashSet<FragmentDescriptor.Cell>();
        for (var f : remainder) {
            if (!f.substance().equals(input.substance())) throw new IllegalArgumentException("Wrong substance");
            for (var c : f.cells()) if (!all.add(c)) throw new IllegalArgumentException("Duplicate ownership");
        }
        for (var c : removed) if (!all.add(c)) throw new IllegalArgumentException("Duplicate ownership");
        if (!new HashSet<>(surface(input)).containsAll(removed)) throw new IllegalArgumentException("Removal must use original surface cells");
        if (!all.equals(new HashSet<>(input.cells())) || debrisMilligrams != MassAccounting.solidCellsMilligrams(input.substance(),removed.size()))
            throw new IllegalArgumentException("Geometry or mass mismatch");
    }
    public static List<FragmentDescriptor.Cell> surface(FragmentDescriptor f) {
        Set<FragmentDescriptor.Cell> all = new HashSet<>(f.cells()); List<FragmentDescriptor.Cell> result = new ArrayList<>();
        for (var c : f.cells()) {
            boolean edge = false;
            for (int[] d : new int[][]{{-1,0},{1,0},{0,-1},{0,1}}) {
                long x=(long)c.x()+d[0],y=(long)c.y()+d[1];
                if (x<0 || y<0 || x>=Integer.MAX_VALUE || y>=Integer.MAX_VALUE || !all.contains(new FragmentDescriptor.Cell((int)x,(int)y))) { edge=true; break; }
            }
            if (edge) result.add(c);
        }
        return List.copyOf(result);
    }
    public static FragmentationPlan select(FragmentDescriptor input, int count, long seed) {
        var candidates = new ArrayList<>(surface(input));
        if (count < 0 || count > candidates.size()) throw new IllegalArgumentException("Invalid removal count");
        Collections.shuffle(candidates,new Random(seed)); var removed = new ArrayList<>(candidates.subList(0,count)); Collections.sort(removed);
        var rest = new HashSet<>(input.cells()); rest.removeAll(removed);
        List<FragmentDescriptor> remainder = new ArrayList<>();
        for (var part : FragmentDescriptor.components(rest)) remainder.add(new FragmentDescriptor(input.substance(),part));
        return new FragmentationPlan(input,remainder,removed,new MaterialSample(input.substance().id(),MaterialForm.GRIT),
                MassAccounting.solidCellsMilligrams(input.substance(),count));
    }
}

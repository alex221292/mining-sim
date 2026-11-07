package miningsim.fragment;

import java.util.Objects;
import miningsim.resource.FragmentDescriptor;
import miningsim.resource.SubstanceId;

/** Stationary extracted geometry; position is the world coordinate of local cell (0,0). */
public record RockFragment(long id, FragmentDescriptor descriptor, int originX, int originY, int sourceComponentId) {
    public RockFragment {
        Objects.requireNonNull(descriptor);
        if (id <= 0 || originX < 0 || originY < 0 || sourceComponentId < 0) throw new IllegalArgumentException("Invalid fragment identity or placement");
        if (descriptor.cells().stream().mapToInt(FragmentDescriptor.Cell::x).min().orElseThrow() != 0
                || descriptor.cells().stream().mapToInt(FragmentDescriptor.Cell::y).min().orElseThrow() != 0)
            throw new IllegalArgumentException("Local geometry must start at minimum x/y zero");
        Math.addExact(originX, descriptor.width() - 1); Math.addExact(originY, descriptor.height() - 1);
    }
    public SubstanceId substance() { return descriptor.substance().id(); }
    public long massMilligrams() { return descriptor.massMilligrams(); }
    public double orientationRadians() { return 0; }
    public int worldX(FragmentDescriptor.Cell cell) { return Math.addExact(originX, cell.x()); }
    public int worldY(FragmentDescriptor.Cell cell) { return Math.addExact(originY, cell.y()); }
}

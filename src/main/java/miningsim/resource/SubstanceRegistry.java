package miningsim.resource;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import miningsim.world.Material;

public final class SubstanceRegistry {
    public static final SubstanceId STONE = new SubstanceId("stone"), MARBLE = new SubstanceId("marble"),
            GRANITE = new SubstanceId("granite"), LIMESTONE = new SubstanceId("limestone"), CLAY = new SubstanceId("clay");
    private final Map<SubstanceId, SubstanceProperties> entries;
    public SubstanceRegistry(Iterable<SubstanceProperties> values) {
        TreeMap<SubstanceId, SubstanceProperties> map = new TreeMap<>();
        for (var value : values) if (map.putIfAbsent(value.id(), value) != null) throw new IllegalArgumentException("Duplicate substance ID");
        entries = Map.copyOf(map);
    }
    public SubstanceProperties get(SubstanceId id) {
        var p = entries.get(id); if (p == null) throw new IllegalArgumentException("Unknown substance: " + id); return p;
    }
    public static SubstanceRegistry reference() {
        return new SubstanceRegistry(java.util.List.of(
                entry(STONE,"Stone",2500,SubstanceProperties.Classification.ROCK), entry(MARBLE,"Marble",2700,SubstanceProperties.Classification.ROCK),
                entry(GRANITE,"Granite",2700,SubstanceProperties.Classification.ROCK), entry(LIMESTONE,"Limestone",2400,SubstanceProperties.Classification.ROCK),
                entry(CLAY,"Clay",1800,SubstanceProperties.Classification.EARTH)));
    }
    private static SubstanceProperties entry(SubstanceId id, String name, long density, SubstanceProperties.Classification kind) {
        return new SubstanceProperties(id,name,density,kind,Optional.empty());
    }
    /** Empty means no identified substance; in particular legacy GRAVEL is unspecified. */
    public static Optional<MaterialSample> legacyCell(Material material) {
        return material == Material.STONE ? Optional.of(new MaterialSample(STONE, MaterialForm.SOLID_MASS)) : Optional.empty();
    }
}

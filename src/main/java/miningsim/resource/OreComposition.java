package miningsim.resource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Immutable canonical mass fractions in parts per million. */
public final class OreComposition {
    public static final int MAX_PPM=1_000_000;
    public record Component(OreId ore,int ppm) {
        public Component {
            Objects.requireNonNull(ore);
            if(ppm<=0 || ppm>MAX_PPM) throw new IllegalArgumentException("Invalid ore concentration");
        }
    }
    public static final OreComposition EMPTY=new OreComposition(List.of());
    private final List<Component> components;
    private OreComposition(List<Component> components) { this.components=components; }
    public static OreComposition of(Component... entries) {
        Objects.requireNonNull(entries);
        if(entries.length==0) return EMPTY;
        var copy=entries.clone();
        for(var c:copy) Objects.requireNonNull(c);
        Arrays.sort(copy,Comparator.comparingInt(c->c.ore().id()));
        long total=0; int previous=0;
        for(var c:copy) {
            if(c.ore().id()==previous) throw new IllegalArgumentException("Duplicate ore ID");
            previous=c.ore().id(); total+=c.ppm();
            if(total>MAX_PPM) throw new IllegalArgumentException("Ore sum exceeds 100%");
        }
        return new OreComposition(List.of(copy));
    }
    public List<Component> components() { return components; }
    public int ppm(OreId ore) { for(var c:components) if(c.ore()==ore) return c.ppm(); return 0; }
    @Override public boolean equals(Object other) { return other instanceof OreComposition c && components.equals(c.components); }
    @Override public int hashCode() { return components.hashCode(); }
    @Override public String toString() { return components.toString(); }
}

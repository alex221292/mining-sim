package miningsim.resource;

import java.util.Objects;

/** Stable textual identifier, independent of enum ordinals and registry order. */
public record SubstanceId(String value) implements Comparable<SubstanceId> {
    public SubstanceId {
        Objects.requireNonNull(value);
        if (!value.matches("[a-z][a-z0-9_.-]*")) throw new IllegalArgumentException("Invalid substance ID");
    }
    @Override public int compareTo(SubstanceId other) { return value.compareTo(other.value); }
}

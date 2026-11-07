package miningsim.resource;

import java.util.Objects;

public record MaterialSample(SubstanceId substance, MaterialForm form) implements Comparable<MaterialSample> {
    public MaterialSample { Objects.requireNonNull(substance); Objects.requireNonNull(form); }
    @Override public int compareTo(MaterialSample o) {
        int c = substance.compareTo(o.substance); return c != 0 ? c : form.name().compareTo(o.form.name());
    }
}

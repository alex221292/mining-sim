package miningsim.resource;

import java.util.Objects;
import java.util.Optional;
import miningsim.structural.RockProperties;

/** Density is integral kg/m³; structural parameters are optional and separately calibrated. */
public record SubstanceProperties(SubstanceId id, String displayName, long densityKgPerM3,
                                  Classification classification, Optional<RockProperties> structural) {
    public enum Classification { ROCK, EARTH, WOOD, METAL, OTHER }
    public SubstanceProperties {
        Objects.requireNonNull(id); Objects.requireNonNull(displayName); Objects.requireNonNull(classification); Objects.requireNonNull(structural);
        if (displayName.isBlank() || densityKgPerM3 <= 0) throw new IllegalArgumentException("Invalid substance properties");
    }
}

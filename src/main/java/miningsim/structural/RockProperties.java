package miningsim.structural;

/** Immutable reference properties in SI units; not calibrated geological data. */
public record RockProperties(double densityKgPerM3, double youngModulusPa,
                             double tensileStrengthPa, double compressiveStrengthPa,
                             double shearStrengthPa, double bendingStrengthPa,
                             double damageRatePerSecond) {
    public RockProperties {
        positive(densityKgPerM3, "densityKgPerM3");
        positive(youngModulusPa, "youngModulusPa");
        positive(tensileStrengthPa, "tensileStrengthPa");
        positive(compressiveStrengthPa, "compressiveStrengthPa");
        positive(shearStrengthPa, "shearStrengthPa");
        positive(bendingStrengthPa, "bendingStrengthPa");
        if (!Double.isFinite(damageRatePerSecond) || damageRatePerSecond < 0)
            throw new IllegalArgumentException("damageRatePerSecond must be finite and nonnegative");
    }
    private static void positive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be finite and positive");
    }

    /** Conservative interface strengths and stiffness; faster of the two damage rates. */
    public static RockProperties weakerInterface(RockProperties a, RockProperties b) {
        java.util.Objects.requireNonNull(a); java.util.Objects.requireNonNull(b);
        return new RockProperties(Math.min(a.densityKgPerM3, b.densityKgPerM3),
                Math.min(a.youngModulusPa, b.youngModulusPa),
                Math.min(a.tensileStrengthPa, b.tensileStrengthPa),
                Math.min(a.compressiveStrengthPa, b.compressiveStrengthPa),
                Math.min(a.shearStrengthPa, b.shearStrengthPa),
                Math.min(a.bendingStrengthPa, b.bendingStrengthPa),
                Math.max(a.damageRatePerSecond, b.damageRatePerSecond));
    }
}

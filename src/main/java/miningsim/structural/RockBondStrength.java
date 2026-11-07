package miningsim.structural;

import java.util.Objects;

/** Pure strength calculations; does not own topology or advance simulation time. */
public final class RockBondStrength {
    public static final double TICK_SECONDS = 1.0 / 60.0;
    private RockBondStrength() { }

    /**
     * Uses the element's local end actions [N1,V1,M1,N2,V2,M2].
     * Positive N2 and negative N1 are tensile; shear and moment use peak magnitude.
     * Section area is m² and section modulus is m³.
     */
    public static double utilization(RockProperties properties, double area, double sectionModulus, double[] endForces) {
        Objects.requireNonNull(properties); Objects.requireNonNull(endForces);
        if (!Double.isFinite(area) || area <= 0 || !Double.isFinite(sectionModulus) || sectionModulus <= 0 || endForces.length != 6)
            throw new IllegalArgumentException("Invalid section or end forces");
        for (double force : endForces) if (!Double.isFinite(force)) throw new IllegalArgumentException("Non-finite force");
        double tension = Math.max(0, Math.max(-endForces[0], endForces[3]));
        double compression = Math.max(0, Math.max(endForces[0], -endForces[3]));
        double axial = Math.max((tension / area) / properties.tensileStrengthPa(),
                (compression / area) / properties.compressiveStrengthPa());
        double shear = (Math.max(Math.abs(endForces[1]), Math.abs(endForces[4])) / area) / properties.shearStrengthPa();
        double bending = (Math.max(Math.abs(endForces[2]), Math.abs(endForces[5])) / sectionModulus) / properties.bendingStrengthPa();
        double result = axial + shear + bending;
        if (!Double.isFinite(result)) throw new IllegalArgumentException("Non-finite utilization");
        return result;
    }

    /** Monotone one-tick law; caller must enforce tick identity and revision validity. */
    public static double damageAfterTick(double damage, double utilization, double ratePerSecond) {
        if (!Double.isFinite(damage) || damage < 0 || damage > 1 || !Double.isFinite(utilization) || utilization < 0
                || !Double.isFinite(ratePerSecond) || ratePerSecond < 0)
            throw new IllegalArgumentException("Invalid damage inputs");
        return Math.min(1, damage + ratePerSecond * TICK_SECONDS * Math.max(0, utilization - 1));
    }
}

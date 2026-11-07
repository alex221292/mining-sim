package miningsim.structural;

/** Linear, small-displacement Euler–Bernoulli frame element in local coordinates.
 * DOFs are [axial, transverse, rotation] at each end; moments use the same
 * positive rotation convention at both ends. This is a numerical primitive,
 * not a complete structural solver or a material failure model.
 */
public final class PlanarFrameElement {
    private PlanarFrameElement() { }

    /** Computes local resisting end forces K*u; inputs and output must not alias. */
    public static void endForces(double length, double axialRigidity, double bendingRigidity,
                                 double[] displacement, double[] force) {
        if (!Double.isFinite(length) || length <= 0 || !Double.isFinite(axialRigidity) || axialRigidity <= 0
                || !Double.isFinite(bendingRigidity) || bendingRigidity <= 0)
            throw new IllegalArgumentException("Positive finite length and rigidities required");
        if (displacement.length != 6 || force.length != 6 || displacement == force)
            throw new IllegalArgumentException("Distinct six-element arrays required");
        for (double value : displacement) if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite displacement");
        double a = axialRigidity / length;
        double b = 12 * bendingRigidity / (length * length * length);
        double c = 6 * bendingRigidity / (length * length);
        double d = 4 * bendingRigidity / length;
        double e = 2 * bendingRigidity / length;
        double u1 = displacement[0], v1 = displacement[1], t1 = displacement[2];
        double u2 = displacement[3], v2 = displacement[4], t2 = displacement[5];
        double[] result = {
            a * (u1 - u2), b * (v1 - v2) + c * (t1 + t2), c * (v1 - v2) + d * t1 + e * t2,
            a * (u2 - u1), b * (v2 - v1) - c * (t1 + t2), c * (v1 - v2) + e * t1 + d * t2
        };
        for (double value : result) if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite element force");
        System.arraycopy(result, 0, force, 0, 6);
    }
}

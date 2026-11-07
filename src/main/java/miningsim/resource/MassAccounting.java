package miningsim.resource;

/** Integer milligrams; a solid cell is 0.001 m³. No bulk porosity is implied. */
public final class MassAccounting {
    private MassAccounting() { }
    public static long cellMilligrams(SubstanceProperties p) { return Math.multiplyExact(p.densityKgPerM3(), 1000L); }
    public static long solidCellsMilligrams(SubstanceProperties p, long cells) {
        if (cells < 0) throw new IllegalArgumentException("Negative cells");
        return Math.multiplyExact(cellMilligrams(p), cells);
    }
    public static long add(long a, long b) {
        if (a < 0 || b < 0) throw new IllegalArgumentException("Negative mass"); return Math.addExact(a,b);
    }
}

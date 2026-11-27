package miningsim.resource;

/** Stable wire IDs, independent of material and rock substance IDs. */
public enum OreId {
    IRON(1), COPPER(2), SILVER(3), GOLD(4);
    private final int id;
    OreId(int id) { this.id=id; }
    public int id() { return id; }
    public static OreId fromId(int id) {
        for(var ore:values()) if(ore.id==id) return ore;
        throw new IllegalArgumentException("Unknown ore ID: "+id);
    }
}

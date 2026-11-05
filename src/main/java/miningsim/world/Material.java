package miningsim.world;

public enum Material {
    AIR, STONE, SAND, WATER, WOOD;

    private static final Material[] VALUES = values();

    public static Material fromId(byte id) { return VALUES[id]; }
    public byte id() { return (byte) ordinal(); }
}

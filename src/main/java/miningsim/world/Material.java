package miningsim.world;

public enum Material {
    AIR(0, Category.EMPTY), STONE(1, Category.STATIC), SAND(2, Category.GRANULAR),
    WATER(3, Category.LIQUID), WOOD(4, Category.STATIC),
    GRAVEL(5, Category.GRANULAR), SOIL(6, Category.GRANULAR), BEDROCK(7, Category.STATIC);

    public enum Category { EMPTY, STATIC, GRANULAR, LIQUID }
    private static final Material[] BY_ID = new Material[256];
    static {
        for (Material material : values()) {
            int id = Byte.toUnsignedInt(material.id);
            if (BY_ID[id] != null) throw new IllegalStateException("Duplicate material ID " + id);
            BY_ID[id] = material;
        }
    }
    private final byte id;
    private final Category category;

    Material(int id, Category category) { this.id = (byte) id; this.category = category; }
    public static Material fromId(byte id) {
        Material material = BY_ID[Byte.toUnsignedInt(id)];
        if (material == null) throw new IllegalArgumentException("Unknown material ID " + Byte.toUnsignedInt(id));
        return material;
    }
    public byte id() { return id; }
    public Category category() { return category; }
    public boolean canDisplace(Material target) {
        return category == Category.GRANULAR && target.category == Category.LIQUID;
    }
}

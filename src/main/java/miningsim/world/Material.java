package miningsim.world;

public enum Material {
    AIR(0, Category.EMPTY), STONE(1, Category.STATIC), SAND(2, Category.GRANULAR),
    WATER(3, Category.LIQUID), WOOD(4, Category.STATIC),
    GRAVEL(5, Category.GRANULAR), SOIL(6, Category.GRANULAR), BEDROCK(7, Category.STATIC),
    GRANITE(8, Category.STATIC), LIMESTONE(9, Category.STATIC), MARBLE(10, Category.STATIC),
    STONE_GRIT(11, Category.GRANULAR), GRANITE_GRIT(12, Category.GRANULAR),
    LIMESTONE_GRIT(13, Category.GRANULAR), MARBLE_GRIT(14, Category.GRANULAR);

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
    public boolean isFixedRock() { return this == STONE || this == GRANITE || this == LIMESTONE || this == MARBLE; }
    public boolean isGrit() { return this == STONE_GRIT || this == GRANITE_GRIT || this == LIMESTONE_GRIT || this == MARBLE_GRIT; }
    public miningsim.resource.MaterialForm rockForm() {
        if (isGrit()) return miningsim.resource.MaterialForm.GRIT;
        if (isFixedRock()) return miningsim.resource.MaterialForm.SOLID_MASS;
        throw new IllegalArgumentException("Not rock: " + this);
    }
    /** Game support range in world cells, not a physical material property. */
    public int rockSupportRangeCells() {
        return switch (this) {
            case GRANITE -> 48;
            case MARBLE -> 40;
            case STONE -> 32;
            case LIMESTONE -> 24;
            default -> throw new IllegalArgumentException("Not fixed rock: " + this);
        };
    }
    public miningsim.resource.SubstanceId rockSubstance() {
        return switch (this) {
            case STONE, STONE_GRIT -> miningsim.resource.SubstanceRegistry.STONE;
            case GRANITE, GRANITE_GRIT -> miningsim.resource.SubstanceRegistry.GRANITE;
            case LIMESTONE, LIMESTONE_GRIT -> miningsim.resource.SubstanceRegistry.LIMESTONE;
            case MARBLE, MARBLE_GRIT -> miningsim.resource.SubstanceRegistry.MARBLE;
            default -> throw new IllegalArgumentException("Not fixed rock: " + this);
        };
    }
    public boolean canDisplace(Material target) {
        return category == Category.GRANULAR && target.category == Category.LIQUID;
    }
}

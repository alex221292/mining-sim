package miningsim.structural;

import java.util.Arrays;
import java.util.Objects;
import miningsim.world.Material;

/** Immutable ID-to-properties lookup. Registration does not add world material IDs. */
public final class RockMaterialRegistry {
    private final RockProperties[] profiles;

    private RockMaterialRegistry(RockProperties[] profiles) { this.profiles = profiles; }

    public static RockMaterialRegistry stoneReference() {
        return new Builder().register(Byte.toUnsignedInt(Material.STONE.id()),
                new RockProperties(2500, 1e8, 1e5, 1e6, 1e5, 1e5, 0.1)).build();
    }
    public RockProperties get(int unsignedMaterialId) {
        if (unsignedMaterialId < 0 || unsignedMaterialId >= profiles.length)
            throw new IllegalArgumentException("Material ID must be 0–255");
        return profiles[unsignedMaterialId];
    }
    public boolean isRock(int unsignedMaterialId) { return get(unsignedMaterialId) != null; }

    public static final class Builder {
        private final RockProperties[] profiles = new RockProperties[256];
        public Builder register(int unsignedMaterialId, RockProperties properties) {
            if (unsignedMaterialId < 0 || unsignedMaterialId >= profiles.length)
                throw new IllegalArgumentException("Material ID must be 0–255");
            Objects.requireNonNull(properties);
            if (profiles[unsignedMaterialId] != null) throw new IllegalArgumentException("Duplicate material ID");
            profiles[unsignedMaterialId] = properties;
            return this;
        }
        public RockMaterialRegistry build() { return new RockMaterialRegistry(Arrays.copyOf(profiles, profiles.length)); }
    }
}

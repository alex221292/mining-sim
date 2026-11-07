package miningsim.resource;

import java.util.Objects;

public record ItemStack(ItemType type, MaterialSample sample, String metadata, long quantity) {
    public ItemStack {
        Objects.requireNonNull(type); Objects.requireNonNull(sample); Objects.requireNonNull(metadata);
        if (quantity < 0) throw new IllegalArgumentException("Negative quantity");
        Math.multiplyExact(type.milligramsPerUnit(),quantity);
    }
    public long massMilligrams() { return Math.multiplyExact(type.milligramsPerUnit(),quantity); }
    public boolean compatible(ItemStack other) { return type.equals(other.type) && sample.equals(other.sample) && metadata.equals(other.metadata); }
    public ItemStack withQuantity(long quantity) { return new ItemStack(type,sample,metadata,quantity); }
}

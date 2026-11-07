package miningsim.resource;

import java.util.Objects;

/** COUNT units weigh milligramsPerUnit; MASS units are integer milligrams. */
public record ItemType(String id, QuantityKind quantityKind, long milligramsPerUnit, boolean pickupAllowed) {
    public enum QuantityKind { COUNT, MASS }
    public ItemType {
        Objects.requireNonNull(id); Objects.requireNonNull(quantityKind);
        if (!id.matches("[a-z][a-z0-9_.-]*") || milligramsPerUnit <= 0 || (quantityKind == QuantityKind.MASS && milligramsPerUnit != 1))
            throw new IllegalArgumentException("Invalid item type");
    }
}

package miningsim.resource;

import java.util.Map;
import java.util.Objects;

public record InventoryRules(int slots, long maxMassMilligrams, Map<String, Long> stackLimits) {
    public InventoryRules {
        if (slots < 0 || maxMassMilligrams < 0) throw new IllegalArgumentException("Negative capacity");
        stackLimits = Map.copyOf(Objects.requireNonNull(stackLimits));
        for (var entry : stackLimits.entrySet()) if (entry.getValue() <= 0) throw new IllegalArgumentException("Invalid stack limit");
    }
    public long limit(ItemType type) {
        Long n=stackLimits.get(type.id()); if (n == null) throw new IllegalArgumentException("Unknown item type: " + type.id()); return n;
    }
}

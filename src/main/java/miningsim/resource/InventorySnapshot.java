package miningsim.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InventorySnapshot(List<Optional<ItemStack>> slots) {
    public InventorySnapshot {
        slots=List.copyOf(Objects.requireNonNull(slots));
        for (var slot : slots) if (slot.isPresent() && slot.get().quantity() == 0) throw new IllegalArgumentException("Empty stack in slot");
    }
    public static InventorySnapshot empty(int size) {
        if (size < 0) throw new IllegalArgumentException("Negative slots");
        List<Optional<ItemStack>> slots = new ArrayList<>(); for(int i=0;i<size;i++) slots.add(Optional.empty()); return new InventorySnapshot(slots);
    }
    public long massMilligrams() { long mass=0; for(var slot:slots) if(slot.isPresent()) mass=MassAccounting.add(mass,slot.get().massMilligrams()); return mass; }
}

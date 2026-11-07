package miningsim.resource;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

/** Pure deterministic insertion: compatible slots first, then empty slots, ascending indices. */
public final class InventoryInsertion {
    public record Result(InventorySnapshot inventory, ItemStack remainder) { }
    private InventoryInsertion() { }
    public static Result insert(InventoryRules rules, InventorySnapshot inventory, ItemStack input) {
        Objects.requireNonNull(rules); Objects.requireNonNull(inventory); Objects.requireNonNull(input);
        if (inventory.slots().size()!=rules.slots()) throw new IllegalArgumentException("Wrong slot count");
        long mass=inventory.massMilligrams(); if(mass>rules.maxMassMilligrams()) throw new IllegalArgumentException("Inventory overweight");
        for (var slot:inventory.slots()) if(slot.isPresent() && slot.get().quantity()>rules.limit(slot.get().type())) throw new IllegalArgumentException("Overfull stack");
        long limit=rules.limit(input.type());
        if (!input.type().pickupAllowed() || input.sample().form()==MaterialForm.BOULDER) return new Result(inventory,input);
        long remaining=input.quantity(), available=(rules.maxMassMilligrams()-mass)/input.type().milligramsPerUnit();
        var out=new ArrayList<>(inventory.slots());
        for(int pass=0;pass<2;pass++) for(int i=0;i<out.size() && remaining>0 && available>0;i++) {
            var slot=out.get(i);
            if(pass==0 && (slot.isEmpty() || !slot.get().compatible(input))) continue;
            if(pass==1 && slot.isPresent()) continue;
            long old=slot.isPresent()?slot.get().quantity():0;
            long added=Math.min(Math.min(limit-old,remaining),available);
            if(added>0) { out.set(i,Optional.of(input.withQuantity(Math.addExact(old,added)))); remaining-=added; available-=added; }
        }
        return new Result(new InventorySnapshot(out),input.withQuantity(remaining));
    }
}

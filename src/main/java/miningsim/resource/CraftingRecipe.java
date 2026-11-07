package miningsim.resource;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Declarative one-batch recipe with explicit composite output composition in milligrams. */
public record CraftingRecipe(List<ItemStack> ingredients, ItemStack output, Map<SubstanceId,Long> outputCompositionMilligrams) {
    public CraftingRecipe {
        ingredients = List.copyOf(Objects.requireNonNull(ingredients)); Objects.requireNonNull(output);
        outputCompositionMilligrams = Map.copyOf(Objects.requireNonNull(outputCompositionMilligrams));
        if (ingredients.isEmpty() || output.quantity() <= 0 || output.sample().form() != MaterialForm.CRAFTED_BLOCK)
            throw new IllegalArgumentException("Recipe requires ingredients and crafted output");
        Map<SubstanceId,Long> inputMass = new TreeMap<>();
        for (ItemStack input : ingredients) {
            if(input.quantity() <= 0) throw new IllegalArgumentException("Empty ingredient");
            inputMass.merge(input.sample().substance(),input.massMilligrams(),MassAccounting::add);
        }
        long total=0;
        for (var e:outputCompositionMilligrams.entrySet()) {
            if(e.getValue() <= 0) throw new IllegalArgumentException("Invalid constituent mass");
            total=MassAccounting.add(total,e.getValue());
        }
        if(!inputMass.equals(outputCompositionMilligrams) || total != output.massMilligrams())
            throw new IllegalArgumentException("Recipe must conserve each substance and total mass");
    }
}

package com.aquariverr.fluxod.miniature.recipe;

import com.aquariverr.fluxod.miniature.model.FactoryRules;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

public record ResolvedRecipe(RecipeReference reference, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                             List<ResourceAmount> catalysts) {
    public ResolvedRecipe(RecipeReference reference, List<ResourceAmount> inputs, List<ResourceAmount> outputs) {
        this(reference, inputs, outputs, List.of());
    }

    public ResolvedRecipe {
        Objects.requireNonNull(reference, "reference");
        inputs = FactoryRules.scaleForMachines(inputs, BigInteger.ONE, 1);
        outputs = FactoryRules.scaleForMachines(outputs, BigInteger.ONE, 1);
        catalysts = FactoryRules.scaleForMachines(catalysts, BigInteger.ONE, 1);
        subtract(inputs, catalysts);
        if (inputs.isEmpty()) throw new IllegalArgumentException("Recipes require consumed inputs");
        FactoryRules.normalizationFactor(subtract(outputs, catalysts));
    }

    public BigInteger normalization() { return FactoryRules.normalizationFactor(subtract(outputs, catalysts)); }
    public List<ResourceAmount> inputsForMachines(int count) { return FactoryRules.scaleForMachines(inputs, normalization(), count); }
    public List<ResourceAmount> outputsForMachines(int count) { return FactoryRules.scaleForMachines(outputs, normalization(), count); }
    public List<ResourceAmount> inputsForOperations(BigInteger count) { return scale(inputs, count); }
    public List<ResourceAmount> outputsForOperations(BigInteger count) { return scale(outputs, count); }
    public List<ResourceAmount> catalystsForOperations(BigInteger count) { return scale(catalysts, count); }

    private static List<ResourceAmount> scale(List<ResourceAmount> amounts, BigInteger count) {
        com.aquariverr.fluxod.miniature.model.FactoryLimits.requirePositiveQuantity(count);
        return amounts.stream().map(amount -> new ResourceAmount(amount.resource(), amount.amount().multiply(count))).toList();
    }

    private static List<ResourceAmount> subtract(List<ResourceAmount> resources, List<ResourceAmount> catalysts) {
        var amounts = new java.util.LinkedHashMap<com.aquariverr.fluxod.miniature.model.ResourceKey, BigInteger>();
        for (var resource : resources) amounts.put(resource.resource(), resource.amount());
        for (var catalyst : catalysts) {
            BigInteger remaining = amounts.getOrDefault(catalyst.resource(), BigInteger.ZERO).subtract(catalyst.amount());
            if (remaining.signum() < 0) throw new IllegalArgumentException("Catalysts must occur unchanged in both input and output");
            if (remaining.signum() == 0) amounts.remove(catalyst.resource());
            else amounts.put(catalyst.resource(), remaining);
        }
        return amounts.entrySet().stream().map(entry -> new ResourceAmount(entry.getKey(), entry.getValue())).toList();
    }
}

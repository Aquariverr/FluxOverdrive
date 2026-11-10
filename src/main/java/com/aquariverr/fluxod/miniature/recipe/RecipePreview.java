package com.aquariverr.fluxod.miniature.recipe;

import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

public record RecipePreview(Status status, int candidateCount, int selectedIndex, RecipeReference reference,
                            BigInteger normalization, List<ResourceAmount> inputs, List<ResourceAmount> outputs,
                            List<ResourceKey> missing) {
    public RecipePreview {
        Objects.requireNonNull(status);
        FactoryLimits.requirePositiveQuantity(normalization);
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
        missing = List.copyOf(missing);
        if (candidateCount < 0 || candidateCount > MiniatureRecipeCatalog.MAX_CANDIDATES
                || selectedIndex < -1 || selectedIndex >= candidateCount
                || inputs.size() > FactoryLimits.MAX_RECIPE_RESOURCES || outputs.size() > FactoryLimits.MAX_RECIPE_RESOURCES
                || missing.size() > 9) throw new IllegalArgumentException("Invalid recipe preview bounds");
        if (status == Status.READY || status == Status.AUTOMATIC || status == Status.COMPILED) {
            if (reference == null || inputs.isEmpty() || outputs.isEmpty() || selectedIndex < 0 || !missing.isEmpty()) {
                throw new IllegalArgumentException("Incomplete resolved recipe preview");
            }
        } else if (!inputs.isEmpty() || !outputs.isEmpty() || selectedIndex != -1 || !BigInteger.ONE.equals(normalization)) {
            throw new IllegalArgumentException("Failed recipe preview exposes resolved resources");
        }
    }

    public enum Status { AUTOMATIC, READY, MISSING_INPUTS, INVALID_SELECTION, UNSUPPORTED, SEARCH_LIMIT, COMPILED }
}

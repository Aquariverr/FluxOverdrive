package com.aquariverr.fluxod.miniature.recipe;

import com.aquariverr.fluxod.miniature.model.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/** Optional recipe adapter hook for recipes whose inputs are not all item ingredients. */
public interface ResourceRecipeAdapter extends MachineRecipeAdapter {
    List<ResolvedRecipe> candidates(String recipeId, Recipe<?> recipe, List<ResourceKey> available, ServerLevel level);

    @Override
    default Pattern pattern(Recipe<?> recipe) {
        throw new UnsupportedOperationException("Resource recipes do not have an item grid pattern");
    }

    @Override
    default ResolvedRecipe resolve(com.aquariverr.fluxod.miniature.model.RecipeReference reference,
                                   Recipe<?> recipe, ServerLevel level) {
        return candidates(reference.recipeId(), recipe, reference.bindings().stream().map(binding -> binding.resource()).toList(), level)
                .stream().filter(candidate -> candidate.reference().equals(reference)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Resource recipe selection is invalid"));
    }
}

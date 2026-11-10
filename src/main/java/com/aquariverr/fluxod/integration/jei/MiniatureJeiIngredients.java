package com.aquariverr.fluxod.integration.jei;

import com.aquariverr.fluxod.integration.mekanism.MekanismJeiIngredients;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

/** Converts a displayed ingredient's identity only. Its amount never becomes a transfer quantity. */
final class MiniatureJeiIngredients {
    static ResourceKey resourceOf(Object ingredient, HolderLookup.Provider registries) {
        if (ingredient instanceof ItemStack item && !item.isEmpty()) return FactoryItemResources.keyOf(item.copy(), registries);
        if (ingredient instanceof FluidStack fluid && !fluid.isEmpty()) return FactoryFluidResources.keyOf(fluid.copy(), registries);
        return ModList.get().isLoaded("mekanism") ? MekanismJeiIngredients.resourceOf(ingredient) : null;
    }

    private MiniatureJeiIngredients() {}
}

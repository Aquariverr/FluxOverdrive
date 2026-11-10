package com.aquariverr.fluxod.miniature.recipe;

import net.neoforged.neoforge.fluids.crafting.CompoundFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SingleFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;

/** Rejects arbitrary addon predicates while allowing deterministic native fluid ingredients. */
public final class FluidIngredientSupport {

    public static boolean isSupported(FluidIngredient ingredient) {
        return supportedFluid(ingredient, 0);
    }

    private static boolean supportedFluid(FluidIngredient ingredient, int depth) {
        if (depth > 16) return false;
        if (ingredient.getClass() == SingleFluidIngredient.class || ingredient.getClass() == TagFluidIngredient.class
                || ingredient.getClass() == DataComponentFluidIngredient.class) return true;
        return ingredient.getClass() == CompoundFluidIngredient.class
                && ((CompoundFluidIngredient) ingredient).children().stream().allMatch(child -> supportedFluid(child, depth + 1));
    }

    private FluidIngredientSupport() {}
}

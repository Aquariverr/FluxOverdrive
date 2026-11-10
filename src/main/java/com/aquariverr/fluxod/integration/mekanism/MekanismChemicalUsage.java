package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;

import java.math.BigInteger;

/** Deterministic consumption with a virtual eight-chemical-upgrade baseline, before k, c and P scaling. */
final class MekanismChemicalUsage {
    // Mekanism 10.7: dissolution uses 5 seconds; advanced electric, infusing and painting use 10.
    static final int DISSOLUTION_TICKS = 100;
    static final int ITEM_CHEMICAL_TICKS = 200;
    // Eight chemical upgrades and zero speed upgrades give 10^-1 usage with Mekanism's
    // default maxUpgradeMultiplier=10. Only dissolution, purification and injection support them.
    static final int FULL_CHEMICAL_UPGRADE_DIVISOR = 10;

    static ResolvedRecipe forBatch(ResolvedRecipe recipe, int usageTicks) {
        if (usageTicks <= 0) throw new IllegalArgumentException("Chemical usage duration must be positive");
        if (usageTicks == 1) return recipe;
        BigInteger multiplier = BigInteger.valueOf(usageTicks);
        var inputs = recipe.inputs().stream().map(input -> input.resource().kind() == ResourceKind.CHEMICAL
                ? new ResourceAmount(input.resource(), input.amount().multiply(multiplier)) : input).toList();
        return new ResolvedRecipe(recipe.reference(), inputs, recipe.outputs(), recipe.catalysts());
    }

    private MekanismChemicalUsage() {}
}

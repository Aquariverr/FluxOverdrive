package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.Chemical;

/** JEI/EMI chemical identities, loaded only when Mekanism is present. */
public final class MekanismJeiIngredients {
    public static ResourceKey resourceOf(Object ingredient) {
        if (ingredient instanceof Chemical chemical) {
            var id = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getKey(chemical);
            return id == null || id.equals(mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.getDefaultKey())
                    ? null : new ResourceKey(ResourceKind.CHEMICAL, id.toString());
        }
        return ingredient instanceof ChemicalStack chemical && !chemical.isEmpty()
                ? new ResourceKey(ResourceKind.CHEMICAL, chemical.getChemicalHolder().getRegisteredName()) : null;
    }

    private MekanismJeiIngredients() {}
}

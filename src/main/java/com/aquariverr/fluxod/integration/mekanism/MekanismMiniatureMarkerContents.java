package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryEditing.MarkerSource;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;

import java.util.ArrayList;
import java.util.List;

/** Loaded only behind the Mekanism mod guard. No extraction or container replacement occurs here. */
public final class MekanismMiniatureMarkerContents {
    private static final ItemCapability<IChemicalHandler, Void> CAPABILITY = ItemCapability.createVoid(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    public static ResourceKey keyOf(ItemStack copy, int tank) {
        IChemicalHandler handler = CAPABILITY.getCapability(copy, null);
        if (handler == null || tank < 0 || tank >= MiniatureFactoryCommandPayload.MAX_MARKER_TANKS
                || tank >= handler.getChemicalTanks()) return null;
        var chemical = handler.getChemicalInTank(tank);
        return chemical.isEmpty() ? null : new ResourceKey(ResourceKind.CHEMICAL,
                chemical.getChemicalHolder().getRegisteredName());
    }

    public static List<MarkerSource> sources(ItemStack copy) {
        IChemicalHandler handler = CAPABILITY.getCapability(copy, null);
        if (handler == null) return List.of();
        List<MarkerSource> sources = new ArrayList<>();
        for (int tank = 0; tank < Math.min(handler.getChemicalTanks(), MiniatureFactoryCommandPayload.MAX_MARKER_TANKS); tank++) {
            var chemical = handler.getChemicalInTank(tank);
            if (!chemical.isEmpty()) sources.add(new MarkerSource(
                    MiniatureFactoryCommandPayload.Action.SET_CHEMICAL_MARKER_FROM_CARRIED, tank,
                    new ResourceKey(ResourceKind.CHEMICAL, chemical.getChemicalHolder().getRegisteredName())));
        }
        return List.copyOf(sources);
    }

    private MekanismMiniatureMarkerContents() {}
}

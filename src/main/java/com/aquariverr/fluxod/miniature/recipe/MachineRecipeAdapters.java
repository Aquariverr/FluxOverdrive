package com.aquariverr.fluxod.miniature.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class MachineRecipeAdapters {
    private static final List<MachineRecipeAdapter> ADAPTERS = new ArrayList<>(List.of(
            new VanillaRecipeAdapter("flux_overdrive:smelting", Items.FURNACE, List.of(SmeltingRecipe.class)),
            new VanillaRecipeAdapter("flux_overdrive:blasting", Items.BLAST_FURNACE, List.of(BlastingRecipe.class)),
            new VanillaRecipeAdapter("flux_overdrive:smoking", Items.SMOKER, List.of(SmokingRecipe.class)),
            new VanillaRecipeAdapter("flux_overdrive:crafting", Items.CRAFTING_TABLE, List.of(ShapedRecipe.class, ShapelessRecipe.class)),
            new VanillaRecipeAdapter("flux_overdrive:stonecutting", Items.STONECUTTER, List.of(StonecutterRecipe.class))));
    private static boolean frozen;
    private static boolean optionalBootstrapped;

    public static synchronized void register(MachineRecipeAdapter adapter) {
        if (frozen || ADAPTERS.stream().anyMatch(existing -> existing.id().equals(adapter.id()))) {
            throw new IllegalStateException("Recipe adapters must have unique IDs and register before first use");
        }
        ADAPTERS.add(adapter);
    }

    public static synchronized List<MachineRecipeAdapter> forMachine(ItemStack machine) {
        if (!optionalBootstrapped) {
            optionalBootstrapped = true;
            if (ModList.get().isLoaded("mekanism")) {
                com.aquariverr.fluxod.integration.mekanism.MekanismRecipeIntegration.register();
            }
        }
        frozen = true;
        List<MachineRecipeAdapter> result = new ArrayList<>(ADAPTERS.stream().filter(adapter -> adapter.supportsMachine(machine)
                        || machine.is(TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(
                        ResourceLocation.parse(adapter.id()).getNamespace(), "miniature_machines/" + ResourceLocation.parse(adapter.id()).getPath()))))
                .toList());
        // GTCEu 8 changed recipe content and energy APIs. Keep 7.x's existing energy integration usable
        // without linking the newer recipe adapter against its incompatible classes.
        if (ModList.get().getModContainerById("gtceu")
                .map(mod -> mod.getModInfo().getVersion().getMajorVersion() >= 8).orElse(false)) {
            result.addAll(com.aquariverr.fluxod.integration.gtceu.GtceuMiniatureRecipeAdapter.forMachine(machine));
        }
        return result.stream()
                .sorted(Comparator.comparing(MachineRecipeAdapter::id)).toList();
    }

    private MachineRecipeAdapters() {}
}

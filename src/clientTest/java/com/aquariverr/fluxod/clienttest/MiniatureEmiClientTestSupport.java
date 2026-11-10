package com.aquariverr.fluxod.clienttest;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.registry.EmiRecipes;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.EmiScreenManager;
import dev.emi.emi.search.EmiSearch;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.List;
import java.util.Map;
import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.integration.emi.MiniatureEmiDragDropHandler;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.registry.EmiDragDropHandlers;
import dev.emi.emi.registry.EmiStackList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** Loaded only when the optional EMI mod is present in the client-test JVM. */
final class MiniatureEmiClientTestSupport {
    static void configure() {
        EmiConfig.devMode = false;
        if (!EmiConfig.enabled) throw new IllegalStateException("Installed EMI must remain enabled for integration verification");
    }

    static boolean indexesReady() {
        Thread search = EmiSearch.searchThread;
        return EmiReloadManager.isLoaded() && EmiRecipes.activeWorker == null && (search == null || !search.isAlive());
    }

    static void drop(MiniatureUniverseScreen screen, Object ingredient, double x, double y) {
        if (EmiDragDropHandlers.fromClass.getOrDefault(MiniatureUniverseScreen.class, List.of()).stream()
                .noneMatch(MiniatureEmiDragDropHandler.class::isInstance)) throw new IllegalStateException("Native EMI handler was not registered");
        EmiStack stack;
        if (ingredient instanceof ItemStack item) stack = EmiStack.of(item);
        else if (ingredient instanceof FluidStack fluid) stack = EmiStack.of(fluid.getFluid(), fluid.getComponentsPatch(), fluid.getAmount());
        else stack = EmiStackList.stacks.stream().filter(value -> value.getId().equals(ResourceLocation.parse(ingredient.toString())))
                    .filter(value -> {
                        var resource = MiniatureEmiDragDropHandler.resourceOf(value);
                        return resource != null && resource.kind() == com.aquariverr.fluxod.miniature.model.ResourceKind.CHEMICAL;
                    })
                    .findFirst().orElseThrow(() -> new IllegalStateException("Native EMI chemical is missing: " + ingredient));
        if (MiniatureEmiDragDropHandler.resourceOf(stack) == null) throw new IllegalStateException("Unsupported native EMI stack");
        if (EmiDragDropHandlers.dropStack(screen, stack, screen.getGuiLeft() - 8, screen.getGuiTop() - 8)) {
            throw new IllegalStateException("EMI accepted a drop outside the viewport");
        }
        if (!EmiDragDropHandlers.dropStack(screen, stack, (int) x, (int) y)) throw new IllegalStateException("EMI drop was rejected");
    }

    static List<AbstractWidget> widgets() {
        return List.of(EmiScreenManager.search, EmiScreenManager.emi, EmiScreenManager.tree);
    }

    static Map<String, Object> status() {
        return Map.of("emiInstalled", true, "emiReloadComplete", EmiReloadManager.isLoaded(),
                "emiRecipeBakeComplete", EmiRecipes.activeWorker == null, "emiDevMode", EmiConfig.devMode,
                "emiEnabled", EmiConfig.enabled);
    }

    private MiniatureEmiClientTestSupport() {}
}

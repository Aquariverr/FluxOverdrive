package com.aquariverr.fluxod.integration.emi;

import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.integration.mekanism.MekanismJeiIngredients;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.stack.EmiIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;

public final class MiniatureEmiDragDropHandler implements EmiDragDropHandler<MiniatureUniverseScreen> {
    public static ResourceKey resourceOf(EmiIngredient ingredient) {
        var level = Minecraft.getInstance().level;
        if (level == null || ingredient == null || ingredient.isEmpty() || ingredient.getEmiStacks().size() != 1) return null;
        var stack = ingredient.getEmiStacks().getFirst();
        try {
            // Amount is only display metadata. Preserve identity/components, never mint real resources.
            if (stack.getKey() instanceof Fluid fluid) {
                var value = new FluidStack(fluid, 1);
                value.applyComponents(stack.getComponentChanges());
                return FactoryFluidResources.keyOf(value, level.registryAccess());
            }
            var item = stack.getItemStack();
            if (!item.isEmpty()) return FactoryItemResources.keyOf(item, level.registryAccess());
            return ModList.get().isLoaded("mekanism")
                    ? MekanismJeiIngredients.resourceOf(stack.getKey()) : null;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return null;
        }
    }

    @Override
    public boolean dropStack(MiniatureUniverseScreen screen, EmiIngredient ingredient, int x, int y) {
        ResourceKey resource = resourceOf(ingredient);
        if (resource == null) return false;
        for (var target : screen.ghostMarkerTargets()) {
            if (target.area().contains(x, y)) return screen.placeGhostMarker(target, resource);
        }
        return false;
    }

    @Override
    public void render(MiniatureUniverseScreen screen, EmiIngredient ingredient, GuiGraphics graphics,
                       int mouseX, int mouseY, float delta) {
        if (resourceOf(ingredient) == null) return;
        for (var target : screen.ghostMarkerTargets()) {
            var area = target.area();
            graphics.fill(area.getX(), area.getY(), area.getX() + area.getWidth(), area.getY() + area.getHeight(),
                    area.contains(mouseX, mouseY) ? 0x804DD9C0 : 0x304DD9C0);
        }
    }
}

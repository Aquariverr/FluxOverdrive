package com.aquariverr.fluxod.integration.jei;

import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;

import java.util.ArrayList;
import java.util.List;

public final class MiniatureGhostIngredientHandler implements IGhostIngredientHandler<MiniatureUniverseScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(MiniatureUniverseScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        if (resourceOf(ingredient.getIngredient()) == null) return List.of();
        List<Target<I>> targets = new ArrayList<>();
        for (var target : screen.ghostMarkerTargets()) targets.add(new Target<>() {
            @Override
            public Rect2i getArea() {
                var area = target.area();
                return new Rect2i(area.getX(), area.getY(), area.getWidth(), area.getHeight());
            }

            @Override
            public void accept(I dropped) {
                ResourceKey resource = resourceOf(dropped);
                if (resource != null) screen.placeGhostMarker(target, resource);
            }
        });
        return List.copyOf(targets);
    }

    private static ResourceKey resourceOf(Object ingredient) {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        try { return MiniatureJeiIngredients.resourceOf(ingredient, level.registryAccess()); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return null; }
    }

    @Override
    public void onComplete() {}
}

package com.aquariverr.fluxod.integration.jei;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Discovered by JEI only; the normal editor has no dependency on JEI classes. */
@JeiPlugin
public final class MiniatureJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() { return FluxOverdrive.location("miniature_universe"); }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(MiniatureUniverseScreen.class, new MiniatureGhostIngredientHandler());
        registration.addGuiContainerHandler(MiniatureUniverseScreen.class, new IGuiContainerHandler<>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(MiniatureUniverseScreen screen) {
                // Include the outer frame as well as the editor's normal container rectangle.
                return List.of(new Rect2i(screen.getGuiLeft() - 4, screen.getGuiTop() - 4,
                        screen.getXSize() + 8, screen.getYSize() + 8));
            }
        });
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new MiniatureRecipeTransferHandler(registration.getTransferHelper()));
    }
}

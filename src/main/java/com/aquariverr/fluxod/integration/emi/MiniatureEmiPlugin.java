package com.aquariverr.fluxod.integration.emi;

import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.widget.Bounds;

/** Discovered by EMI independently of its optional JEI bridge. */
@EmiEntrypoint
public final class MiniatureEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(MiniatureUniverseScreen.class, new MiniatureEmiDragDropHandler());
        registry.addExclusionArea(MiniatureUniverseScreen.class, (screen, areas) -> areas.accept(
                new Bounds(screen.getGuiLeft() - 4, screen.getGuiTop() - 4,
                        screen.getXSize() + 8, screen.getYSize() + 8)));
    }
}

package com.aquariverr.fluxod.integration.emi;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = FluxOverdrive.MODID, value = Dist.CLIENT)
public final class MiniatureEmiLayout {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof MiniatureUniverseScreen screen && ModList.get().isLoaded("emi")) {
            Access.arrange(screen);
        }
    }

    @SubscribeEvent
    public static void beforeInit(ScreenEvent.Init.Pre event) {
        if (event.getScreen() instanceof MiniatureUniverseScreen screen && ModList.get().isLoaded("emi")) {
            Access.restore(screen);
        }
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof MiniatureUniverseScreen screen && ModList.get().isLoaded("emi")) {
            Access.restore(screen);
        }
    }

    // EMI's exclusion API reserves sidebar cells, but its search field ignores those exclusions.
    private static final class Access {
        private static MiniatureUniverseScreen owner;
        private static WidgetState search;
        private static WidgetState configuration;
        private static WidgetState recipeTree;

        private static void arrange(MiniatureUniverseScreen screen) {
            if (owner != screen) {
                owner = screen;
                search = WidgetState.capture(dev.emi.emi.screen.EmiScreenManager.search);
                configuration = WidgetState.capture(dev.emi.emi.screen.EmiScreenManager.emi);
                recipeTree = WidgetState.capture(dev.emi.emi.screen.EmiScreenManager.tree);
            }
            search.restore();
            configuration.restore();
            recipeTree.restore();
            if (search.visible() && overlaps(search.widget(), screen)) {
                int right = screen.getGuiLeft() + screen.getXSize() + 3;
                int width = Math.min(search.width(), screen.width - right - 3);
                if (width >= 40) {
                    search.widget().setX(right);
                    search.widget().setWidth(width);
                } else {
                    hide(search.widget());
                }
            }
            if (overlaps(configuration.widget(), screen)) hide(configuration.widget());
            if (overlaps(recipeTree.widget(), screen)) hide(recipeTree.widget());
        }

        private static boolean overlaps(AbstractWidget widget, MiniatureUniverseScreen screen) {
            return widget.getX() - 1 < screen.getGuiLeft() + screen.getXSize()
                    && widget.getRight() + 1 > screen.getGuiLeft()
                    && widget.getY() - 1 < screen.getGuiTop() + screen.getYSize()
                    && widget.getBottom() + 1 > screen.getGuiTop();
        }

        private static void hide(AbstractWidget widget) {
            widget.visible = false;
            widget.active = false;
            if (widget instanceof EditBox editBox) editBox.setFocused(false);
        }

        private static void restore(MiniatureUniverseScreen screen) {
            if (owner != screen) return;
            search.restore();
            configuration.restore();
            recipeTree.restore();
            owner = null;
            search = null;
            configuration = null;
            recipeTree = null;
        }
    }

    private record WidgetState(AbstractWidget widget, int x, int y, int width, int height,
                               boolean visible, boolean active) {
        private static WidgetState capture(AbstractWidget widget) {
            return new WidgetState(widget, widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight(),
                    widget.visible, widget.active);
        }

        private void restore() {
            widget.setX(x);
            widget.setY(y);
            widget.setWidth(width);
            widget.setHeight(height);
            widget.visible = visible;
            widget.active = active;
        }
    }

    private MiniatureEmiLayout() {}
}

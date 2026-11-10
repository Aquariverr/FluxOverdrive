package com.aquariverr.fluxod.client;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.client.gui.FluxButtonSound;
import com.aquariverr.fluxod.client.gui.FluxGuiStyle;
import com.aquariverr.fluxod.network.OpenQuantumBatteryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import sonar.fluxnetworks.common.connection.FluxMenu;

@EventBusSubscriber(modid = FluxOverdrive.MODID, value = Dist.CLIENT)
public final class QuantumBatteryFluxGuiSwitch {

    private static final int BUTTON_WIDTH = 118;
    private static final int BUTTON_HEIGHT = 20;

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        TileQuantumBatteryController controller = getController(event.getScreen(), minecraft);
        if (controller == null) return;

        int x = buttonX(event.getScreen());
        int y = buttonY(event.getScreen());
        boolean hovered = event.getMouseX() >= x && event.getMouseX() < x + BUTTON_WIDTH
                && event.getMouseY() >= y && event.getMouseY() < y + BUTTON_HEIGHT;
        GuiGraphics graphics = event.getGuiGraphics();
        FluxGuiStyle.renderButton(graphics, minecraft.font, x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable("screen.flux_overdrive.quantum_battery.return"),
                FluxGuiStyle.accent(controller.getNetworkColorRgb()), false, true, hovered,
                FluxGuiStyle.ButtonStyle.ACTION);
    }

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 0) return;
        Minecraft minecraft = Minecraft.getInstance();
        TileQuantumBatteryController controller = getController(event.getScreen(), minecraft);
        if (controller == null) return;

        int x = buttonX(event.getScreen());
        int y = buttonY(event.getScreen());
        if (event.getMouseX() < x || event.getMouseX() >= x + BUTTON_WIDTH
                || event.getMouseY() < y || event.getMouseY() >= y + BUTTON_HEIGHT) return;

        FluxButtonSound.play();
        PacketDistributor.sendToServer(new OpenQuantumBatteryPayload(controller.getBlockPos()));
        event.setCanceled(true);
    }

    private static int buttonX(net.minecraft.client.gui.screens.Screen screen) {
        return screen.width / 2 - BUTTON_WIDTH / 2;
    }

    private static int buttonY(net.minecraft.client.gui.screens.Screen screen) {
        return Math.min(screen.height - BUTTON_HEIGHT - 2, screen.height / 2 + 94);
    }

    private static TileQuantumBatteryController getController(net.minecraft.client.gui.screens.Screen screen,
                                                                Minecraft minecraft) {
        if (minecraft.player == null || !(minecraft.player.containerMenu instanceof FluxMenu menu)) return null;
        if (!(screen instanceof MenuAccess<?> menuAccess) || menuAccess.getMenu() != menu) return null;
        return menu.mProvider instanceof TileQuantumBatteryController controller ? controller : null;
    }

    private QuantumBatteryFluxGuiSwitch() {}
}

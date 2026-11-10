package com.aquariverr.fluxod.client.screen;

import com.aquariverr.fluxod.client.gui.FluxGuiStyle;
import com.aquariverr.fluxod.client.gui.FluxStyleButton;
import com.aquariverr.fluxod.client.gui.QuantumGuiLayout;
import com.aquariverr.fluxod.menu.QuantumCoolingPluginMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.util.Locale;

public final class QuantumCoolingPluginScreen extends AbstractContainerScreen<QuantumCoolingPluginMenu> {

    private static final int FLUID_COLOR = 0xFF3F8DDA;
    private static final int ADVANCED_TEXT_WIDTH = 145;

    private float uiScale = 1.0F;

    public QuantumCoolingPluginScreen(QuantumCoolingPluginMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = QuantumGuiLayout.REFERENCE_WIDTH;
        imageHeight = QuantumGuiLayout.REFERENCE_HEIGHT;
        titleLabelX = 14;
        titleLabelY = 11;
        inventoryLabelX = QuantumCoolingPluginMenu.SLOT_COLUMN_LEFT;
        inventoryLabelY = 115;
    }

    @Override
    protected void init() {
        super.init();
        uiScale = QuantumGuiLayout.scale(width, height);
        leftPos = QuantumGuiLayout.centeredPosition(width, imageWidth, uiScale);
        topPos = QuantumGuiLayout.centeredPosition(height, imageHeight, uiScale);
        if (menu.getPluginType() != QuantumCoolingPluginMenu.PluginType.ADVANCED) return;
        addRenderableWidget(new FluxStyleButton(leftPos + 24, topPos + 196, 175, 22,
                Component.translatable("screen.flux_overdrive.quantum_cooling.use_main_hand"),
                ignored -> sendMenuButton(QuantumCoolingPluginMenu.BUTTON_INTERACT_MAIN_HAND),
                () -> FluxGuiStyle.FALLBACK_ACCENT, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
        addRenderableWidget(new FluxStyleButton(leftPos + 231, topPos + 196, 175, 22,
                Component.translatable("screen.flux_overdrive.quantum_cooling.use_off_hand"),
                ignored -> sendMenuButton(QuantumCoolingPluginMenu.BUTTON_INTERACT_OFF_HAND),
                () -> FluxGuiStyle.FALLBACK_ACCENT, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        int logicalMouseX = (int) Math.floor(mouseX / uiScale);
        int logicalMouseY = (int) Math.floor(mouseY / uiScale);
        graphics.pose().pushPose();
        graphics.pose().scale(uiScale, uiScale, 1.0F);
        super.render(graphics, logicalMouseX, logicalMouseY, partialTick);
        renderTooltip(graphics, logicalMouseX, logicalMouseY);
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX / uiScale, mouseY / uiScale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return super.mouseDragged(mouseX / uiScale, mouseY / uiScale, button,
                dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX / uiScale, mouseY / uiScale, scrollX, scrollY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int accent = FluxGuiStyle.FALLBACK_ACCENT;
        FluxGuiStyle.renderFrame(graphics, leftPos, topPos, imageWidth, imageHeight, accent);
        if (menu.getPluginType() == QuantumCoolingPluginMenu.PluginType.SOLID) {
            FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 34, 406, 64, accent);
            FluxGuiStyle.renderPanel(graphics, leftPos + 112, topPos + 104, 206, 124, accent);
            for (int slot = 0; slot < 9; slot++) {
                renderSlotBackground(graphics,
                        leftPos + QuantumCoolingPluginMenu.SLOT_COLUMN_LEFT + slot * 18,
                        topPos + QuantumCoolingPluginMenu.SOLID_PLUGIN_SLOT_Y);
            }
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    renderSlotBackground(graphics,
                            leftPos + QuantumCoolingPluginMenu.SLOT_COLUMN_LEFT + column * 18,
                            topPos + QuantumCoolingPluginMenu.PLAYER_INVENTORY_SLOT_Y + row * 18);
                }
            }
            for (int column = 0; column < 9; column++) {
                renderSlotBackground(graphics,
                        leftPos + QuantumCoolingPluginMenu.SLOT_COLUMN_LEFT + column * 18,
                        topPos + QuantumCoolingPluginMenu.PLAYER_HOTBAR_SLOT_Y);
            }
        } else {
            FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 34, 199, 194, accent);
            FluxGuiStyle.renderPanel(graphics, leftPos + 219, topPos + 34, 199, 194, accent);
            renderGauge(graphics, leftPos + 24, topPos + 64, 22, 96,
                    menu.getFluidAmount(), menu.getFluidCapacity(), FLUID_COLOR);
            renderGauge(graphics, leftPos + 231, topPos + 64, 22, 96,
                    menu.getChemicalAmount(), menu.getChemicalCapacity(), menu.getChemicalTint());
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFitted(graphics, title, titleLabelX, titleLabelY,
                imageWidth - titleLabelX * 2, FluxGuiStyle.TEXT);
        if (menu.getPluginType() == QuantumCoolingPluginMenu.PluginType.SOLID) {
            renderSolidLabels(graphics);
        } else {
            renderAdvancedLabels(graphics);
        }
    }

    private void renderSolidLabels(GuiGraphics graphics) {
        drawFitted(graphics, Component.translatable("screen.flux_overdrive.quantum_cooling.solid_slot"),
                20, 43, 190, FluxGuiStyle.TEXT);
        drawFitted(graphics, rateComponent(), 222, 43, 188, FluxGuiStyle.MUTED);
        drawFitted(graphics, targetComponent(menu.getTargetKelvin()),
                20, 82, 104, FluxGuiStyle.FALLBACK_ACCENT);
        graphics.drawString(font, Component.translatable("container.inventory"),
                inventoryLabelX, inventoryLabelY, FluxGuiStyle.TEXT, false);
    }

    private void renderAdvancedLabels(GuiGraphics graphics) {
        drawFitted(graphics, Component.translatable("screen.flux_overdrive.quantum_cooling.fluid"),
                24, 44, 175, FluxGuiStyle.TEXT);
        drawFitted(graphics, mediumName(menu.getFluidId()), 54, 65, ADVANCED_TEXT_WIDTH,
                menu.getFluidId().isEmpty() ? FluxGuiStyle.MUTED : FluxGuiStyle.TEXT);
        drawFitted(graphics, amountComponent(menu.getFluidAmount(), menu.getFluidCapacity()),
                54, 82, ADVANCED_TEXT_WIDTH, FluxGuiStyle.MUTED);
        drawFitted(graphics, targetComponent(menu.getFluidTargetKelvin()),
                54, 99, ADVANCED_TEXT_WIDTH, FluxGuiStyle.FALLBACK_ACCENT);

        drawFitted(graphics, Component.translatable("screen.flux_overdrive.quantum_cooling.chemical"),
                231, 44, 175, FluxGuiStyle.TEXT);
        if (!menu.isChemicalAvailable()) {
            drawFitted(graphics,
                    Component.translatable("screen.flux_overdrive.quantum_cooling.mekanism_unavailable"),
                    261, 65, ADVANCED_TEXT_WIDTH, FluxGuiStyle.GOLD);
        } else {
            drawFitted(graphics, mediumName(menu.getChemicalId()), 261, 65, ADVANCED_TEXT_WIDTH,
                    menu.getChemicalId().isEmpty() ? FluxGuiStyle.MUTED : FluxGuiStyle.TEXT);
            drawFitted(graphics, amountComponent(menu.getChemicalAmount(), menu.getChemicalCapacity()),
                    261, 82, ADVANCED_TEXT_WIDTH, FluxGuiStyle.MUTED);
            drawFitted(graphics, targetComponent(menu.getChemicalTargetKelvin()),
                    261, 99, ADVANCED_TEXT_WIDTH, FluxGuiStyle.FALLBACK_ACCENT);
        }
        graphics.drawCenteredString(font, rateComponent(), imageWidth / 2, 176, FluxGuiStyle.MUTED);
    }

    private Component rateComponent() {
        return Component.translatable("screen.flux_overdrive.quantum_cooling.rate",
                String.format(Locale.ROOT, "%.0f K/t", menu.getCoolingRateKelvinPerTick()));
    }

    private static Component targetComponent(double targetKelvin) {
        return targetKelvin < 0.0
                ? Component.translatable("screen.flux_overdrive.quantum_cooling.empty")
                : Component.translatable("screen.flux_overdrive.quantum_cooling.target",
                String.format(Locale.ROOT, "%.0f K", targetKelvin));
    }

    private static Component amountComponent(long amount, long capacity) {
        return Component.literal(compactAmount(amount) + " / " + compactAmount(capacity));
    }

    private static Component mediumName(String id) {
        if (id == null || id.isBlank()) {
            return Component.translatable("screen.flux_overdrive.quantum_cooling.empty");
        }
        int separator = id.indexOf(':');
        String path = separator >= 0 ? id.substring(separator + 1) : id;
        String[] words = path.split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Component.literal(result.toString());
    }

    private static String compactAmount(long value) {
        long nonNegative = Math.max(0L, value);
        if (nonNegative < 1_000L) return Long.toString(nonNegative);
        String[] suffixes = {"K", "M", "G", "T", "P", "E"};
        double scaled = nonNegative;
        int suffix = -1;
        while (scaled >= 1_000.0 && suffix + 1 < suffixes.length) {
            scaled /= 1_000.0;
            suffix++;
        }
        String pattern = scaled < 10.0 ? "%.1f%s" : "%.0f%s";
        return String.format(Locale.ROOT, pattern, scaled, suffixes[suffix]);
    }

    private void drawFitted(GuiGraphics graphics, Component component, int x, int y, int maxWidth, int color) {
        String text = component.getString();
        if (font.width(text) <= maxWidth) {
            graphics.drawString(font, component, x, y, color, false);
            return;
        }
        String ellipsis = "...";
        int available = Math.max(0, maxWidth - font.width(ellipsis));
        String clipped = font.plainSubstrByWidth(text, available) + ellipsis;
        graphics.drawString(font, Component.literal(clipped), x, y, color, false);
    }

    private void sendMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private static void renderGauge(GuiGraphics graphics, int x, int y, int width, int height,
                                    long amount, long capacity, int color) {
        graphics.fill(x, y, x + width, y + height, FluxGuiStyle.FRAME);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF101216);
        double ratio = capacity <= 0L ? 0.0 : Math.clamp(amount / (double) capacity, 0.0, 1.0);
        int fillHeight = (int) Math.round((height - 4) * ratio);
        if (fillHeight > 0) {
            int opaqueColor = 0xFF000000 | color & 0xFFFFFF;
            graphics.fill(x + 2, y + height - 2 - fillHeight,
                    x + width - 2, y + height - 2, opaqueColor);
        }
    }

    private static void renderSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, FluxGuiStyle.FRAME);
        graphics.fill(x, y, x + 16, y + 16, 0xFF101216);
    }
}

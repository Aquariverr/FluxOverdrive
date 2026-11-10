package com.aquariverr.fluxod.client.screen;

import com.aquariverr.fluxod.block.entity.QuantumBatteryThermalModel;
import com.aquariverr.fluxod.client.gui.FluxGuiStyle;
import com.aquariverr.fluxod.client.gui.FluxStyleButton;
import com.aquariverr.fluxod.client.gui.QuantumGuiLayout;
import com.aquariverr.fluxod.client.render.QuantumEnergySphereEffectMath;
import com.aquariverr.fluxod.client.render.QuantumEnergySphereRenderer;
import com.aquariverr.fluxod.menu.QuantumBatteryMenu;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;
import java.util.Locale;

public final class QuantumBatteryScreen extends AbstractContainerScreen<QuantumBatteryMenu> {

    private static final int FRAME = FluxGuiStyle.FRAME;
    private static final int TEXT = FluxGuiStyle.TEXT;
    private static final int CYAN = 0xFF43D9FF;
    private static final int MAGENTA = 0xFFE65CFF;
    private static final int GOLD = FluxGuiStyle.GOLD;
    private static final int MUTED = FluxGuiStyle.MUTED;

    private final Button[] tierButtons = new Button[3];
    private Page page = Page.OVERVIEW;
    private int selectedTier;
    private boolean tierManuallySelected;
    private Button overviewTab;
    private Button buildTab;
    private Button fluxTab;
    private Button buildButton;
    private Button upgradeButton;
    private float uiScale = 1.0F;
    private int observedLeakEventSequence;
    private int activeLeakEventSequence;
    private int leakPulseTicksRemaining;

    public QuantumBatteryScreen(QuantumBatteryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = QuantumGuiLayout.REFERENCE_WIDTH;
        imageHeight = QuantumGuiLayout.REFERENCE_HEIGHT;
        inventoryLabelY = 10_000;
        selectedTier = Math.max(1, menu.getStructureTier());
    }

    @Override
    protected void init() {
        super.init();
        uiScale = QuantumGuiLayout.scale(width, height);
        leftPos = QuantumGuiLayout.centeredPosition(width, imageWidth, uiScale);
        topPos = QuantumGuiLayout.centeredPosition(height, imageHeight, uiScale);

        overviewTab = addRenderableWidget(new FluxStyleButton(leftPos + 12, topPos + 7, 128, 20,
                        Component.translatable("screen.flux_overdrive.quantum_battery.tab"), ignored -> {
                            page = Page.OVERVIEW;
                            updateButtons();
                        }, this::accentColor, () -> page == Page.OVERVIEW, FluxGuiStyle.ButtonStyle.TAB));
        buildTab = addRenderableWidget(new FluxStyleButton(leftPos + 144, topPos + 7, 132, 20,
                        Component.translatable(menu.isRemoteAccess()
                                ? "screen.flux_overdrive.quantum_battery.flux_tab"
                                : "screen.flux_overdrive.quantum_battery.build_tab"), ignored -> {
                            if (menu.isRemoteAccess()) {
                                sendMenuButton(QuantumBatteryMenu.BUTTON_OPEN_FLUX);
                            } else {
                                page = Page.BUILD;
                                updateButtons();
                            }
                        }, this::accentColor, () -> !menu.isRemoteAccess() && page == Page.BUILD,
                        FluxGuiStyle.ButtonStyle.TAB));
        fluxTab = addRenderableWidget(new FluxStyleButton(leftPos + 280, topPos + 7, 138, 20,
                        Component.translatable(menu.isRemoteAccess()
                                ? "screen.flux_overdrive.quantum_battery.return_entanglement"
                                : "screen.flux_overdrive.quantum_battery.flux_tab"),
                        ignored -> sendMenuButton(menu.isRemoteAccess()
                                ? QuantumBatteryMenu.BUTTON_RETURN_ENTANGLEMENT
                                : QuantumBatteryMenu.BUTTON_OPEN_FLUX),
                        this::accentColor, () -> false, FluxGuiStyle.ButtonStyle.TAB));

        for (int tier = 1; tier <= 3; tier++) {
            final int selected = tier;
            tierButtons[tier - 1] = addRenderableWidget(new FluxStyleButton(leftPos + 42 + (tier - 1) * 116,
                    topPos + 77, 108, 26, tierName(tier), ignored -> {
                        selectedTier = selected;
                        tierManuallySelected = true;
                        updateButtons();
                    }, this::accentColor, () -> selectedTier == selected, FluxGuiStyle.ButtonStyle.TIER));
        }

        buildButton = addRenderableWidget(new FluxStyleButton(leftPos + 85, topPos + 195, 260, 25,
                Component.empty(), ignored ->
                        sendMenuButton(QuantumBatteryMenu.BUTTON_BUILD_TIER_1 + selectedTier - 1),
                this::accentColor, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
        upgradeButton = addRenderableWidget(new FluxStyleButton(leftPos + 18, topPos + 195, 64, 20,
                Component.translatable("screen.flux_overdrive.quantum_battery.core_upgrade"),
                ignored -> sendMenuButton(QuantumBatteryMenu.BUTTON_UPGRADE_CORE),
                this::accentColor, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
        updateButtons();
    }

    @Override
    protected void containerTick() {
        if (!tierManuallySelected && menu.getStructureTier() > 0) {
            selectedTier = menu.getStructureTier();
        }
        int leakEventSequence = menu.getLeakEventSequence();
        if (leakEventSequence != observedLeakEventSequence) {
            observedLeakEventSequence = leakEventSequence;
            activeLeakEventSequence = leakEventSequence;
            leakPulseTicksRemaining = QuantumEnergySphereEffectMath.LEAK_PULSE_TICKS;
        } else if (leakPulseTicksRemaining > 0) {
            leakPulseTicksRemaining--;
        }
        updateButtons();
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
        renderFluxFrame(graphics);
        if (page == Page.OVERVIEW) {
            renderFluxPanel(graphics, leftPos + 12, topPos + 32, 306, 36);
            renderFluxPanel(graphics, leftPos + 326, topPos + 32, 92, 72);
            renderFluxPanel(graphics, leftPos + 12, topPos + 74, 76, 154);
            renderFluxPanel(graphics, leftPos + 92, topPos + 74, 226, 154);
            renderFluxGrid(graphics, leftPos + 94, topPos + 76, 222, 150);
            renderThermalLoad(graphics);
            renderCoreProgress(graphics);
            renderEnergySphere(graphics, partialTick);
        } else {
            renderFluxPanel(graphics, leftPos + 12, topPos + 32, 406, 36);
            renderFluxPanel(graphics, leftPos + 12, topPos + 72, 406, 36);
            renderFluxPanel(graphics, leftPos + 12, topPos + 112, 406, 74);
            renderFluxPanel(graphics, leftPos + 12, topPos + 191, 406, 31);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (page == Page.OVERVIEW) {
            renderOverviewLabels(graphics);
        } else {
            renderBuildLabels(graphics);
        }
    }

    private void renderOverviewLabels(GuiGraphics graphics) {
        int statusColor = menu.isSuspended() || !menu.isStructureReady() ? GOLD : accentColor();
        graphics.fill(20, 40, 25, 45, statusColor);
        graphics.drawString(font, statusComponent(), 30, 39,
                statusColor, false);
        graphics.drawString(font, stabilityComponent(), 20, 52,
                menu.isStructureReady()
                        && (!menu.isConstraintPowered() || menu.getStabilityBasisPoints() == 0) ? GOLD : MUTED,
                false);

        graphics.drawString(font, Component.translatable("screen.flux_overdrive.quantum_battery.temperature"),
                334, 40, TEXT, false);
        graphics.drawString(font, Component.literal(String.format(Locale.ROOT, "%.1f K",
                menu.getTemperatureKelvin())), 334, 53, thermalLoadColor(), false);
        graphics.drawString(font, coolingComponent(), 334, 66,
                menu.isCoolingActive() ? CYAN : MUTED, false);
        graphics.drawString(font, Component.translatable(
                        "screen.flux_overdrive.quantum_battery.thermal_load_multiplier",
                        String.format(Locale.ROOT, "%.2f", menu.getConstraintEnergyMultiplier())),
                334, 79, MUTED, false);

        graphics.drawCenteredString(font,
                Component.translatable("screen.flux_overdrive.quantum_battery.stored_energy"),
                215, 80, TEXT);
        graphics.drawCenteredString(font, Component.translatable(
                        "screen.flux_overdrive.quantum_battery.stored_energy_value",
                        menu.getStoredEnergyCompact()),
                215, 93, accentColor());

        drawCenteredWrapped(graphics,
                Component.translatable("screen.flux_overdrive.quantum_battery.manipulation_path"),
                50, 81, 64, TEXT);
        graphics.drawCenteredString(font, Component.translatable(
                        "screen.flux_overdrive.quantum_battery.core_level", menu.getCoreLevel()),
                50, 111, tierColor(menu.getCoreLevel()));
        Component goal = menu.getCoreLevel() >= 3
                ? Component.translatable("screen.flux_overdrive.quantum_battery.core_complete")
                : Component.translatable("screen.flux_overdrive.quantum_battery.core_next",
                menu.getCoreLevel() == 1 ? "1G FE" : "1P FE");
        graphics.drawCenteredString(font, goal, 50, 129, MUTED);
        graphics.drawCenteredString(font, Component.literal(formatProgress(
                        menu.getCoreUpgradeProgressBasisPoints())),
                50, 157, accentColor());
    }

    private void renderBuildLabels(GuiGraphics graphics) {
        int statusColor = menu.isSuspended() || !menu.isStructureReady() ? GOLD : MUTED;
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_battery.select_tier"),
                20, 39, TEXT, false);
        graphics.fill(20, 54, 25, 59, statusColor);
        graphics.drawString(font, statusComponent(), 30, 53, statusColor, false);

        graphics.drawString(font, Component.translatable(
                        "screen.flux_overdrive.quantum_battery.requirements", selectedTier),
                20, 120, TEXT, false);
        int y = 137;
        for (int tier = 1; tier <= selectedTier; tier++) {
            QuantumBatteryStructure.MaterialCounts counts = QuantumBatteryStructure.materialCounts(tier);
            graphics.drawString(font, Component.translatable(
                    "screen.flux_overdrive.quantum_battery.layer_materials",
                    tier, counts.casing(), counts.glass()), 26, y, tierColor(tier), false);
            y += 14;
        }

        Component mode = menu.isRemoteAccess()
                ? Component.translatable("screen.flux_overdrive.quantum_battery.remote_view")
                : menu.isCreativeBuilder()
                ? Component.translatable("screen.flux_overdrive.quantum_battery.creative_hint")
                : Component.translatable("screen.flux_overdrive.quantum_battery.survival_hint");
        graphics.drawCenteredString(font, mode, 215, 225, MUTED);
    }

    private void renderFluxFrame(GuiGraphics graphics) {
        FluxGuiStyle.renderFrame(graphics, leftPos, topPos, imageWidth, imageHeight, accentColor());
    }

    private void renderFluxPanel(GuiGraphics graphics, int x, int y, int width, int height) {
        FluxGuiStyle.renderPanel(graphics, x, y, width, height, accentColor());
    }

    private void renderFluxGrid(GuiGraphics graphics, int x, int y, int width, int height) {
        int gridColor = withAlpha(accentColor(), 13);
        for (int gridX = x + 18; gridX < x + width; gridX += 24) {
            graphics.vLine(gridX, y, y + height, gridColor);
        }
        for (int gridY = y + 16; gridY < y + height; gridY += 16) {
            graphics.hLine(x, x + width, gridY, gridColor);
        }
    }

    private Component statusComponent() {
        return menu.isSuspended()
                ? Component.translatable("screen.flux_overdrive.quantum_battery.suspended")
                : !menu.isStructureReady()
                ? Component.translatable("screen.flux_overdrive.quantum_battery.not_ready")
                : Component.translatable("screen.flux_overdrive.quantum_battery.current_tier",
                menu.getStructureTier());
    }

    private Component stabilityComponent() {
        if (!menu.isStructureReady() || menu.isSuspended()) {
            return Component.translatable("screen.flux_overdrive.quantum_battery.stability",
                    menu.getStabilityPercent());
        }
        if (!menu.isConstraintPowered() && menu.getStabilityBasisPoints() == 0) {
            return Component.translatable("screen.flux_overdrive.quantum_battery.stability_zero",
                    menu.getStabilityPercent());
        }
        if (!menu.isConstraintPowered()) {
            return Component.translatable("screen.flux_overdrive.quantum_battery.stability_decaying",
                    menu.getStabilityPercent());
        }
        if (menu.getStabilityBasisPoints() < 10_000) {
            return Component.translatable("screen.flux_overdrive.quantum_battery.stability_recovering",
                    menu.getStabilityPercent());
        }
        return Component.translatable("screen.flux_overdrive.quantum_battery.stability",
                menu.getStabilityPercent());
    }

    private Component coolingComponent() {
        if (!menu.hasCoolingTarget()) {
            return Component.translatable("screen.flux_overdrive.quantum_battery.cooling_none");
        }
        String target = String.format(Locale.ROOT, "%.0f K", menu.getCoolingTargetKelvin());
        return Component.translatable(menu.isCoolingActive()
                ? "screen.flux_overdrive.quantum_battery.cooling_active"
                : "screen.flux_overdrive.quantum_battery.cooling_idle", target);
    }

    private void renderThermalLoad(GuiGraphics graphics) {
        int x = leftPos + 404;
        int y = topPos + 40;
        int width = 10;
        int height = 46;
        int color = thermalLoadColor();
        graphics.fill(x, y, x + width, y + height, FRAME);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF101216);
        double normalized = menu.getThermalLoadPercent() / 100.0;
        int fillHeight = (int) Math.round((height - 4) * Math.clamp(normalized, 0.0, 1.0));
        if (fillHeight > 0) {
            graphics.fill(x + 2, y + height - 2 - fillHeight,
                    x + width - 2, y + height - 2, color);
        }
        graphics.hLine(x - 4, x - 1, y + 3, withAlpha(color, 150));
        graphics.hLine(x - 4, x - 1, y + height / 2, withAlpha(color, 150));
        graphics.hLine(x - 4, x - 1, y + height - 3, withAlpha(color, 150));
        drawFilledCircle(graphics, x + width / 2, y + height + 4, 7, FRAME);
        drawFilledCircle(graphics, x + width / 2, y + height + 4, 5, color);
    }

    private void renderCoreProgress(GuiGraphics graphics) {
        int x = leftPos + 18;
        int y = topPos + 174;
        int width = 64;
        int progress = menu.getCoreUpgradeProgressBasisPoints();
        graphics.fill(x, y, x + width, y + 7, FRAME);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 6, 0xFF101216);
        int filledWidth = (int) Math.round((width - 2) * progress / 10_000.0);
        if (filledWidth > 0) {
            graphics.fill(x + 1, y + 1, x + 1 + filledWidth, y + 6,
                    withAlpha(accentColor(), 220));
        }
        graphics.hLine(x + 1, x + width - 1, y + 8, withAlpha(accentColor(), 80));
    }

    private void renderEnergySphere(GuiGraphics graphics, float partialTick) {
        double normalized = Math.clamp(menu.getEnergyLogScale(), 0.0, 1.0);
        int centerX = leftPos + 215;
        int centerY = topPos + 158;
        int baseRadius = 11 + (int) Math.round(normalized * 44.0);
        double gameTime = minecraft != null && minecraft.level != null
                ? minecraft.level.getGameTime() + partialTick : partialTick;
        double leakPulseAge = leakPulseTicksRemaining > 0
                ? QuantumEnergySphereEffectMath.LEAK_PULSE_TICKS
                - leakPulseTicksRemaining + partialTick
                : QuantumEnergySphereEffectMath.NO_LEAK_PULSE;
        boolean quenchImminent = menu.isStructureReady() && !menu.isSuspended()
                && !menu.isConstraintPowered() && menu.areInstabilityPenaltiesEnabled();
        QuantumEnergySphereEffectMath.Effects effects = QuantumEnergySphereEffectMath.calculate(
                menu.getStabilityBasisPoints(), quenchImminent, activeLeakEventSequence,
                leakPulseAge, gameTime);
        float radius = (float) (baseRadius * effects.radiusScale());
        if (normalized > 0.0) {
            int networkColor = menu.getNetworkColor();
            int roundedRadius = Math.round(radius);
            int flashAlpha = (int) Math.round(effects.flashIntensity() * 28.0);
            drawFilledCircle(graphics, centerX, centerY, roundedRadius + 14,
                    withAlpha(networkColor, 8 + flashAlpha / 3));
            drawFilledCircle(graphics, centerX, centerY, roundedRadius + 10,
                    withAlpha(networkColor, 14 + flashAlpha / 2));
            drawFilledCircle(graphics, centerX, centerY, roundedRadius + 5,
                    withAlpha(networkColor, 28 + flashAlpha));
        }
        enableScaledScissor(graphics, leftPos + 92, topPos + 94,
                leftPos + 308, topPos + 218);
        try {
            QuantumEnergySphereRenderer.render(graphics, centerX, centerY, radius, gameTime,
                    normalized, menu.getNetworkColor(), effects);
        } finally {
            graphics.disableScissor();
        }
        int railX = leftPos + 92;
        int railWidth = 216;
        graphics.fill(railX, topPos + 220, railX + railWidth, topPos + 222, 0xFF292B31);
        int filledWidth = (int) Math.round(railWidth * normalized);
        if (filledWidth > 0) {
            graphics.fill(railX, topPos + 220, railX + filledWidth, topPos + 222,
                    withAlpha(accentColor(), 220));
        }
        graphics.hLine(railX, railX + railWidth, topPos + 223, withAlpha(accentColor(), 80));
    }

    private void enableScaledScissor(GuiGraphics graphics, int minX, int minY, int maxX, int maxY) {
        graphics.enableScissor((int) Math.floor(minX * uiScale),
                (int) Math.floor(minY * uiScale),
                (int) Math.ceil(maxX * uiScale),
                (int) Math.ceil(maxY * uiScale));
    }

    private int thermalLoadColor() {
        double temperature = menu.getTemperatureKelvin();
        if (temperature <= QuantumBatteryThermalModel.ROOM_TEMPERATURE_K) {
            return blendColor(0xFF43D9FF, 0xFF63E6A2,
                    temperature / QuantumBatteryThermalModel.ROOM_TEMPERATURE_K);
        }
        double hot = (temperature - QuantumBatteryThermalModel.ROOM_TEMPERATURE_K)
                / (QuantumBatteryThermalModel.MAX_TEMPERATURE_K
                - QuantumBatteryThermalModel.ROOM_TEMPERATURE_K);
        return blendColor(0xFF63E6A2, 0xFFFF554D, hot);
    }

    private int accentColor() {
        return 0xFF000000 | menu.getNetworkColor();
    }

    private static int blendColor(int from, int to, double amount) {
        return FluxGuiStyle.blendColor(from, to, amount);
    }

    private static int withAlpha(int color, int alpha) {
        return FluxGuiStyle.withAlpha(color, alpha);
    }

    private static void drawFilledCircle(GuiGraphics graphics, int centerX, int centerY,
                                         int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.floor(Math.sqrt(radius * radius - y * y));
            graphics.fill(centerX - halfWidth, centerY + y,
                    centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }

    private void updateButtons() {
        boolean overviewPage = page == Page.OVERVIEW;
        boolean buildPage = page == Page.BUILD && !menu.isRemoteAccess();
        if (overviewTab != null) overviewTab.active = buildPage;
        if (buildTab != null) buildTab.active = !buildPage;
        if (fluxTab != null) fluxTab.active = true;
        for (int tier = 1; tier <= tierButtons.length; tier++) {
            Button button = tierButtons[tier - 1];
            if (button != null) {
                button.visible = buildPage;
                button.active = buildPage && tier <= menu.getCoreLevel() && tier != selectedTier;
                button.setTooltip(tier > menu.getCoreLevel()
                        ? Tooltip.create(Component.translatable(
                        "screen.flux_overdrive.quantum_battery.build_core_locked", tier))
                        : null);
            }
        }
        if (buildButton != null) {
            buildButton.visible = buildPage;
            buildButton.active = buildPage && selectedTier <= menu.getCoreLevel() && !menu.isAssembling();
            buildButton.setMessage(Component.translatable(menu.isCreativeBuilder()
                    ? (menu.isAssembling() ? "screen.flux_overdrive.quantum_battery.building"
                    : "screen.flux_overdrive.quantum_battery.build_creative")
                    : (menu.isAssembling() ? "screen.flux_overdrive.quantum_battery.building"
                    : "screen.flux_overdrive.quantum_battery.build_survival")));
        }
        if (upgradeButton != null) {
            int coreLevel = menu.getCoreLevel();
            upgradeButton.visible = overviewPage;
            upgradeButton.active = overviewPage && menu.canUpgradeCore();
            upgradeButton.setMessage(Component.translatable(coreLevel >= 3
                    ? "screen.flux_overdrive.quantum_battery.core_max"
                    : "screen.flux_overdrive.quantum_battery.core_upgrade"));
            String tooltipKey = coreLevel >= 3
                    ? "screen.flux_overdrive.quantum_battery.core_upgrade_complete"
                    : menu.canUpgradeCore()
                    ? "screen.flux_overdrive.quantum_battery.core_upgrade_available"
                    : "screen.flux_overdrive.quantum_battery.core_upgrade_unavailable";
            upgradeButton.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        }
    }

    private void sendMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private static Component tierName(int tier) {
        return Component.translatable("screen.flux_overdrive.quantum_battery.tier", tier);
    }

    private void drawCenteredWrapped(GuiGraphics graphics, Component text, int centerX, int y,
                                     int width, int color) {
        List<FormattedCharSequence> lines = font.split(text, width);
        int count = Math.min(lines.size(), 2);
        for (int index = 0; index < count; index++) {
            graphics.drawCenteredString(font, lines.get(index), centerX, y + index * 10, color);
        }
    }

    private static String formatProgress(int basisPoints) {
        int clamped = Math.clamp(basisPoints, 0, 10_000);
        if (clamped % 100 == 0) return clamped / 100 + "%";
        if (clamped % 10 == 0) return String.format(Locale.ROOT, "%.1f%%", clamped / 100.0);
        return String.format(Locale.ROOT, "%.2f%%", clamped / 100.0);
    }

    private static int tierColor(int tier) {
        return switch (tier) {
            case 1 -> CYAN;
            case 2 -> MAGENTA;
            default -> GOLD;
        };
    }

    private enum Page {
        OVERVIEW,
        BUILD
    }
}

package com.aquariverr.fluxod.client.screen;

import com.aquariverr.fluxod.client.gui.FluxGuiStyle;
import com.aquariverr.fluxod.client.gui.FluxEditBox;
import com.aquariverr.fluxod.client.gui.FluxButtonSound;
import com.aquariverr.fluxod.client.gui.FluxStyleButton;
import com.aquariverr.fluxod.client.gui.QuantumGuiLayout;
import com.aquariverr.fluxod.menu.QuantumEntanglementMenu;
import com.aquariverr.fluxod.entanglement.QuantumPluginAdapters;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpoint;
import com.aquariverr.fluxod.network.BindQuantumBatteryPayload;
import com.aquariverr.fluxod.network.SelectEntanglementPluginPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

public final class QuantumEntanglementScreen extends AbstractContainerScreen<QuantumEntanglementMenu> {

    private static final int PLUGIN_ROW_X = 18;
    private static final int PLUGIN_ROW_Y = 69;
    private static final int PLUGIN_ROW_WIDTH = 394;
    private static final int PLUGIN_ROW_HEIGHT = 35;
    private static final int VISIBLE_PLUGIN_ROWS = 6;

    private Button buildButton;
    private Button unbindButton;
    private Button openBatteryButton;
    private Button openPluginButton;
    private Button bindButton;
    private Button bindingTabButton;
    private Button pluginsTabButton;
    private Button automaticInputButton;
    private Button automaticOutputButton;
    private final PluginEndpointButton[] pluginButtons = new PluginEndpointButton[VISIBLE_PLUGIN_ROWS];
    private EditBox xInput;
    private EditBox yInput;
    private EditBox zInput;
    private EditBox dimensionInput;
    private float uiScale = 1.0F;
    private Page page = Page.BINDING;
    private int pluginScrollOffset;

    public QuantumEntanglementScreen(QuantumEntanglementMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = QuantumGuiLayout.REFERENCE_WIDTH;
        imageHeight = 342;
        titleLabelX = 14;
        titleLabelY = 11;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        String previousX = inputValue(xInput);
        String previousY = inputValue(yInput);
        String previousZ = inputValue(zInput);
        String previousDimension = inputValue(dimensionInput);
        super.init();

        uiScale = QuantumGuiLayout.scale(width, height);
        leftPos = QuantumGuiLayout.centeredPosition(width, imageWidth, uiScale);
        topPos = QuantumGuiLayout.centeredPosition(height, imageHeight, uiScale);

        bindingTabButton = addRenderableWidget(new FluxStyleButton(leftPos + 12, topPos + 31, 128, 22,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.binding_tab"),
                ignored -> setPage(Page.BINDING), this::accentColor, () -> page == Page.BINDING,
                FluxGuiStyle.ButtonStyle.TAB));
        pluginsTabButton = addRenderableWidget(new FluxStyleButton(leftPos + 144, topPos + 31, 132, 22,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.plugins_tab"),
                ignored -> setPage(Page.PLUGINS), this::accentColor, () -> page == Page.PLUGINS,
                FluxGuiStyle.ButtonStyle.TAB));
        openPluginButton = addRenderableWidget(new FluxStyleButton(leftPos + 280, topPos + 31, 138, 22,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.open_plugin"),
                ignored -> sendMenuButton(QuantumEntanglementMenu.BUTTON_OPEN_PLUGIN),
                this::accentColor, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
        for (int row = 0; row < VISIBLE_PLUGIN_ROWS; row++) {
            pluginButtons[row] = addRenderableWidget(new PluginEndpointButton(this, row));
        }

        xInput = addRenderableWidget(coordinateInput(leftPos + 34, topPos + 155, 92, "X", previousX));
        yInput = addRenderableWidget(coordinateInput(leftPos + 151, topPos + 155, 92, "Y", previousY));
        zInput = addRenderableWidget(coordinateInput(leftPos + 268, topPos + 155, 142, "Z", previousZ));
        dimensionInput = new FluxEditBox(font, leftPos + 88, topPos + 184, 214, 20,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.dimension"),
                this::accentColor);
        dimensionInput.setMaxLength(128);
        dimensionInput.setHint(Component.translatable(
                "screen.flux_overdrive.quantum_entanglement.dimension_hint"));
        dimensionInput.setValue(previousDimension);
        addRenderableWidget(dimensionInput);

        bindButton = addRenderableWidget(new FluxStyleButton(leftPos + 310, topPos + 184, 100, 20,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.bind"),
                ignored -> bindFromInputs(), this::accentColor, () -> false,
                FluxGuiStyle.ButtonStyle.ACTION));
        openBatteryButton = addRenderableWidget(new FluxStyleButton(leftPos + 18, topPos + 301, 150, 24,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.open_battery"),
                ignored -> sendMenuButton(QuantumEntanglementMenu.BUTTON_OPEN_BATTERY),
                this::accentColor, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
        buildButton = addRenderableWidget(new FluxStyleButton(leftPos + 174, topPos + 301, 142, 24,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.build"),
                ignored -> sendMenuButton(QuantumEntanglementMenu.BUTTON_BUILD),
                this::accentColor, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
        unbindButton = addRenderableWidget(new FluxStyleButton(leftPos + 322, topPos + 301, 90, 24,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.unbind"),
                ignored -> sendMenuButton(QuantumEntanglementMenu.BUTTON_UNBIND),
                this::accentColor, () -> false, FluxGuiStyle.ButtonStyle.ACTION));
        automaticInputButton = addRenderableWidget(new FluxStyleButton(leftPos + 18, topPos + 301, 194, 24,
                Component.empty(), ignored -> sendMenuButton(QuantumEntanglementMenu.BUTTON_TOGGLE_AUTO_INPUT),
                this::accentColor, menu::isAutomaticInputEnabled, FluxGuiStyle.ButtonStyle.ACTION));
        automaticOutputButton = addRenderableWidget(new FluxStyleButton(leftPos + 218, topPos + 301, 194, 24,
                Component.empty(), ignored -> sendMenuButton(QuantumEntanglementMenu.BUTTON_TOGGLE_AUTO_OUTPUT),
                this::accentColor, menu::isAutomaticOutputEnabled, FluxGuiStyle.ButtonStyle.ACTION));
        updatePageWidgets();
        updateButtons();
    }

    @Override
    protected void containerTick() {
        clampPluginScroll();
        refreshPluginButtons();
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
        renderPluginItemTooltip(graphics, logicalMouseX, logicalMouseY);
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
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX / uiScale, mouseY / uiScale, button,
                dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double logicalX = mouseX / uiScale;
        double logicalY = mouseY / uiScale;
        if (page == Page.PLUGINS && logicalX >= leftPos + PLUGIN_ROW_X
                && logicalX < leftPos + PLUGIN_ROW_X + PLUGIN_ROW_WIDTH
                && logicalY >= topPos + PLUGIN_ROW_Y
                && logicalY < topPos + PLUGIN_ROW_Y + PLUGIN_ROW_HEIGHT * VISIBLE_PLUGIN_ROWS
                && scrollY != 0.0) {
            pluginScrollOffset += scrollY > 0.0 ? -1 : 1;
            clampPluginScroll();
            refreshPluginButtons();
            return true;
        }
        return super.mouseScrolled(mouseX / uiScale, mouseY / uiScale, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && hasFocusedInput()) {
            bindFromInputs();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int accent = accentColor();
        FluxGuiStyle.renderFrame(graphics, leftPos, topPos, imageWidth, imageHeight, accent);
        if (page == Page.BINDING) {
            FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 59, 406, 56, accent);
            FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 121, 406, 125, accent);
            FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 252, 406, 32, accent);
        } else {
            FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 59, 406, 224, accent);
        }
        FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 292, 406, 42, accent);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int accent = accentColor();
        graphics.drawString(font, title, titleLabelX, titleLabelY, FluxGuiStyle.TEXT, false);

        if (page == Page.PLUGINS) {
            renderPluginDirectory(graphics, mouseX, mouseY, accent);
            return;
        }

        int structureColor = menu.isFormed() ? accent : FluxGuiStyle.GOLD;
        Component structure = menu.isFormed()
                ? Component.translatable("screen.flux_overdrive.quantum_entanglement.formed")
                : Component.translatable("screen.flux_overdrive.quantum_entanglement.not_formed");
        graphics.fill(20, 68, 25, 73, structureColor);
        graphics.drawString(font, structure, 30, 67, structureColor, false);

        Component binding = !menu.isBound()
                ? Component.translatable("screen.flux_overdrive.quantum_entanglement.unbound")
                : menu.isTargetAvailable()
                ? Component.translatable("screen.flux_overdrive.quantum_entanglement.target_online",
                menu.getTargetTier())
                : Component.translatable("screen.flux_overdrive.quantum_entanglement.target_offline");
        int bindingColor = menu.isBound() && menu.isTargetAvailable() ? accent : FluxGuiStyle.GOLD;
        graphics.fill(20, 85, 25, 90, bindingColor);
        graphics.drawString(font, binding, 30, 84, bindingColor, false);
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.binding_hint"),
                20, 101, FluxGuiStyle.MUTED, false);

        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.manual_binding"),
                20, 130, FluxGuiStyle.TEXT, false);
        graphics.drawString(font, Component.literal("X"), 20, 161, FluxGuiStyle.MUTED, false);
        graphics.drawString(font, Component.literal("Y"), 137, 161, FluxGuiStyle.MUTED, false);
        graphics.drawString(font, Component.literal("Z"), 254, 161, FluxGuiStyle.MUTED, false);
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.dimension"),
                20, 190, FluxGuiStyle.MUTED, false);
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_entanglement.energy_hint"),
                20, 263, FluxGuiStyle.MUTED, false);
    }

    private EditBox coordinateInput(int x, int y, int width, String hint, String value) {
        EditBox input = new FluxEditBox(font, x, y, width, 20, Component.literal(hint), this::accentColor);
        input.setMaxLength(11);
        input.setFilter(QuantumEntanglementScreen::isCoordinateInput);
        input.setHint(Component.literal("0"));
        input.setValue(value);
        return input;
    }

    private void bindFromInputs() {
        if (minecraft == null || minecraft.player == null) return;
        try {
            int x = Integer.parseInt(xInput.getValue().trim());
            int y = Integer.parseInt(yInput.getValue().trim());
            int z = Integer.parseInt(zInput.getValue().trim());
            PacketDistributor.sendToServer(new BindQuantumBatteryPayload(
                    menu.containerId, menu.getControllerPos(), x, y, z, dimensionInput.getValue().trim()));
        } catch (NumberFormatException ignored) {
            minecraft.player.displayClientMessage(Component.translatable(
                    "message.flux_overdrive.quantum_entanglement.invalid_coordinates"), true);
        }
    }

    private boolean hasFocusedInput() {
        return xInput.isFocused() || yInput.isFocused() || zInput.isFocused() || dimensionInput.isFocused();
    }

    private void updateButtons() {
        if (buildButton != null) buildButton.active = !menu.isFormed();
        if (unbindButton != null) unbindButton.active = menu.isBound();
        if (openBatteryButton != null) openBatteryButton.active = menu.isTargetResolved();
        if (openPluginButton != null) openPluginButton.active = menu.selectedEndpointHasRemoteMenu();
        if (automaticInputButton != null) {
            automaticInputButton.setMessage(Component.translatable(menu.isAutomaticInputEnabled()
                    ? "screen.flux_overdrive.quantum_entanglement.automatic_input_on"
                    : "screen.flux_overdrive.quantum_entanglement.automatic_input_off"));
            automaticInputButton.active = menu.hasController();
        }
        if (automaticOutputButton != null) {
            automaticOutputButton.setMessage(Component.translatable(menu.isAutomaticOutputEnabled()
                    ? "screen.flux_overdrive.quantum_entanglement.automatic_output_on"
                    : "screen.flux_overdrive.quantum_entanglement.automatic_output_off"));
            automaticOutputButton.active = menu.hasController();
        }
    }

    private void setPage(Page nextPage) {
        if (page == nextPage) return;
        page = nextPage;
        updatePageWidgets();
    }

    private void updatePageWidgets() {
        boolean binding = page == Page.BINDING;
        if (xInput != null) xInput.visible = binding;
        if (yInput != null) yInput.visible = binding;
        if (zInput != null) zInput.visible = binding;
        if (dimensionInput != null) dimensionInput.visible = binding;
        if (bindButton != null) bindButton.visible = binding;
        if (openBatteryButton != null) openBatteryButton.visible = binding;
        if (buildButton != null) buildButton.visible = binding;
        if (unbindButton != null) unbindButton.visible = binding;
        if (automaticInputButton != null) automaticInputButton.visible = !binding;
        if (automaticOutputButton != null) automaticOutputButton.visible = !binding;
        if (bindingTabButton != null) bindingTabButton.active = !binding;
        if (pluginsTabButton != null) pluginsTabButton.active = binding;
        refreshPluginButtons();
    }

    private void renderPluginDirectory(GuiGraphics graphics, int mouseX, int mouseY, int accent) {
        var endpoints = menu.getPluginEndpoints();
        graphics.drawString(font, Component.translatable(
                "screen.flux_overdrive.quantum_entanglement.plugins_count", endpoints.size()),
                20, 61, FluxGuiStyle.MUTED, false);
        if (endpoints.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                    "screen.flux_overdrive.quantum_entanglement.no_plugins"), imageWidth / 2, 164,
                    FluxGuiStyle.MUTED);
        }
    }

    private void renderPluginButtonContents(GuiGraphics graphics, PluginEndpointButton button,
                                            QuantumPluginEndpoint endpoint, boolean selected) {
        int x = button.getX();
        int y = button.getY();
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(endpoint.iconItemId()));
        graphics.renderItem(icon, x + 8, y + 8);
        String name = fit(Component.translatable(endpoint.displayTranslationKey()).getString(), 156);
        graphics.drawString(font, name, x + 32, y + 5, FluxGuiStyle.TEXT, false);

        Component location = Component.translatable(
                "screen.flux_overdrive.quantum_entanglement.ring_location", endpoint.ringTier(),
                Component.translatable("screen.flux_overdrive.quantum_entanglement.location."
                        + endpoint.location().serializedName()));
        String locationText = location.getString();
        if (QuantumPluginAdapters.isConstraint(endpoint.key())) {
            locationText = locationText + " · " + Component.translatable(
                    "screen.flux_overdrive.quantum_entanglement.all_constraints").getString();
        }
        graphics.drawString(font, fit(locationText, 156), x + 32, y + 19,
                selected ? accentColor() : FluxGuiStyle.MUTED, false);

        BlockPos pos = endpoint.absolutePos();
        Component coordinates = Component.translatable(
                "screen.flux_overdrive.quantum_entanglement.plugin_position",
                pos.getX(), pos.getY(), pos.getZ());
        graphics.drawString(font, fit(coordinates.getString(), 119), x + 209, y + 12,
                FluxGuiStyle.MUTED, false);

        Component action = Component.translatable(selected
                ? "screen.flux_overdrive.quantum_entanglement.selected_plugin"
                : "screen.flux_overdrive.quantum_entanglement.select_plugin");
        graphics.drawCenteredString(font, fit(action.getString(), 58), x + PLUGIN_ROW_WIDTH - 34,
                y + 12, selected ? accentColor() : FluxGuiStyle.TEXT);
    }

    private void renderPluginItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (page != Page.PLUGINS) return;
        int row = pluginRowAt(mouseX, mouseY);
        if (row < 0 || mouseX >= leftPos + PLUGIN_ROW_X + 31) return;
        int endpointIndex = pluginScrollOffset + row;
        if (endpointIndex >= menu.getPluginEndpoints().size()) return;
        QuantumPluginEndpoint endpoint = menu.getPluginEndpoints().get(endpointIndex);
        graphics.renderTooltip(font, new ItemStack(BuiltInRegistries.ITEM.get(endpoint.iconItemId())),
                mouseX, mouseY);
    }

    private int pluginRowAt(double logicalMouseX, double logicalMouseY) {
        double localX = logicalMouseX - leftPos;
        double localY = logicalMouseY - topPos;
        if (localX < PLUGIN_ROW_X || localX >= PLUGIN_ROW_X + PLUGIN_ROW_WIDTH
                || localY < PLUGIN_ROW_Y
                || localY >= PLUGIN_ROW_Y + PLUGIN_ROW_HEIGHT * VISIBLE_PLUGIN_ROWS) {
            return -1;
        }
        int row = (int) ((localY - PLUGIN_ROW_Y) / PLUGIN_ROW_HEIGHT);
        return localY < PLUGIN_ROW_Y + row * PLUGIN_ROW_HEIGHT + PLUGIN_ROW_HEIGHT - 2 ? row : -1;
    }

    private void clampPluginScroll() {
        pluginScrollOffset = Math.clamp(pluginScrollOffset, 0,
                Math.max(0, menu.getPluginEndpoints().size() - VISIBLE_PLUGIN_ROWS));
    }

    private void refreshPluginButtons() {
        for (int row = 0; row < pluginButtons.length; row++) {
            PluginEndpointButton button = pluginButtons[row];
            if (button == null) continue;
            QuantumPluginEndpoint endpoint = endpointAtVisibleRow(row);
            button.visible = page == Page.PLUGINS && endpoint != null;
            button.active = endpoint != null;
            if (endpoint == null) {
                button.setMessage(Component.empty());
                continue;
            }
            boolean selected = endpoint.key().equals(menu.getSelectedEndpointKey());
            Component status = Component.translatable(selected
                    ? "screen.flux_overdrive.quantum_entanglement.selected_plugin"
                    : "screen.flux_overdrive.quantum_entanglement.select_plugin");
            button.setMessage(Component.translatable(endpoint.displayTranslationKey()).copy()
                    .append(Component.literal(", ")).append(status));
        }
    }

    private QuantumPluginEndpoint endpointAtVisibleRow(int row) {
        int endpointIndex = pluginScrollOffset + row;
        if (row < 0 || row >= VISIBLE_PLUGIN_ROWS
                || endpointIndex < 0 || endpointIndex >= menu.getPluginEndpoints().size()) {
            return null;
        }
        return menu.getPluginEndpoints().get(endpointIndex);
    }

    private void selectPluginAtVisibleRow(int row) {
        QuantumPluginEndpoint endpoint = endpointAtVisibleRow(row);
        if (endpoint == null || endpoint.key().equals(menu.getSelectedEndpointKey())) return;
        PacketDistributor.sendToServer(new SelectEntanglementPluginPayload(menu.containerId,
                menu.getControllerPos(), menu.getPluginDirectoryRevision(), endpoint.key().save()));
    }

    private String fit(String value, int maxWidth) {
        if (font.width(value) <= maxWidth) return value;
        String suffix = "...";
        return font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(suffix))) + suffix;
    }

    private void sendMenuButton(int buttonId) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
        }
    }

    private int accentColor() {
        return FluxGuiStyle.accent(menu.getNetworkColor());
    }

    private static boolean isCoordinateInput(String value) {
        if (value.isEmpty() || value.equals("-")) return true;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) return false;
        for (int index = start; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    private static String inputValue(EditBox input) {
        return input == null ? "" : input.getValue();
    }

    private static final class PluginEndpointButton extends Button {

        private final QuantumEntanglementScreen screen;
        private final int row;

        private PluginEndpointButton(QuantumEntanglementScreen screen, int row) {
            super(screen.leftPos + PLUGIN_ROW_X,
                    screen.topPos + PLUGIN_ROW_Y + row * PLUGIN_ROW_HEIGHT,
                    PLUGIN_ROW_WIDTH, PLUGIN_ROW_HEIGHT - 2, Component.empty(),
                    ignored -> screen.selectPluginAtVisibleRow(row),
                    messageSupplier -> messageSupplier.get());
            this.screen = screen;
            this.row = row;
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
            FluxButtonSound.play(soundManager);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            QuantumPluginEndpoint endpoint = screen.endpointAtVisibleRow(row);
            if (endpoint == null) return;
            boolean selected = endpoint.key().equals(screen.menu.getSelectedEndpointKey());
            FluxGuiStyle.renderButton(graphics, screen.font, getX(), getY(), getWidth(), getHeight(),
                    Component.empty(), screen.accentColor(), selected, active, isHoveredOrFocused(),
                    FluxGuiStyle.ButtonStyle.ACTION);
            screen.renderPluginButtonContents(graphics, this, endpoint, selected);
        }
    }

    private enum Page {
        BINDING,
        PLUGINS
    }
}

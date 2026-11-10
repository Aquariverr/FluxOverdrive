package com.aquariverr.fluxod.client.screen;

import com.aquariverr.fluxod.client.gui.FluxEditBox;
import com.aquariverr.fluxod.client.gui.FluxGuiStyle;
import com.aquariverr.fluxod.client.gui.FluxStyleButton;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridInteraction;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridViewport;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureGridViewport.Rect;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureIconButton;
import com.aquariverr.fluxod.client.gui.miniature.MiniatureResourceRendering;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.GridDirection;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryEditing;
import com.aquariverr.fluxod.miniature.storage.FactoryEditing.MarkerSource;
import com.aquariverr.fluxod.miniature.recipe.RecipePreview;
import com.aquariverr.fluxod.miniature.compile.CompilationPreview;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload;
import com.aquariverr.fluxod.network.MiniatureFactoryCommandPayload.Action;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.CellPreview;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.FactorySnapshot;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import io.netty.buffer.Unpooled;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.lwjgl.glfw.GLFW;

import java.math.BigInteger;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MiniatureUniverseScreen extends AbstractContainerScreen<MiniatureUniverseMenu> {
    private static WeakReference<MiniatureUniverseScreen> lastEditor = new WeakReference<>(null);
    private static final int ACCENT = FluxGuiStyle.FALLBACK_ACCENT;
    private static final int MARKER_COLOR = 0xFFFFD36A;
    private static final int OUTPUT_COLOR = 0xFF80D4A0;
    private final Map<ResourceKey, MiniatureResourceRendering.Visual> icons = new HashMap<>();
    private MiniatureIconButton markerSource;
    private ItemStack observedCarried = ItemStack.EMPTY;
    private List<MarkerSource> markerSources = List.of();
    private int markerSourceIndex;
    private MiniatureGridViewport viewport;
    private FactorySnapshot observed;
    private int recipeOutputOffset;
    private int outputRowOffset;
    private MiniatureIconButton compile;
    private MiniatureIconButton autoBalance;
    private MiniatureIconButton upgradeTab;
    private MiniatureIconButton upgradeConfirm;
    private boolean viewingUpgrade;
    private boolean viewingCapacity;
    private ResourceKey capacityResource;
    private MiniatureIconButton resetCapacity;
    private MiniatureIconButton runSwitch;
    private MiniatureIconButton redstone;
    private FluxEditBox quantity;
    private MiniatureIconButton applyQuantity;
    private MiniatureIconButton deposit;
    private final List<MiniatureIconButton> directionButtons = new ArrayList<>();
    private int selectedX = -1;
    private int selectedY = -1;
    private int inventoryOffset;
    private boolean outputInventory;
    private boolean panning;
    private boolean quantityDirty;
    private boolean settingQuantity;
    private GridDirection selectedBranch = GridDirection.RIGHT;
    private long nextCommandId = 1;
    private long pendingCommand;
    private long observedAcknowledgement;
    private Component feedback = Component.empty();

    public MiniatureUniverseScreen(MiniatureUniverseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 310;
        imageHeight = 234;
        inventoryLabelX = 8;
        inventoryLabelY = 140;
    }

    @Override
    protected void init() {
        String draft = quantity != null ? quantity.getValue() : "";
        MiniatureGridViewport previousViewport = viewport;
        super.init();
        lastEditor = new WeakReference<>(this);
        observed = null;
        viewport = new MiniatureGridViewport(Math.max(3, menu.snapshot().size()),
                new Rect(leftPos + 8, topPos + 51, 172, 81), 24, 3, 0.25, 2.0);
        if (previousViewport != null && previousViewport.gridSize() == viewport.gridSize()) {
            zoom(previousViewport.scale());
            viewport.panBy(previousViewport.originX() - previousViewport.clip().x() - (viewport.originX() - viewport.clip().x()),
                    previousViewport.originY() - previousViewport.clip().y() - (viewport.originY() - viewport.clip().y()));
        } else if (viewport.gridSize() > 3) viewport.fit();
        resetCapacity = iconButton(188, 110, 114, 18, text("cache_reset").getString(), "cache_reset_hint", button -> {
            if (available()) {
                setQuantity(menu.snapshot().inputCapacity().toString());
                quantityDirty = true;
                commitQuantity();
            }
        }, () -> false);
        runSwitch = iconButton(144, 8, 16, 16, "\u25b6", "run", button -> sendControl(Action.TOGGLE_RUN),
                () -> menu.snapshot().runtime().enabled());
        redstone = iconButton(162, 8, 16, 16, "R", "redstone.ignore", button -> sendControl(Action.CYCLE_REDSTONE),
                () -> menu.snapshot().runtime().redstoneMode() != com.aquariverr.fluxod.miniature.runtime.RedstoneMode.IGNORE);
        compile = iconButton(108, 33, 16, 16, "\u2713", "compile", button -> sendCompile(), () -> false);
        autoBalance = iconButton(90, 33, 16, 16, "=", "auto_balance", button -> {
            if (editable() && !quantityDirty) sendInventory(Action.AUTO_BALANCE, -1, 1);
        }, () -> false);
        upgradeTab = iconButton(8, 33, 40, 16, "", "upgrade", button -> {
            if (quantityDirty) return;
            viewingUpgrade = !viewingUpgrade;
            viewingCapacity = false;
            clearSelection();
            updateView();
        }, () -> viewingUpgrade);
        upgradeConfirm = iconButton(188, 112, 114, 16, text("upgrade_confirm").getString(), "upgrade_hint", button -> {
            if (editable() && !quantityDirty) sendInventory(Action.UPGRADE, -1, 1);
        }, () -> false);
        iconButton(126, 33, 16, 16, "-", "zoom_out", button -> zoom(1 / 1.2), () -> false);
        iconButton(144, 33, 16, 16, "+", "zoom_in", button -> zoom(1.2), () -> false);
        iconButton(162, 33, 16, 16, "\u25a1", "fit", button -> viewport.fit(), () -> false);
        markerSource = iconButton(188, 33, 114, 16, "", "marker_source",
                button -> { markerSourceIndex = (markerSourceIndex + 1) % markerSources.size(); updateMarkerSource(); },
                () -> markerSourceIndex != 0);
        quantity = addRenderableWidget(new FluxEditBox(font, leftPos + 188, topPos + 78, 86, 18,
                text("quantity"), () -> ACCENT));
        quantity.setMaxLength(78);
        quantity.setFilter(value -> value.chars().allMatch(character -> character >= '0' && character <= '9'));
        quantity.setResponder(value -> { if (!settingQuantity) quantityDirty = true; });
        quantity.setTooltip(Tooltip.create(text("quantity")));
        setQuantity(draft);
        applyQuantity = iconButton(278, 78, 24, 18, "\u2713", "apply_quantity",
                button -> commitQuantity(), () -> false);
        directionButtons.clear();
        for (GridDirection direction : GridDirection.values()) {
            directionButtons.add(iconButton(188 + direction.ordinal() * 28, 108, 24, 18, arrow(direction),
                    "direction." + direction.name().toLowerCase(java.util.Locale.ROOT),
                    button -> selectDirection(direction),
                    () -> selectedCell() != null && (selectedCell().type() == CellPreview.Type.ARROW
                            ? selectedBranch : selectedCell().direction()) == direction));
        }
        addRenderableWidget(new FluxStyleButton(leftPos + 180, topPos + 136, 51, 15, text("inputs"),
                button -> selectInventory(false), () -> ACCENT, () -> !outputInventory, FluxGuiStyle.ButtonStyle.TAB));
        addRenderableWidget(new FluxStyleButton(leftPos + 233, topPos + 136, 51, 15, text("outputs"),
                button -> selectInventory(true), () -> OUTPUT_COLOR, () -> outputInventory, FluxGuiStyle.ButtonStyle.TAB));
        deposit = iconButton(288, 136, 14, 15, "+", "deposit",
                button -> sendInventory(Action.INSERT_INPUT, -1, Math.max(1, menu.getCarried().getCount())), () -> false);
        iconButton(288, 154, 14, 16, "\u2191", "scroll_up", button -> scrollInventory(-1), () -> false);
        iconButton(288, 208, 14, 16, "\u2193", "scroll_down", button -> scrollInventory(1), () -> false);
        updateView();
    }

    private MiniatureIconButton iconButton(int x, int y, int width, int height, String symbol, String key,
                                           net.minecraft.client.gui.components.Button.OnPress action,
                                           java.util.function.BooleanSupplier selected) {
        return addRenderableWidget(new MiniatureIconButton(leftPos + x, topPos + y, width, height,
                symbol, text(key), action, selected));
    }

    private void updateView() {
        if (pendingCommand != 0 && menu.lastAcknowledgedCommandId() >= pendingCommand) pendingCommand = 0;
        if (menu.lastAcknowledgedCommandId() > observedAcknowledgement) {
            observedAcknowledgement = menu.lastAcknowledgedCommandId();
            feedback = menu.lastResult() == Result.SUCCESS ? Component.empty()
                    : Component.translatable(menu.lastResult().translationKey());
        }
        FactorySnapshot current = menu.snapshot();
        if (current != observed) {
            observed = current;
            icons.clear();
            if (current.size() > 0 && viewport.gridSize() != current.size()) {
                if (selectedX == viewport.gridSize()) selectedX = current.size();
                viewport = new MiniatureGridViewport(current.size(), viewport.clip(), 24, 3, 0.25, 2.0);
                viewport.fit();
            }
            if (selectedX > current.size() || selectedY >= current.size()) clearSelection();
            CellPreview cell = selectedCell();
            if (!quantityDirty) {
                if (viewingCapacity) {
                    String capacity = current.configuredInputCapacity(capacityResource).toString();
                    if (!quantity.getValue().equals(capacity)) setQuantity(capacity);
                    if (!current.inputResources().contains(capacityResource)) viewingCapacity = false;
                }
                else if (cell != null) loadQuantity(cell);
            }
            inventoryOffset = Math.min(inventoryOffset, Math.max(0, inventory().size() - 4));
        }
        CellPreview cell = selectedCell();
        updateMarkerSource();
        boolean marker = cell != null && cell.type() == CellPreview.Type.MARKER;
        boolean arrow = cell != null && cell.type() == CellPreview.Type.ARROW;
        quantity.visible = viewingCapacity || marker || arrow;
        quantity.active = quantity.visible && (viewingCapacity ? capacityAvailable() : editable());
        quantity.setTooltip(Tooltip.create(viewingCapacity ? capacityTooltip() : text(arrow ? "split_hint" : "quantity")));
        applyQuantity.visible = quantity.visible;
        applyQuantity.active = quantity.active && validQuantity() != null && quantityDirty;
        resetCapacity.visible = viewingCapacity;
        resetCapacity.active = viewingCapacity && capacityAvailable()
                && (quantityDirty || !current.configuredInputCapacity(capacityResource).equals(current.inputCapacity()));
        for (MiniatureIconButton button : directionButtons) {
            button.visible = marker || arrow;
            button.active = button.visible && editable() && !quantityDirty;
        }
        deposit.active = available() && !outputInventory && !menu.getCarried().isEmpty();
        compile.active = editable() && !quantityDirty;
        autoBalance.active = editable() && !quantityDirty;
        upgradeTab.setSymbol(current.size() + "\u00d7" + current.size() + (current.size() < 11 ? "\u2191" : ""));
        upgradeTab.active = available() && !quantityDirty;
        upgradeConfirm.visible = viewingUpgrade && !markerSource.visible;
        upgradeConfirm.active = upgradeConfirm.visible && editable() && !quantityDirty
                && current.upgrade().status() == com.aquariverr.fluxod.miniature.upgrade.UpgradePreview.Status.AVAILABLE
                && current.upgrade().materials().stream().allMatch(cost -> ownedMaterial(cost) >= cost.amount().intValueExact());
        runSwitch.active = available();
        redstone.active = available();
        runSwitch.setSymbol(current.runtime().enabled() ? "\u2016" : "\u25b6");
        runSwitch.setTooltip(Tooltip.create(text(current.runtime().enabled() ? "pause" : "run")));
        redstone.setSymbol(switch (current.runtime().redstoneMode()) { case IGNORE -> "R"; case HIGH -> "1"; case LOW -> "0"; });
        redstone.setTooltip(Tooltip.create(Component.translatable(current.runtime().redstoneMode().translationKey())));
        outputRowOffset = Math.min(outputRowOffset, Math.max(0, selectedOutputRow().size() - 4));
    }

    @Override
    protected void containerTick() { super.containerTick(); updateView(); }

    private void updateMarkerSource() {
        ItemStack carried = menu.getCarried();
        if (!ItemStack.matches(carried, observedCarried)) {
            observedCarried = carried.copy();
            markerSourceIndex = 0;
            try {
                markerSources = minecraft.level == null ? List.of() : FactoryEditing.markerSources(carried, minecraft.level.registryAccess());
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                markerSources = List.of();
            }
        }
        markerSource.visible = !viewingCapacity && markerSources.size() > 1;
        markerSource.active = markerSource.visible && editable();
        if (markerSource.visible) {
            MarkerSource source = markerSources.get(markerSourceIndex);
            Component label = source.tank() < 0 ? MiniatureResourceRendering.kind(source.resource())
                    : text("marker_tank", MiniatureResourceRendering.kind(source.resource()), source.tank() + 1);
            markerSource.setSymbol(label.getString());
            Component tooltip = text("marker_source_value", label, resourceName(source.resource()))
                    .append("\n").append(text("marker_source_hint"));
            markerSource.setMessage(tooltip);
            markerSource.setTooltip(Tooltip.create(tooltip));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateView();
        renderTransparentBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        renderEditorTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBg(graphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        FluxGuiStyle.renderFrame(graphics, leftPos, topPos, imageWidth, imageHeight, ACCENT);
        graphics.fill(leftPos + 8, topPos + 28, leftPos + 302, topPos + 30, FluxGuiStyle.FRAME);
        graphics.fill(leftPos + 8, topPos + 28, leftPos + 8 + (int) (294L * menu.snapshot().runtime().paidTicks()
                / menu.snapshot().runtime().requiredTicks()), topPos + 30, OUTPUT_COLOR);
        graphics.vLine(leftPos + 183, topPos + 33, topPos + 129, FluxGuiStyle.FRAME);
        graphics.hLine(leftPos + 8, leftPos + 301, topPos + 133, FluxGuiStyle.FRAME);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x;
            int y = topPos + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, FluxGuiStyle.FRAME);
            graphics.fill(x, y, x + 16, y + 16, 0xFF101216);
        }
        drawGrid(graphics, mouseX, mouseY);
        drawInventory(graphics, mouseX, mouseY);
    }

    private void drawGrid(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect clip = viewport.clip();
        graphics.fill((int) clip.x(), (int) clip.y(), (int) clip.right(), (int) clip.bottom(), 0xFF0D1014);
        graphics.enableScissor((int) clip.x(), (int) clip.y(), (int) clip.right(), (int) clip.bottom());
        if (menu.snapshot().size() > 0 && menu.snapshotReady()) {
            var hovered = viewport.hitTest(mouseX, mouseY).orElse(null);
            for (int y = 0; y < viewport.gridSize(); y++) {
                for (int x = 0; x <= viewport.gridSize(); x++) {
                    Rect rect = viewport.cellBounds(x, y);
                    if (rect.right() <= clip.x() || rect.x() >= clip.right()
                            || rect.bottom() <= clip.y() || rect.y() >= clip.bottom()) continue;
                    CellPreview cell = x < viewport.gridSize() ? cellAt(x, y) : null;
                    int color = cell == null ? OUTPUT_COLOR : switch (cell.type()) {
                        case MACHINE -> ACCENT;
                        case MARKER -> MARKER_COLOR;
                        default -> FluxGuiStyle.FRAME;
                    };
                    boolean highlighted = selectedX == x && selectedY == y
                            || hovered != null && hovered.x() == x && hovered.y() == y;
                    var diagnostic = menu.snapshot().compilation().diagnostic();
                    if (diagnostic != null && diagnostic.position().isPresent()
                            && diagnostic.position().get().x() == x && diagnostic.position().get().y() == y) color = 0xFFFF766F;
                    graphics.pose().pushPose();
                    graphics.pose().translate(rect.x(), rect.y(), 0);
                    graphics.pose().scale((float) viewport.scale(), (float) viewport.scale(), 1);
                    graphics.fill(0, 0, 24, 24, highlighted ? FluxGuiStyle.TEXT : color);
                    graphics.fill(1, 1, 23, 23, cell == null ? 0xFF16251E : 0xFF191D23);
                    if (cell == null) {
                        List<ResourceAmount> row = menu.snapshot().compilation().outputRows().getOrDefault(y, List.of());
                        if (row.isEmpty()) graphics.drawCenteredString(font, "\u2192", 12, 8, OUTPUT_COLOR);
                        else {
                            var amount = row.getFirst();
                            renderResource(graphics, amount.resource(), 4, 2);
                            graphics.pose().pushPose();
                            graphics.pose().translate(0, 0, 220);
                            graphics.pose().scale(0.75F, 0.75F, 1);
                            String label = row.size() > 1 ? "+" + (row.size() - 1) : compact(amount.amount());
                            graphics.drawString(font, label, 30 - font.width(label), 22, OUTPUT_COLOR, true);
                            graphics.pose().popPose();
                        }
                    }
                    else if (cell.type() == CellPreview.Type.ARROW) {
                        if (cell.weights().size() == 1) graphics.drawCenteredString(font, arrow(cell.direction()), 12, 8, FluxGuiStyle.TEXT);
                        else for (GridDirection direction : cell.weights().keySet()) graphics.drawCenteredString(font,
                                arrow(direction), 12 + direction.deltaX() * 7, 8 + direction.deltaY() * 7, FluxGuiStyle.TEXT);
                    }
                    else if (cell.resource() != null) {
                        renderResource(graphics, cell.resource(), 4, 2);
                        String amount = cell.type() == CellPreview.Type.MACHINE ? Integer.toString(cell.machineCount()) : compact(cell.quantity());
                        graphics.pose().pushPose();
                        graphics.pose().translate(0, 0, 220);
                        if (cell.type() == CellPreview.Type.MACHINE && !cell.outputDirections().isEmpty()) {
                            for (GridDirection direction : GridDirection.values()) if (cell.outputDirections().containsValue(direction))
                                graphics.drawCenteredString(font, arrow(direction), 12 + direction.deltaX() * 7, 8 + direction.deltaY() * 7, color);
                        } else graphics.drawString(font, arrow(cell.direction()), 1, 1, color, true);
                        graphics.pose().scale(0.75F, 0.75F, 1);
                        graphics.drawString(font, amount, 30 - font.width(amount), 22, color, true);
                        graphics.pose().popPose();
                    }
                    graphics.pose().popPose();
                }
            }
        }
        graphics.disableScissor();
    }

    private void drawInventory(GuiGraphics graphics, int mouseX, int mouseY) {
        List<InventoryEntry> resources = inventory();
        for (int row = 0; row < 4; row++) {
            int y = topPos + 154 + row * 18;
            graphics.fill(leftPos + 180, y - 1, leftPos + 284, y + 17,
                    inside(mouseX, mouseY, 180, 154 + row * 18, 104, 18) ? FluxGuiStyle.PANEL_RAISED : 0xFF111419);
            int index = inventoryOffset + row;
            if (index < resources.size()) {
                InventoryEntry resource = resources.get(index);
                renderResource(graphics, resource.resource(), leftPos + 181, y);
                drawFitted(graphics, resourceName(resource.resource()), leftPos + 201, y, 81, FluxGuiStyle.TEXT);
                drawFitted(graphics, MiniatureResourceRendering.amount(resource.resource(), compactInventory(resource.amount())), leftPos + 201, y + 9,
                        81, outputInventory ? OUTPUT_COLOR : FluxGuiStyle.MUTED);
            }
        }
        if (resources.isEmpty()) graphics.drawCenteredString(font, text("empty"), leftPos + 232, topPos + 185, FluxGuiStyle.MUTED);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawFitted(graphics, title, 8, 12, 132, FluxGuiStyle.TEXT);
        Component status = !menu.snapshotReady() ? text("loading") : pendingCommand != 0 ? text("saving")
                : !feedback.getString().isEmpty() ? feedback : menu.snapshot().size() == 0 ? text("unavailable")
                : menu.snapshot().runtime().enabled() || menu.snapshot().runtime().locked() ? runtimeStatus() : compilationStatus();
        drawFitted(graphics, status, 188, 12, 114,
                !feedback.getString().isEmpty() || menu.snapshot().size() == 0 || menu.snapshot().compilation().diagnostic() != null
                        ? MARKER_COLOR : FluxGuiStyle.MUTED);
        int machines = menu.snapshot().cells().stream().mapToInt(CellPreview::machineCount).sum();
        drawFitted(graphics, text("machines_short", machines), 51, 37, 36, ACCENT);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, FluxGuiStyle.MUTED, false);
        CellPreview cell = selectedCell();
        if (viewingCapacity) {
            drawFitted(graphics, text("cache_title"), 188, 36, 114, ACCENT);
            drawFitted(graphics, resourceName(capacityResource), 188, 51, 114, FluxGuiStyle.TEXT);
            drawFitted(graphics, text("cache_effective", currentCapacity()), 188, 64, 114, ACCENT);
            drawFitted(graphics, text(validQuantity() == null ? "cache_invalid" : "cache_units"), 188, 99, 114,
                    validQuantity() == null ? 0xFFFF8C82 : FluxGuiStyle.MUTED);
        } else if (viewingUpgrade && !markerSource.visible) {
            drawFitted(graphics, text("upgrade_target", Math.min(11, viewport.gridSize() + 2), Math.min(11, viewport.gridSize() + 2)), 188, 36, 114, ACCENT);
            var upgrade = menu.snapshot().upgrade();
            if (upgrade.status() == com.aquariverr.fluxod.miniature.upgrade.UpgradePreview.Status.AVAILABLE) {
                for (int i = 0; i < Math.min(3, upgrade.materials().size()); i++) {
                    var cost = upgrade.materials().get(i);
                    int y = 53 + i * 18;
                    renderResource(graphics, cost.resource(), 188, y);
                    drawFitted(graphics, resourceName(cost.resource()), 208, y, 94, FluxGuiStyle.TEXT);
                    drawFitted(graphics, Component.literal(ownedMaterial(cost) + " / " + cost.amount()), 208, y + 9, 94,
                            ownedMaterial(cost) >= cost.amount().intValueExact() ? OUTPUT_COLOR : MARKER_COLOR);
                }
                if (upgrade.materials().size() > 3) graphics.drawString(font, "+" + (upgrade.materials().size() - 3), 290, 103, MARKER_COLOR, false);
            } else drawWrapped(graphics, text("upgrade." + upgrade.status().name().toLowerCase(java.util.Locale.ROOT)), 188, 56, 114, 52, FluxGuiStyle.MUTED);
        } else if (selectedX == viewport.gridSize() && selectedY >= 0) {
            if (!markerSource.visible) drawFitted(graphics, text("output_row", selectedY + 1), 188, 36, 114, OUTPUT_COLOR);
            drawOutputRow(graphics);
        } else if (cell != null) {
            if (!markerSource.visible) drawFitted(graphics, text("cell", selectedX + 1, selectedY + 1), 188, 36, 114, FluxGuiStyle.MUTED);
            drawFitted(graphics, cell.resource() == null ? text("cell." + cell.type().name().toLowerCase(java.util.Locale.ROOT))
                    : resourceName(cell.resource()), 188, 51, 114, FluxGuiStyle.TEXT);
            if (cell.type() == CellPreview.Type.MARKER) {
                drawFitted(graphics, text("marker_quantity", MiniatureResourceRendering.kind(cell.resource())), 188, 66, 114,
                        validQuantity() == null ? 0xFFFF8C82 : FluxGuiStyle.MUTED);
                graphics.drawString(font, text("direction"), 188, 99, FluxGuiStyle.MUTED, false);
            } else if (cell.type() == CellPreview.Type.ARROW) {
                drawFitted(graphics, text("split_weight", text("direction." + selectedBranch.name().toLowerCase(java.util.Locale.ROOT))),
                        188, 66, 114, validQuantity() == null ? 0xFFFF8C82 : FluxGuiStyle.MUTED);
                drawFitted(graphics, Component.literal(splitSummary(cell)), 188, 99, 114, FluxGuiStyle.MUTED);
            } else if (cell.type() == CellPreview.Type.MACHINE) {
                drawRecipe(graphics, cell);
            }
        } else {
            if (!markerSource.visible) graphics.drawString(font, text("selection"), 188, 36, FluxGuiStyle.MUTED, false);
            var compilation = menu.snapshot().compilation();
            if (compilation.status() == CompilationPreview.Status.READY || menu.snapshot().runtime().locked()) {
                var runtime = menu.snapshot().runtime();
                drawFitted(graphics, text("energy_tick", runtime.energyPerTick()), 188, 55, 114, ACCENT);
                drawFitted(graphics, runtime.locked() ? text("energy_batch", runtime.totalEnergy())
                        : text("base_energy", compilation.energyPerBatch()), 188, 70, 114, FluxGuiStyle.MUTED);
                drawFitted(graphics, text("parallelism", runtime.environmentParallelism(), runtime.batchParallelism()), 188, 85, 114, OUTPUT_COLOR);
                drawFitted(graphics, text("progress", runtime.paidTicks(), runtime.requiredTicks()), 188, 100, 114, FluxGuiStyle.TEXT);
                drawFitted(graphics, runtimeStatus(), 188, 115, 114, FluxGuiStyle.MUTED);
            } else drawWrapped(graphics, compilationStatus(), 188, 55, 114, 65, FluxGuiStyle.MUTED);
        }
    }

    private void renderEditorTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!menu.getCarried().isEmpty()) return;
        List<Component> lines = new ArrayList<>();
        if (viewingCapacity && inside(mouseX, mouseY, 188, 33, 114, 43)) {
            lines.add(capacityTooltip());
        } else if (viewingUpgrade && !markerSource.visible && inside(mouseX, mouseY, 188, 50, 114, 60)) {
            lines.add(text("upgrade_hint"));
            if (menu.snapshot().runtime().locked()) lines.add(Component.translatable(Result.BATCH_LOCKED.translationKey()));
            for (var cost : menu.snapshot().upgrade().materials()) lines.add(resourceName(cost.resource()).copy()
                    .append(" " + ownedMaterial(cost) + " / " + cost.amount()));
        } else if (inside(mouseX, mouseY, 8, 25, 294, 7) || inside(mouseX, mouseY, 188, 8, 114, 19)) {
            var runtime = menu.snapshot().runtime();
            lines.add(feedback.getString().isEmpty() ? runtimeStatus() : feedback);
            lines.add(text("parallelism", runtime.environmentParallelism(), runtime.batchParallelism()));
            lines.add(text("progress", runtime.paidTicks(), runtime.requiredTicks()));
            lines.add(text("energy_tick", runtime.energyPerTick()));
            lines.add(text("working_machines", runtime.energyPerTick().divide(com.aquariverr.fluxod.miniature.model.FactoryRules.ENERGY_PER_MACHINE_TICK)));
            compilationTooltip(lines);
            var error = menu.lastError();
            if (error.hasPosition() && !feedback.getString().isEmpty()) lines.add(text("cell", error.x() + 1, error.y() + 1));
            if (!feedback.getString().isEmpty()) {
                if (error.expected() != null) lines.add(text("expected", error.expected().toString()));
                if (error.actual() != null) lines.add(text("actual", error.actual().toString()));
            }
        } else {
            var hit = viewport.hitTest(mouseX, mouseY).orElse(null);
            if (hit != null && menu.snapshot().size() > 0) {
                if (hit.output()) {
                    lines.add(text("output_row", hit.y() + 1).withStyle(ChatFormatting.GREEN));
                    var row = menu.snapshot().compilation().outputRows().getOrDefault(hit.y(), List.of());
                    if (row.isEmpty()) lines.add(menu.snapshot().compilation().status() == CompilationPreview.Status.READY ? text("empty") : compilationStatus());
                    else {
                        row.stream().limit(4).forEach(amount -> resourceTooltip(lines, amount.resource(), amount.amount()));
                        if (row.size() > 4) lines.add(Component.literal("+" + (row.size() - 4)));
                    }
                }
                else {
                    CellPreview cell = cellAt(hit.x(), hit.y());
                    if (cell.resource() != null) {
                        resourceTooltip(lines, cell.resource(), cell.type() == CellPreview.Type.MACHINE
                                ? cell.machineCount() : cell.quantity());
                    }
                    if (cell.type() != CellPreview.Type.EMPTY) {
                        if (cell.type() == CellPreview.Type.ARROW) {
                            lines.add(Component.literal(splitSummary(cell)));
                            lines.add(text("split_hint"));
                        }
                        if (cell.type() != CellPreview.Type.MACHINE || cell.outputDirections().isEmpty())
                            lines.add(text(cell.type() == CellPreview.Type.MACHINE ? "output_direction" : "direction")
                                    .append(": ").append(text("direction." + cell.direction().name().toLowerCase(java.util.Locale.ROOT))));
                        lines.add(text(cell.type() == CellPreview.Type.MACHINE ? "machine_controls" : "rotate_hint"));
                        if (cell.type() == CellPreview.Type.MACHINE && !cell.outputDirections().isEmpty())
                            cell.outputDirections().forEach((resource, direction) -> lines.add(resourceName(resource).copy().append(" " + arrow(direction))));
                    }
                    var diagnostic = menu.snapshot().compilation().diagnostic();
                    if (diagnostic != null && diagnostic.position().isPresent() && diagnostic.position().get().x() == hit.x()
                            && diagnostic.position().get().y() == hit.y()) compilationTooltip(lines);
                }
            } else if (inside(mouseX, mouseY, 180, 154, 104, 72)) {
                int index = inventoryOffset + (mouseY - topPos - 154) / 18;
                if (index < inventory().size()) {
                    InventoryEntry amount = inventory().get(index);
                    resourceTooltip(lines, amount.resource(), amount.amount());
                    if (!outputInventory) {
                        lines.add(text("cache_configure_hint"));
                        lines.add(text("cache_effective", menu.snapshot().effectiveInputCapacity(amount.resource())));
                    }
                }
            } else if (selectedX == menu.snapshot().size() && inside(mouseX, mouseY, 188, 52, 114, 72)) {
                int index = outputRowOffset + (mouseY - topPos - 52) / 18;
                if (index < selectedOutputRow().size()) {
                    var amount = selectedOutputRow().get(index);
                    resourceTooltip(lines, amount.resource(), amount.amount());
                }
            } else if (inside(mouseX, mouseY, 188, 62, 114, 68) && selectedRecipe() != null) {
                recipeTooltip(lines, mouseX - leftPos, mouseY - topPos);
            } else if (inside(mouseX, mouseY, 188, 47, 114, 15) && selectedCell() != null && selectedCell().resource() != null) lines.add(resourceName(selectedCell().resource()));
        }
        if (!lines.isEmpty()) graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateView();
        if (pendingCommand != 0) return true;
        if (viewport.clip().contains(mouseX, mouseY)) {
            if (viewingCapacity && quantityDirty) return true;
            quantity.setFocused(false);
            if (button == 2) { panning = true; return true; }
            var hit = viewport.hitTest(mouseX, mouseY).orElse(null);
            if (hit == null || !available()) return true;
            viewingUpgrade = false;
            viewingCapacity = false;
            selectCell(hit.x(), hit.y());
            var intent = hit.output() ? MiniatureGridInteraction.decideOutput(button)
                    : MiniatureGridInteraction.decide(cellAt(hit.x(), hit.y()).type(), button, menu.getCarried().isEmpty(), hasShiftDown());
            if (intent.kind() == MiniatureGridInteraction.Kind.COMMAND && editable()) {
                int count = intent.action() == Action.INSERT_MACHINE ? Math.min(64, menu.getCarried().getCount()) : 1;
                sendGrid(intent.action(), BigInteger.valueOf(count), GridDirection.RIGHT);
            }
            updateView();
            return true;
        }
        if (inside(mouseX, mouseY, 180, 154, 104, 72)) {
            if (button != 0 && button != 1 || !available()) return true;
            int index = inventoryOffset + (int) (mouseY - topPos - 154) / 18;
            if (!outputInventory && button == 1) {
                if (index < inventory().size()) openCapacity(inventory().get(index).resource());
                return true;
            }
            if (!outputInventory && !menu.getCarried().isEmpty()) sendInventory(Action.INSERT_INPUT, -1, hasShiftDown() ? 1 : menu.getCarried().getCount());
            else {
                if (index < inventory().size() && inventory().get(index).amount().signum() > 0)
                    sendInventory(outputInventory ? Action.EXTRACT_OUTPUT : Action.EXTRACT_INPUT, index, button == 1 || hasShiftDown() ? 1 : 99);
            }
            return true;
        }
        if (button == 1 && inside(mouseX, mouseY, 260, 76, 36, 54) && selectedRecipe() != null
                && menu.getCarried().isEmpty() && editable() && !quantityDirty) {
            int index = recipeOutputOffset + (int) (mouseY - topPos - 76) / 18 * 2 + (int) (mouseX - leftPos - 260) / 18;
            if (index < selectedRecipe().outputs().size()) {
                var resource = selectedRecipe().outputs().get(index).resource();
                pendingCommand = nextCommandId++;
                feedback = Component.empty();
                PacketDistributor.sendToServer(new MiniatureFactoryCommandPayload(menu.containerId, menu.sessionId(),
                        menu.getPluginPos(), menu.revision(), pendingCommand, Action.SET_PRODUCT_DIRECTION, selectedX, selectedY,
                        BigInteger.ONE, selectedCell().outputDirection(resource).clockwise(), menu.snapshotId(), index));
            }
            return true;
        }
        if (button == 0 && inside(mouseX, mouseY, 188, 62, 114, 12) && selectedRecipe() != null
                && menu.getCarried().isEmpty() && editable()) {
            sendGrid(Action.CYCLE_RECIPE, BigInteger.ONE, GridDirection.RIGHT);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (panning && button == 2) { viewport.panBy(dragX, dragY); return true; }
        return pendingCommand != 0 || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2 && panning) { panning = false; return true; }
        return pendingCommand != 0 || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (selectedX == menu.snapshot().size() && inside(mouseX, mouseY, 188, 52, 114, 76)) {
            outputRowOffset = Math.clamp(outputRowOffset + (scrollY > 0 ? -1 : 1), 0, Math.max(0, selectedOutputRow().size() - 4));
            return true;
        }
        if (inside(mouseX, mouseY, 260, 76, 36, 54) && selectedRecipe() != null) {
            recipeOutputOffset = Math.clamp(recipeOutputOffset + (scrollY > 0 ? -2 : 2), 0,
                    Math.max(0, selectedRecipe().outputs().size() - 6));
            return true;
        }
        if (viewport.clip().contains(mouseX, mouseY)) { viewport.zoomAt(mouseX, mouseY, Math.pow(1.2, scrollY)); return true; }
        if (inside(mouseX, mouseY, 180, 154, 104, 72)) { scrollInventory(scrollY > 0 ? -1 : 1); return true; }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (quantity.isFocused() && quantity.visible) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (viewingCapacity && quantityDirty) {
                    quantityDirty = false;
                    setQuantity(menu.snapshot().configuredInputCapacity(capacityResource).toString());
                }
                quantity.setFocused(false);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitQuantity(); return true; }
            if (quantity.keyPressed(key, scanCode, modifiers)) return true;
            if (key != GLFW.GLFW_KEY_TAB) return true;
        }
        if (pendingCommand != 0 && key != GLFW.GLFW_KEY_ESCAPE) return true;
        if (key == GLFW.GLFW_KEY_DELETE && editable() && selectedCell() != null && selectedCell().type() == CellPreview.Type.MACHINE) {
            sendGrid(Action.EXTRACT_MACHINE, BigInteger.valueOf(64), GridDirection.RIGHT);
            return true;
        }
        if (key == GLFW.GLFW_KEY_DELETE && editable() && selectedCell() != null
                && (selectedCell().type() == CellPreview.Type.MARKER || selectedCell().type() == CellPreview.Type.ARROW)) {
            sendGrid(Action.CLEAR_CONFIGURATION, BigInteger.ONE, GridDirection.RIGHT);
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void sendGrid(Action action, BigInteger amount, GridDirection direction) {
        if (!editable() || selectedCell() == null) return;
        pendingCommand = nextCommandId++;
        feedback = Component.empty();
        int tank = -1;
        if (action == Action.SET_MARKER_FROM_CARRIED && !markerSources.isEmpty()) {
            MarkerSource source = markerSources.get(markerSourceIndex);
            action = source.action();
            tank = source.tank();
        }
        PacketDistributor.sendToServer(new MiniatureFactoryCommandPayload(menu.containerId, menu.sessionId(),
                menu.getPluginPos(), menu.revision(), pendingCommand, action, selectedX, selectedY, amount, direction,
                action == Action.CYCLE_RECIPE ? menu.snapshotId() : MiniatureFactoryCommandPayload.NO_SNAPSHOT, tank));
    }

    /** The recipe browser temporarily replaces this screen while retaining the same live menu. */
    public static Optional<MiniatureUniverseScreen> forMenu(MiniatureUniverseMenu menu) {
        MiniatureUniverseScreen screen = lastEditor.get();
        var player = Minecraft.getInstance().player;
        return screen != null && screen.menu == menu && player != null && player.containerMenu == menu
                ? Optional.of(screen) : Optional.empty();
    }

    /** Integer drop areas lie entirely inside both the drawn cell and the current viewport. */
    public List<GhostMarkerTarget> ghostMarkerTargets() {
        if (viewport == null || minecraft.screen != this) return List.of();
        updateView();
        if (!editable() || quantityDirty) return List.of();
        List<GhostMarkerTarget> targets = new ArrayList<>();
        Rect clip = viewport.clip();
        for (int y = 0; y < menu.snapshot().size(); y++) {
            for (int x = 0; x < menu.snapshot().size(); x++) {
                var type = cellAt(x, y).type();
                if (type != CellPreview.Type.EMPTY && type != CellPreview.Type.MARKER) continue;
                Rect bounds = viewport.cellBounds(x, y);
                int left = (int) Math.ceil(Math.max(clip.x(), bounds.x()));
                int top = (int) Math.ceil(Math.max(clip.y(), bounds.y()));
                // Rect2i.contains includes its right/bottom edge; keep that edge inside the half-open viewport.
                int right = (int) Math.ceil(Math.min(clip.right(), bounds.right())) - 1;
                int bottom = (int) Math.ceil(Math.min(clip.bottom(), bounds.bottom())) - 1;
                if (right > left && bottom > top) targets.add(new GhostMarkerTarget(new GridPosition(x, y),
                        new Rect2i(left, top, right - left, bottom - top), menu.snapshotId()));
            }
        }
        return List.copyOf(targets);
    }

    public boolean placeGhostMarker(GhostMarkerTarget target, ResourceKey resource) {
        if (target == null || resource == null || ghostMarkerTargets().stream().noneMatch(current ->
                current.position().equals(target.position()) && current.snapshotId().equals(target.snapshotId())
                        && sameArea(current.area(), target.area()))) return false;
        selectCell(target.position().x(), target.position().y());
        return sendBrowserSelection(Action.SET_GHOST_MARKER, resource, null);
    }

    public record GhostMarkerTarget(GridPosition position, Rect2i area, UUID snapshotId) {}

    private static boolean sameArea(Rect2i first, Rect2i second) {
        return second != null && first.getX() == second.getX() && first.getY() == second.getY()
                && first.getWidth() == second.getWidth() && first.getHeight() == second.getHeight();
    }

    /** Returns a user-facing reason without changing the selected cell or any inventory. */
    public Component recipeTransferProblem() {
        if (viewport == null) return text("jei.unavailable");
        updateView();
        if (!editable()) return text("jei.unavailable");
        if (quantityDirty) return text("jei.quantity_draft");
        CellPreview cell = selectedCell();
        return cell == null || cell.type() != CellPreview.Type.MACHINE ? text("jei.select_machine") : null;
    }

    public ResourceKey recipeTransferMachine() {
        return recipeTransferProblem() == null ? selectedCell().resource() : null;
    }

    /** Lets the recipe browser disable its button for an intent that exceeds the shared wire budget. */
    public Component recipeTransferProblem(RecipeReference reference) {
        Component problem = recipeTransferProblem();
        if (problem != null) return problem;
        try {
            validateCommand(browserSelection(Action.SELECT_RECIPE, null, reference));
            return null;
        } catch (RuntimeException invalid) {
            return text("jei.invalid_ingredient");
        }
    }

    public boolean selectRecipe(RecipeReference reference) {
        if (reference == null || recipeTransferProblem() != null) return false;
        return sendBrowserSelection(Action.SELECT_RECIPE, null, reference);
    }

    private boolean sendBrowserSelection(Action action, ResourceKey marker, RecipeReference recipe) {
        if (!editable() || quantityDirty || selectedCell() == null) return false;
        try {
            MiniatureFactoryCommandPayload payload = browserSelection(action, marker, recipe);
            // Validate all bindings together before enqueueing a packet or consuming a command ID.
            validateCommand(payload);
            PacketDistributor.sendToServer(payload);
            pendingCommand = nextCommandId++;
            feedback = Component.empty();
            return true;
        } catch (RuntimeException invalid) {
            feedback = text("jei.invalid_ingredient");
            return false;
        }
    }

    private MiniatureFactoryCommandPayload browserSelection(Action action, ResourceKey marker, RecipeReference recipe) {
        return new MiniatureFactoryCommandPayload(menu.containerId, menu.sessionId(),
                menu.getPluginPos(), menu.revision(), nextCommandId, action, selectedX, selectedY, BigInteger.ONE,
                GridDirection.RIGHT, menu.snapshotId(), -1, marker, recipe);
    }

    private void validateCommand(MiniatureFactoryCommandPayload payload) {
        if (minecraft.level == null) throw new IllegalStateException("No client registry access");
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(256, MiniatureFactoryCommandPayload.MAX_PAYLOAD_BYTES),
                minecraft.level.registryAccess(), ConnectionType.NEOFORGE);
        try {
            MiniatureFactoryCommandPayload.STREAM_CODEC.encode(buffer, payload);
        } finally {
            buffer.release();
        }
    }

    private void sendInventory(Action action, int index, int count) {
        if (!available()) return;
        pendingCommand = nextCommandId++;
        feedback = Component.empty();
        PacketDistributor.sendToServer(new MiniatureFactoryCommandPayload(menu.containerId, menu.sessionId(),
                menu.getPluginPos(), menu.revision(), pendingCommand, action, 0, 0, BigInteger.valueOf(count),
                GridDirection.RIGHT, menu.snapshotId(), index));
    }

    private void sendControl(Action action) {
        if (!available()) return;
        pendingCommand = nextCommandId++;
        feedback = Component.empty();
        PacketDistributor.sendToServer(new MiniatureFactoryCommandPayload(menu.containerId, menu.sessionId(),
                menu.getPluginPos(), menu.revision(), pendingCommand, action, 0, 0, BigInteger.ONE,
                GridDirection.RIGHT, MiniatureFactoryCommandPayload.NO_SNAPSHOT, -1));
    }

    private void sendCompile() {
        if (!editable() || quantityDirty) return;
        sendInventory(Action.COMPILE, -1, 1);
    }

    private int ownedMaterial(ResourceAmount cost) {
        if (minecraft.player == null || minecraft.level == null) return 0;
        try {
            ItemStack prototype = com.aquariverr.fluxod.miniature.storage.FactoryItemResources.itemOf(cost.resource(), minecraft.level.registryAccess());
            return minecraft.player.getInventory().items.stream().filter(stack -> ItemStack.isSameItemSameComponents(stack, prototype))
                    .mapToInt(ItemStack::getCount).sum();
        } catch (IllegalArgumentException exception) { return 0; }
    }

    private void commitQuantity() {
        if (viewingCapacity) {
            BigInteger value = validQuantity();
            if (!capacityAvailable() || !quantityDirty || value == null) return;
            var payload = new MiniatureFactoryCommandPayload(menu.containerId, menu.sessionId(), menu.getPluginPos(),
                    menu.revision(), nextCommandId, Action.SET_INPUT_CAPACITY, 0, 0, value, GridDirection.RIGHT,
                    menu.snapshotId(), menu.snapshot().inputResources().indexOf(capacityResource));
            validateCommand(payload);
            PacketDistributor.sendToServer(payload);
            pendingCommand = nextCommandId++;
            feedback = Component.empty();
            quantityDirty = false;
            return;
        }
        CellPreview cell = selectedCell();
        if (!editable() || cell == null || (cell.type() != CellPreview.Type.MARKER && cell.type() != CellPreview.Type.ARROW)) return;
        BigInteger value = validQuantity();
        if (value == null || !quantityDirty) return;
        sendGrid(cell.type() == CellPreview.Type.ARROW ? Action.SET_ARROW_WEIGHT : Action.SET_MARKER_QUANTITY,
                value, cell.type() == CellPreview.Type.ARROW ? selectedBranch : GridDirection.RIGHT);
        if (pendingCommand != 0) quantityDirty = false;
    }

    private BigInteger validQuantity() {
        try {
            BigInteger value = new BigInteger(quantity.getValue());
            if (viewingCapacity) return FactoryLimits.requireInventoryQuantity(FactoryLimits.requirePositiveQuantity(value));
            CellPreview cell = selectedCell();
            if (cell != null && cell.type() == CellPreview.Type.ARROW) {
                FactoryLimits.requireQuantity(value);
                if (value.compareTo(BigInteger.valueOf(com.aquariverr.fluxod.miniature.model.FactoryCell.Arrow.MAX_WEIGHT)) > 0
                        || value.signum() == 0 && cell.weights().size() == 1 && cell.weights().containsKey(selectedBranch)) return null;
                return value;
            }
            return FactoryLimits.requirePositiveQuantity(value);
        }
        catch (IllegalArgumentException exception) { return null; }
    }

    private void selectCell(int x, int y) {
        if (selectedX == x && selectedY == y) return;
        selectedX = x;
        selectedY = y;
        recipeOutputOffset = 0;
        outputRowOffset = 0;
        quantityDirty = false;
        feedback = Component.empty();
        CellPreview cell = selectedCell();
        selectedBranch = cell != null ? cell.direction() : GridDirection.RIGHT;
        loadQuantity(cell);
    }

    private void selectDirection(GridDirection direction) {
        if (quantityDirty || !editable()) return;
        CellPreview cell = selectedCell();
        if (cell != null && cell.type() == CellPreview.Type.ARROW) {
            selectedBranch = direction;
            loadQuantity(cell);
        } else sendGrid(Action.SET_DIRECTION, BigInteger.ONE, direction);
    }

    private void loadQuantity(CellPreview cell) {
        setQuantity(cell == null ? "" : cell.type() == CellPreview.Type.MARKER ? cell.quantity().toString()
                : cell.type() == CellPreview.Type.ARROW ? Integer.toString(cell.weights().getOrDefault(selectedBranch, 0)) : "");
    }

    private static String splitSummary(CellPreview cell) {
        return java.util.Arrays.stream(GridDirection.values()).map(direction -> arrow(direction)
                + cell.weights().getOrDefault(direction, 0)).collect(java.util.stream.Collectors.joining(" "));
    }

    private void clearSelection() { selectedX = -1; selectedY = -1; quantityDirty = false; }
    private void openCapacity(ResourceKey resource) {
        if (quantityDirty) return;
        viewingCapacity = true;
        capacityResource = resource;
        viewingUpgrade = false;
        clearSelection();
        quantity.setFocused(false);
        setQuantity(menu.snapshot().configuredInputCapacity(resource).toString());
        updateView();
    }
    private boolean capacityAvailable() { return available() && menu.snapshot().inputResources().contains(capacityResource); }
    private BigInteger currentCapacity() { return menu.snapshot().effectiveInputCapacity(capacityResource); }
    private Component capacityTooltip() {
        return resourceName(capacityResource).copy().append("\n").append(text("cache_hint"))
                .append("\n").append(text("cache_configured", menu.snapshot().configuredInputCapacity(capacityResource)))
                .append("\n").append(text("cache_default", menu.snapshot().inputCapacity()))
                .append("\n").append(text("cache_effective", currentCapacity()))
                .append("\n").append(text("cache_server_limit", menu.snapshot().inputCapacityLimit()))
                .append("\n").append(text("cache_preserve"));
    }
    private void setQuantity(String value) { settingQuantity = true; quantity.setValue(value); settingQuantity = false; }
    private boolean available() { return menu.snapshotReady() && menu.snapshot().size() > 0 && pendingCommand == 0; }
    private boolean editable() { return available() && !menu.snapshot().runtime().locked(); }
    private Component runtimeStatus() { return Component.translatable(menu.snapshot().runtime().status().translationKey()); }
    private CellPreview selectedCell() {
        return selectedX >= 0 && selectedX < menu.snapshot().size() && selectedY >= 0 && selectedY < menu.snapshot().size() ? cellAt(selectedX, selectedY) : null;
    }
    private CellPreview cellAt(int x, int y) { return menu.snapshot().cells().get(y * menu.snapshot().size() + x); }
    private RecipePreview selectedRecipe() {
        return selectedCell() == null ? null : menu.snapshot().recipes().get(selectedY * menu.snapshot().size() + selectedX);
    }

    private void drawRecipe(GuiGraphics graphics, CellPreview cell) {
        RecipePreview recipe = selectedRecipe();
        if (recipe == null) return;
        boolean ready = recipe.status() == RecipePreview.Status.READY || recipe.status() == RecipePreview.Status.AUTOMATIC || recipe.status() == RecipePreview.Status.COMPILED;
        drawFitted(graphics, recipe.status() == RecipePreview.Status.COMPILED ? text("compiled_recipe_summary", recipe.normalization(), cell.machineCount())
                : ready ? text("recipe_summary", recipe.selectedIndex() + 1, recipe.candidateCount(),
                        recipe.normalization(), cell.machineCount()) : text("recipe." + recipe.status().name().toLowerCase(java.util.Locale.ROOT)),
                188, 64, 114, ready ? ACCENT : MARKER_COLOR);
        if (ready) {
            for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) {
                graphics.fill(188 + x * 18, 76 + y * 18, 205 + x * 18, 93 + y * 18, 0xFF111419);
            }
            for (var binding : recipe.reference().bindings()) {
                int x = binding.slot() % recipe.reference().width();
                int y = binding.slot() / recipe.reference().width();
                renderResource(graphics, binding.resource(), 188 + x * 18, 76 + y * 18);
            }
            graphics.drawString(font, "\u2192", 245, 98, OUTPUT_COLOR, false);
            recipeOutputOffset = Math.clamp(recipeOutputOffset, 0, Math.max(0, recipe.outputs().size() - 6));
            for (int index = 0; index < Math.min(6, recipe.outputs().size() - recipeOutputOffset); index++) {
                ResourceAmount amount = recipe.outputs().get(recipeOutputOffset + index);
                int x = 260 + index % 2 * 18;
                int y = 76 + index / 2 * 18;
                renderResource(graphics, amount.resource(), x, y);
                graphics.drawString(font, arrow(cell.outputDirection(amount.resource())), x, y, OUTPUT_COLOR, true);
                String count = compact(amount.amount());
                graphics.drawString(font, count, x + 17 - font.width(count), y + 9, 0xFFFFFFFF, true);
            }
            if (recipe.outputs().size() > 6) graphics.drawString(font, "+", 297, 118, OUTPUT_COLOR, false);
        } else {
            for (int index = 0; index < recipe.missing().size(); index++) {
                renderResource(graphics, recipe.missing().get(index), 188 + index % 6 * 18, 80 + index / 6 * 18);
            }
        }
    }

    private void recipeTooltip(List<Component> lines, int x, int y) {
        RecipePreview recipe = selectedRecipe();
        lines.add(text("recipe_cycle_hint"));
        lines.add(text("recipe." + recipe.status().name().toLowerCase(java.util.Locale.ROOT)));
        if (recipe.reference() != null) lines.add(Component.literal(recipe.reference().recipeId()));
        if (!recipe.inputs().isEmpty()) {
            if (x < 242 && y >= 76) {
                int column = (x - 188) / 18;
                int row = (y - 76) / 18;
                for (var binding : recipe.reference().bindings()) {
                    if (binding.slot() % recipe.reference().width() == column && binding.slot() / recipe.reference().width() == row) {
                        lines.add(resourceName(binding.resource()));
                        lines.add(MiniatureResourceRendering.kind(binding.resource()));
                        BigInteger amount = recipe.inputs().stream().filter(input -> input.resource().equals(binding.resource()))
                                .map(ResourceAmount::amount).reduce(BigInteger.ZERO, BigInteger::add);
                        lines.add(text(recipe.status() == RecipePreview.Status.COMPILED ? "recipe_input" : "standard_recipe_input",
                                MiniatureResourceRendering.amount(binding.resource(), amount)));
                    }
                }
            } else if (x >= 260 && x < 296 && y >= 76) {
                int index = recipeOutputOffset + (y - 76) / 18 * 2 + (x - 260) / 18;
                if (index < recipe.outputs().size()) {
                    var amount = recipe.outputs().get(index);
                    resourceTooltip(lines, amount.resource(), amount.amount());
                    lines.add(text("output_direction").append(": ").append(text("direction." + selectedCell().outputDirection(amount.resource()).name().toLowerCase(java.util.Locale.ROOT))));
                    lines.add(text("product_direction_hint"));
                }
            } else {
                lines.add(text("normalization", recipe.normalization()));
                lines.add(text("machines", selectedCell().machineCount()));
            }
        } else if (!recipe.missing().isEmpty()) {
            lines.add(text("missing_examples"));
            for (ResourceKey missing : recipe.missing()) lines.add(resourceName(missing));
        }
    }
    private record InventoryEntry(ResourceKey resource, BigInteger amount) {}
    private List<InventoryEntry> inventory() {
        if (outputInventory) return menu.snapshot().outputs().stream().map(value -> new InventoryEntry(value.resource(), value.amount())).toList();
        var rows = new java.util.LinkedHashMap<ResourceKey, BigInteger>();
        menu.snapshot().inputs().forEach(value -> rows.put(value.resource(), value.amount()));
        menu.snapshot().inputCapacities().forEach(value -> rows.putIfAbsent(value.resource(), BigInteger.ZERO));
        return rows.entrySet().stream().map(value -> new InventoryEntry(value.getKey(), value.getValue())).toList();
    }

    private List<ResourceAmount> selectedOutputRow() { return menu.snapshot().compilation().outputRows().getOrDefault(selectedY, List.of()); }

    private Component compilationStatus() {
        var preview = menu.snapshot().compilation();
        return preview.diagnostic() != null ? text("compile_error." + preview.diagnostic().code().name().toLowerCase(java.util.Locale.ROOT))
                : text(preview.status() == CompilationPreview.Status.READY ? "compiled" : "not_compiled");
    }

    private void compilationTooltip(List<Component> lines) {
        var preview = menu.snapshot().compilation();
        lines.add(compilationStatus());
        var diagnostic = preview.diagnostic();
        if (diagnostic != null) {
            diagnostic.position().ifPresent(position -> lines.add(text("cell", position.x() + 1, position.y() + 1)));
            diagnostic.resource().ifPresent(resource -> lines.add(resourceName(resource)));
            diagnostic.expected().ifPresent(amount -> lines.add(text("expected", amount)));
            diagnostic.actual().ifPresent(amount -> lines.add(text("actual", amount)));
        } else if (preview.status() == CompilationPreview.Status.READY) {
            lines.add(text("energy_tick", preview.energyPerTick()));
            lines.add(text("base_energy", preview.energyPerBatch()));
            lines.add(text("base_duration", preview.requiredTicks()));
            lines.add(text("inputs"));
            preview.inputs().stream().limit(6).forEach(amount -> resourceTooltip(lines, amount.resource(), amount.amount()));
            if (preview.inputs().size() > 6) lines.add(Component.literal("+" + (preview.inputs().size() - 6)));
        }
    }

    private void drawOutputRow(GuiGraphics graphics) {
        var resources = selectedOutputRow();
        if (resources.isEmpty()) {
            drawWrapped(graphics, menu.snapshot().compilation().status() == CompilationPreview.Status.READY ? text("empty") : compilationStatus(),
                    188, 55, 114, 64, FluxGuiStyle.MUTED);
            return;
        }
        for (int row = 0; row < Math.min(4, resources.size() - outputRowOffset); row++) {
            var amount = resources.get(outputRowOffset + row);
            int y = 52 + row * 18;
            renderResource(graphics, amount.resource(), 188, y);
            drawFitted(graphics, resourceName(amount.resource()), 208, y, 94, FluxGuiStyle.TEXT);
            drawFitted(graphics, Component.literal(compactInventory(amount.amount())), 208, y + 9, 94, OUTPUT_COLOR);
        }
        if (resources.size() > 4) drawFitted(graphics, Component.literal((outputRowOffset + 1) + "-" + Math.min(resources.size(), outputRowOffset + 4)
                + " / " + resources.size()), 188, 125, 114, FluxGuiStyle.MUTED);
    }
    private void selectInventory(boolean outputs) { outputInventory = outputs; inventoryOffset = 0; }
    private void scrollInventory(int delta) { inventoryOffset = Math.clamp(inventoryOffset + delta, 0, Math.max(0, inventory().size() - 4)); }
    private void zoom(double factor) { viewport.zoomAt(viewport.clip().x() + 86, viewport.clip().y() + 40.5, factor); }
    private boolean inside(double x, double y, int localX, int localY, int width, int height) {
        return x >= leftPos + localX && x < leftPos + localX + width && y >= topPos + localY && y < topPos + localY + height;
    }

    private MiniatureResourceRendering.Visual visual(ResourceKey key) {
        return icons.computeIfAbsent(key, MiniatureResourceRendering::resolve);
    }

    private void renderResource(GuiGraphics graphics, ResourceKey key, int x, int y) {
        visual(key).render(graphics, x, y);
    }

    private Component resourceName(ResourceKey key) {
        return visual(key).name();
    }

    private void resourceTooltip(List<Component> lines, ResourceKey key, Object amount) {
        lines.add(resourceName(key));
        lines.add(MiniatureResourceRendering.kind(key));
        lines.add(MiniatureResourceRendering.amount(key, amount));
        if (key.kind() != ResourceKind.ITEM) lines.add(Component.literal(key.registryId()).withStyle(ChatFormatting.DARK_GRAY));
    }

    private void drawFitted(GuiGraphics graphics, Component component, int x, int y, int width, int color) {
        String value = component.getString();
        graphics.drawString(font, font.width(value) <= width ? value : font.plainSubstrByWidth(value, width - font.width("...")) + "...", x, y, color, false);
    }

    private void drawWrapped(GuiGraphics graphics, Component component, int x, int y, int width, int height, int color) {
        var lines = font.split(component, width);
        for (int line = 0; line < Math.min(lines.size(), height / font.lineHeight); line++) graphics.drawString(font, lines.get(line), x, y + line * font.lineHeight, color, false);
    }

    private static String compact(BigInteger value) {
        String digits = value.toString();
        return digits.length() <= 3 ? digits : digits.charAt(0) + "e" + (digits.length() - 1);
    }

    private static String compactInventory(BigInteger value) {
        String digits = value.toString();
        return digits.length() <= 15 ? digits : digits.charAt(0) + "." + digits.substring(1, 4) + "e" + (digits.length() - 1);
    }

    private static String arrow(GridDirection direction) {
        return switch (direction) { case RIGHT -> "\u2192"; case DOWN -> "\u2193"; case LEFT -> "\u2190"; case UP -> "\u2191"; };
    }

    private static net.minecraft.network.chat.MutableComponent text(String key, Object... arguments) {
        return Component.translatable("screen.flux_overdrive.miniature_universe." + key, arguments);
    }
}

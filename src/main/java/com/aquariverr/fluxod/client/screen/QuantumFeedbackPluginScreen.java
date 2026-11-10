package com.aquariverr.fluxod.client.screen;

import com.aquariverr.fluxod.block.entity.QuantumFeedbackCurve;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackMetric;
import com.aquariverr.fluxod.client.gui.FluxEditBox;
import com.aquariverr.fluxod.client.gui.FluxGuiStyle;
import com.aquariverr.fluxod.client.gui.FluxStyleButton;
import com.aquariverr.fluxod.client.gui.QuantumGuiLayout;
import com.aquariverr.fluxod.menu.QuantumFeedbackPluginMenu;
import com.aquariverr.fluxod.network.UpdateFeedbackCurvePayload;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuantumFeedbackPluginScreen extends AbstractContainerScreen<QuantumFeedbackPluginMenu> {

    private static final int CHART_X = 42;
    private static final int CHART_Y = 58;
    private static final int CHART_WIDTH = 250;
    private static final int CHART_HEIGHT = 146;
    private static final int HANDLE_RADIUS = 3;
    private static final float LINE_WIDTH = 0.75F;

    private final List<QuantumFeedbackCurve.Point> draftPoints = new ArrayList<>();
    private QuantumFeedbackCurve serverCurve;
    private int selectedPoint;
    private int draggingPoint = -1;
    private boolean draftDirty;
    private float uiScale = 1.0F;

    private EditBox thresholdInput;
    private EditBox signalInput;
    private Button removeButton;
    private Button saveButton;

    public QuantumFeedbackPluginScreen(QuantumFeedbackPluginMenu menu, Inventory inventory,
                                       Component title) {
        super(menu, inventory, title);
        imageWidth = QuantumGuiLayout.REFERENCE_WIDTH;
        imageHeight = 294;
        titleLabelX = 14;
        titleLabelY = 11;
        inventoryLabelY = 10_000;
        serverCurve = menu.getCurve();
        draftPoints.addAll(serverCurve.points());
    }

    @Override
    protected void init() {
        super.init();
        uiScale = QuantumGuiLayout.scale(width, height);
        leftPos = QuantumGuiLayout.centeredPosition(width, imageWidth, uiScale);
        topPos = QuantumGuiLayout.centeredPosition(height, imageHeight, uiScale);
        addPresetButton(20, 218, 52, "ascending", Preset.ASCENDING);
        addPresetButton(76, 218, 52, "descending", Preset.DESCENDING);
        addPresetButton(132, 218, 52, "high", Preset.HIGH);
        addPresetButton(188, 218, 52, "low", Preset.LOW);
        addPresetButton(244, 218, 56, "window", Preset.WINDOW);

        addRenderableWidget(new FluxStyleButton(leftPos + 326, topPos + 117, 38, 20,
                Component.literal("+"), ignored -> addPoint(), this::accentColor, () -> false,
                FluxGuiStyle.ButtonStyle.ACTION));
        removeButton = addRenderableWidget(new FluxStyleButton(leftPos + 370, topPos + 117, 38, 20,
                Component.literal("x"), ignored -> removePoint(), this::accentColor, () -> false,
                FluxGuiStyle.ButtonStyle.ACTION));

        thresholdInput = new FluxEditBox(font, leftPos + 326, topPos + 161, 82, 20,
                Component.translatable("screen.flux_overdrive.quantum_feedback.threshold"), this::accentColor);
        thresholdInput.setMaxLength(12);
        thresholdInput.setFilter(QuantumFeedbackPluginScreen::isDecimalInput);
        addRenderableWidget(thresholdInput);

        signalInput = new FluxEditBox(font, leftPos + 326, topPos + 204, 82, 20,
                Component.translatable("screen.flux_overdrive.quantum_feedback.signal"), this::accentColor);
        signalInput.setMaxLength(2);
        signalInput.setFilter(QuantumFeedbackPluginScreen::isIntegerInput);
        addRenderableWidget(signalInput);

        saveButton = addRenderableWidget(new FluxStyleButton(leftPos + 326, topPos + 248, 82, 22,
                Component.translatable("screen.flux_overdrive.quantum_feedback.save"),
                ignored -> saveDraft(), this::accentColor, () -> false,
                FluxGuiStyle.ButtonStyle.ACTION));
        addRenderableWidget(new FluxStyleButton(leftPos + 326, topPos + 272, 82, 16,
                Component.translatable("screen.flux_overdrive.quantum_feedback.reset"),
                ignored -> resetDraft(), this::accentColor, () -> false,
                FluxGuiStyle.ButtonStyle.ACTION));
        selectedPoint = Math.clamp(selectedPoint, 0, draftPoints.size() - 1);
        updateInputsFromSelection();
        updateButtons();
    }

    @Override
    protected void containerTick() {
        QuantumFeedbackCurve current = menu.getCurve();
        if (!current.equals(serverCurve)) {
            serverCurve = current;
            if (!draftDirty) {
                draftPoints.clear();
                draftPoints.addAll(current.points());
                selectedPoint = Math.clamp(selectedPoint, 0, draftPoints.size() - 1);
                updateInputsFromSelection();
            }
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
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int accent = accentColor();
        FluxGuiStyle.renderFrame(graphics, leftPos, topPos, imageWidth, imageHeight, accent);
        FluxGuiStyle.renderPanel(graphics, leftPos + 12, topPos + 34, 296, 210, accent);
        FluxGuiStyle.renderPanel(graphics, leftPos + 316, topPos + 34, 102, 256, accent);
        renderChart(graphics, accent);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, FluxGuiStyle.TEXT, false);
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_feedback.output_axis"),
                18, 42, FluxGuiStyle.MUTED, false);
        graphics.drawCenteredString(font,
                Component.translatable(menu.getMetric() == QuantumFeedbackMetric.STABILITY
                        ? "screen.flux_overdrive.quantum_feedback.stability_axis"
                        : "screen.flux_overdrive.quantum_feedback.temperature_axis"),
                CHART_X + CHART_WIDTH / 2, 207, FluxGuiStyle.MUTED);

        for (int level = 0; level <= 15; level += 3) {
            int y = CHART_Y + CHART_HEIGHT - signalOffset(level);
            graphics.drawString(font, Integer.toString(level), 22, y - 4,
                    FluxGuiStyle.MUTED, false);
        }
        int minimum = draftPoints.getFirst().threshold();
        int maximum = draftPoints.getLast().threshold();
        graphics.drawString(font, formatThreshold(minimum), CHART_X, CHART_Y + CHART_HEIGHT + 5,
                FluxGuiStyle.MUTED, false);
        String maximumLabel = formatThreshold(maximum);
        graphics.drawString(font, maximumLabel, CHART_X + CHART_WIDTH - font.width(maximumLabel),
                CHART_Y + CHART_HEIGHT + 5, FluxGuiStyle.MUTED, false);

        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_feedback.current_value"),
                326, 44, FluxGuiStyle.TEXT, false);
        String currentValue = menu.isSourceAvailable()
                ? formatThreshold(menu.getSampledValue())
                : Component.translatable("screen.flux_overdrive.quantum_feedback.unavailable").getString();
        graphics.drawString(font, currentValue, 326, 58,
                menu.isSourceAvailable() ? accentColor() : FluxGuiStyle.GOLD, false);
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_feedback.preview_signal"),
                326, 79, FluxGuiStyle.TEXT, false);
        int preview = menu.isSourceAvailable() ? draftCurve().evaluate(menu.getSampledValue()) : 0;
        graphics.drawString(font, preview + " / 15", 326, 93, accentColor(), false);

        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_feedback.points",
                        draftPoints.size(), QuantumFeedbackCurve.MAX_POINTS),
                326, 106, FluxGuiStyle.MUTED, false);
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_feedback.threshold"),
                326, 148, FluxGuiStyle.TEXT, false);
        graphics.drawString(font,
                Component.translatable("screen.flux_overdrive.quantum_feedback.signal"),
                326, 191, FluxGuiStyle.TEXT, false);
        graphics.drawCenteredString(font,
                Component.translatable(menu.isRemoteAccess()
                        ? "screen.flux_overdrive.quantum_feedback.remote"
                        : "screen.flux_overdrive.quantum_feedback.local"),
                367, 235, FluxGuiStyle.MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double logicalMouseX = mouseX / uiScale;
        double logicalMouseY = mouseY / uiScale;
        if (button == 0) {
            int point = pointAt(logicalMouseX, logicalMouseY);
            if (point >= 0) {
                selectedPoint = point;
                draggingPoint = point;
                updateInputsFromSelection();
                updateButtons();
                return true;
            }
        }
        return super.mouseClicked(logicalMouseX, logicalMouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        double logicalMouseX = mouseX / uiScale;
        double logicalMouseY = mouseY / uiScale;
        if (button == 0 && draggingPoint >= 0) {
            dragSelectedPoint(logicalMouseX, logicalMouseY);
            return true;
        }
        return super.mouseDragged(logicalMouseX, logicalMouseY, button,
                dragX / uiScale, dragY / uiScale);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingPoint >= 0) {
            draggingPoint = -1;
            return true;
        }
        return super.mouseReleased(mouseX / uiScale, mouseY / uiScale, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return super.mouseScrolled(mouseX / uiScale, mouseY / uiScale, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335)
                && (thresholdInput.isFocused() || signalInput.isFocused())) {
            applyInputsToSelection();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderChart(GuiGraphics graphics, int accent) {
        int x = leftPos + CHART_X;
        int y = topPos + CHART_Y;
        graphics.fill(x, y, x + CHART_WIDTH, y + CHART_HEIGHT, 0xFF0D1016);
        for (int division = 0; division <= 5; division++) {
            int gridY = y + CHART_HEIGHT - CHART_HEIGHT * division / 5;
            graphics.fill(x, gridY, x + CHART_WIDTH, gridY + 1, 0xFF252B36);
            int gridX = x + CHART_WIDTH * division / 5;
            graphics.fill(gridX, y, gridX + 1, y + CHART_HEIGHT, 0xFF202632);
        }

        int minimum = draftPoints.getFirst().threshold();
        int maximum = draftPoints.getLast().threshold();
        int markerX = -1;
        int markerY = -1;
        if (menu.isSourceAvailable()) {
            markerX = x + thresholdOffset(menu.getSampledValue(), minimum, maximum);
            double interpolatedSignal = draftCurve().interpolatedSignal(menu.getSampledValue());
            markerY = y + CHART_HEIGHT - signalOffset(interpolatedSignal);
            drawDashedVerticalLine(graphics, markerX, y, y + CHART_HEIGHT, 0x66FFFFFF);
        }

        for (int index = 0; index < draftPoints.size() - 1; index++) {
            QuantumFeedbackCurve.Point point = draftPoints.get(index);
            QuantumFeedbackCurve.Point next = draftPoints.get(index + 1);
            int firstX = x + thresholdOffset(point.threshold(), minimum, maximum);
            int nextX = x + thresholdOffset(next.threshold(), minimum, maximum);
            int firstY = y + CHART_HEIGHT - signalOffset(point.signal());
            int nextY = y + CHART_HEIGHT - signalOffset(next.signal());
            drawLine(graphics, firstX, firstY, nextX, nextY, accent);
        }

        if (markerX >= 0) {
            graphics.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, accent);
            graphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFFFFFFFF);
        }

        for (int index = 0; index < draftPoints.size(); index++) {
            QuantumFeedbackCurve.Point point = draftPoints.get(index);
            int pointX = x + thresholdOffset(point.threshold(), minimum, maximum);
            int pointY = y + CHART_HEIGHT - signalOffset(point.signal());
            int color = 0xFFFFFFFF;
            graphics.fill(pointX - HANDLE_RADIUS, pointY - HANDLE_RADIUS,
                    pointX + HANDLE_RADIUS + 1, pointY + HANDLE_RADIUS + 1, accent);
            graphics.fill(pointX - HANDLE_RADIUS + 1, pointY - HANDLE_RADIUS + 1,
                    pointX + HANDLE_RADIUS, pointY + HANDLE_RADIUS, color);
        }
    }

    private void dragSelectedPoint(double mouseX, double mouseY) {
        QuantumFeedbackCurve.Point current = draftPoints.get(selectedPoint);
        int signal = Math.clamp((int) Math.round((topPos + CHART_Y + CHART_HEIGHT - mouseY)
                * 15.0 / CHART_HEIGHT), 0, 15);
        int threshold = current.threshold();
        if (selectedPoint > 0 && selectedPoint < draftPoints.size() - 1) {
            int minimum = draftPoints.getFirst().threshold();
            int maximum = draftPoints.getLast().threshold();
            threshold = minimum + (int) Math.round(Math.clamp(
                    (mouseX - leftPos - CHART_X) / CHART_WIDTH, 0.0, 1.0) * (maximum - minimum));
            threshold = Math.clamp(threshold,
                    draftPoints.get(selectedPoint - 1).threshold() + 1,
                    draftPoints.get(selectedPoint + 1).threshold() - 1);
        }
        replaceSelected(new QuantumFeedbackCurve.Point(threshold, signal));
    }

    private void addPoint() {
        if (draftPoints.size() >= QuantumFeedbackCurve.MAX_POINTS) return;
        int insertAfter = -1;
        int largestGap = 1;
        for (int index = 0; index < draftPoints.size() - 1; index++) {
            int gap = draftPoints.get(index + 1).threshold() - draftPoints.get(index).threshold();
            if (gap > largestGap) {
                largestGap = gap;
                insertAfter = index;
            }
        }
        if (insertAfter < 0) return;
        QuantumFeedbackCurve.Point left = draftPoints.get(insertAfter);
        int threshold = left.threshold() + largestGap / 2;
        draftPoints.add(insertAfter + 1, new QuantumFeedbackCurve.Point(threshold, left.signal()));
        selectedPoint = insertAfter + 1;
        markDraftChanged();
    }

    private void removePoint() {
        if (draftPoints.size() <= QuantumFeedbackCurve.MIN_POINTS) return;
        if (menu.getMetric() == QuantumFeedbackMetric.STABILITY
                && (selectedPoint == 0 || selectedPoint == draftPoints.size() - 1)) {
            return;
        }
        draftPoints.remove(selectedPoint);
        selectedPoint = Math.clamp(selectedPoint, 0, draftPoints.size() - 1);
        markDraftChanged();
    }

    private void applyPreset(Preset preset) {
        int minimum = draftPoints.getFirst().threshold();
        int maximum = draftPoints.getLast().threshold();
        QuantumFeedbackCurve curve;
        if (preset == Preset.ASCENDING || preset == Preset.DESCENDING) {
            curve = QuantumFeedbackCurve.evenlySpaced(menu.getMetric(), minimum, maximum,
                    preset == Preset.DESCENDING);
        } else {
            int range = maximum - minimum;
            int low = Math.min(maximum, minimum + Math.max(1, range / 4));
            int high = Math.min(maximum, minimum + Math.max(1, range * 3 / 4));
            List<QuantumFeedbackCurve.Point> points = new ArrayList<>();
            switch (preset) {
                case HIGH -> {
                    addPresetPoint(points, minimum, 0);
                    addPresetPoint(points, Math.max(minimum, high - 1), 0);
                    addPresetPoint(points, high, 15);
                    addPresetPoint(points, maximum, 15);
                }
                case LOW -> {
                    addPresetPoint(points, minimum, 15);
                    if (low < maximum) addPresetPoint(points, low, 15);
                    addPresetPoint(points, Math.min(maximum, low + 1), 0);
                    addPresetPoint(points, maximum, 0);
                }
                case WINDOW -> {
                    addPresetPoint(points, minimum, 0);
                    if (range >= 3) {
                        addPresetPoint(points, Math.max(minimum, low - 1), 0);
                        addPresetPoint(points, low, 15);
                        addPresetPoint(points, high, 15);
                        addPresetPoint(points, Math.min(maximum, high + 1), 0);
                    }
                    addPresetPoint(points, maximum, 0);
                }
                default -> throw new IllegalStateException("Unexpected feedback preset");
            }
            curve = new QuantumFeedbackCurve(menu.getMetric(), points);
        }
        draftPoints.clear();
        draftPoints.addAll(curve.points());
        selectedPoint = 0;
        markDraftChanged();
    }

    private void applyInputsToSelection() {
        try {
            int threshold = new BigDecimal(thresholdInput.getValue().trim())
                    .movePointRight(2).setScale(0, RoundingMode.HALF_UP).intValueExact();
            int signal = Integer.parseInt(signalInput.getValue().trim());
            QuantumFeedbackMetric metric = menu.getMetric();
            threshold = Math.clamp(threshold, metric.allowedMinimum(), metric.allowedMaximum());
            signal = Math.clamp(signal, 0, 15);
            if (metric == QuantumFeedbackMetric.STABILITY && selectedPoint == 0) {
                threshold = metric.allowedMinimum();
            } else if (metric == QuantumFeedbackMetric.STABILITY
                    && selectedPoint == draftPoints.size() - 1) {
                threshold = metric.allowedMaximum();
            } else {
                if (selectedPoint > 0) {
                    threshold = Math.max(threshold, draftPoints.get(selectedPoint - 1).threshold() + 1);
                }
                if (selectedPoint < draftPoints.size() - 1) {
                    threshold = Math.min(threshold, draftPoints.get(selectedPoint + 1).threshold() - 1);
                }
            }
            replaceSelected(new QuantumFeedbackCurve.Point(threshold, signal));
        } catch (ArithmeticException | NumberFormatException ignored) {
            updateInputsFromSelection();
        }
    }

    private void replaceSelected(QuantumFeedbackCurve.Point point) {
        if (draftPoints.get(selectedPoint).equals(point)) return;
        draftPoints.set(selectedPoint, point);
        markDraftChanged();
    }

    private void saveDraft() {
        applyInputsToSelection();
        QuantumFeedbackCurve curve;
        try {
            curve = draftCurve();
        } catch (RuntimeException ignored) {
            resetDraft();
            return;
        }
        PacketDistributor.sendToServer(new UpdateFeedbackCurvePayload(menu.containerId, curve.points()));
        serverCurve = curve;
        draftDirty = false;
        updateButtons();
    }

    private void resetDraft() {
        draftPoints.clear();
        draftPoints.addAll(menu.getCurve().points());
        serverCurve = menu.getCurve();
        selectedPoint = 0;
        draftDirty = false;
        updateInputsFromSelection();
        updateButtons();
    }

    private QuantumFeedbackCurve draftCurve() {
        return new QuantumFeedbackCurve(menu.getMetric(), draftPoints);
    }

    private void markDraftChanged() {
        draftDirty = !draftPoints.equals(serverCurve.points());
        updateInputsFromSelection();
        updateButtons();
    }

    private void updateInputsFromSelection() {
        if (thresholdInput == null || signalInput == null || draftPoints.isEmpty()) return;
        QuantumFeedbackCurve.Point point = draftPoints.get(selectedPoint);
        thresholdInput.setValue(formatEditableThreshold(point.threshold()));
        signalInput.setValue(Integer.toString(point.signal()));
    }

    private void updateButtons() {
        if (removeButton != null) {
            removeButton.active = draftPoints.size() > QuantumFeedbackCurve.MIN_POINTS
                    && (menu.getMetric() != QuantumFeedbackMetric.STABILITY
                    || selectedPoint > 0 && selectedPoint < draftPoints.size() - 1);
        }
        if (saveButton != null) saveButton.active = draftDirty;
    }

    private void addPresetButton(int x, int y, int width, String key, Preset preset) {
        addRenderableWidget(new FluxStyleButton(leftPos + x, topPos + y, width, 18,
                Component.translatable("screen.flux_overdrive.quantum_feedback.preset." + key),
                ignored -> applyPreset(preset), this::accentColor, () -> false,
                FluxGuiStyle.ButtonStyle.ACTION));
    }

    private static void addPresetPoint(List<QuantumFeedbackCurve.Point> points,
                                       int threshold, int signal) {
        QuantumFeedbackCurve.Point point = new QuantumFeedbackCurve.Point(threshold, signal);
        if (!points.isEmpty() && points.getLast().threshold() == threshold) {
            points.set(points.size() - 1, point);
        } else {
            points.add(point);
        }
    }

    private int pointAt(double mouseX, double mouseY) {
        int minimum = draftPoints.getFirst().threshold();
        int maximum = draftPoints.getLast().threshold();
        int best = -1;
        double bestDistance = 64.0;
        for (int index = 0; index < draftPoints.size(); index++) {
            QuantumFeedbackCurve.Point point = draftPoints.get(index);
            int x = leftPos + CHART_X + thresholdOffset(point.threshold(), minimum, maximum);
            int y = topPos + CHART_Y + CHART_HEIGHT - signalOffset(point.signal());
            double distance = (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private static int thresholdOffset(int threshold, int minimum, int maximum) {
        if (maximum <= minimum) return 0;
        double ratio = Math.clamp((threshold - minimum) / (double) (maximum - minimum), 0.0, 1.0);
        return (int) Math.round(ratio * CHART_WIDTH);
    }

    private static int signalOffset(double signal) {
        return (int) Math.round(Math.clamp(signal, 0, 15) / 15.0 * CHART_HEIGHT);
    }

    private static void drawLine(GuiGraphics graphics, int startX, int startY,
                                 int endX, int endY, int color) {
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (length < 0.001F) {
            graphics.fill(startX, startY, startX + 1, startY + 1, color);
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(startX + 0.5F, startY + 0.5F, 0.0F);
        graphics.pose().mulPose(Axis.ZP.rotation((float) Math.atan2(deltaY, deltaX)));
        graphics.pose().translate(0.0F, -LINE_WIDTH / 2.0F, 0.0F);
        graphics.pose().scale(length, LINE_WIDTH, 1.0F);
        graphics.fill(0, 0, 1, 1, color);
        graphics.pose().popPose();
    }

    private static void drawDashedVerticalLine(GuiGraphics graphics, int x,
                                               int top, int bottom, int color) {
        for (int y = top; y < bottom; y += 6) {
            graphics.fill(x, y, x + 1, Math.min(y + 3, bottom), color);
        }
    }

    private String formatThreshold(int threshold) {
        String suffix = menu.getMetric() == QuantumFeedbackMetric.STABILITY ? "%" : " K";
        return formatEditableThreshold(threshold) + suffix;
    }

    private static String formatEditableThreshold(int threshold) {
        if (threshold % 100 == 0) return Integer.toString(threshold / 100);
        if (threshold % 10 == 0) return String.format(Locale.ROOT, "%.1f", threshold / 100.0);
        return String.format(Locale.ROOT, "%.2f", threshold / 100.0);
    }

    private int accentColor() {
        return FluxGuiStyle.accent(menu.getNetworkColorRgb());
    }

    private static boolean isDecimalInput(String value) {
        if (value.isEmpty()) return true;
        int decimals = 0;
        boolean separator = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '.') {
                if (separator) return false;
                separator = true;
            } else if (!Character.isDigit(character)) {
                return false;
            } else if (separator && ++decimals > 2) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIntegerInput(String value) {
        if (value.isEmpty()) return true;
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) return false;
        }
        return true;
    }

    private enum Preset {
        ASCENDING,
        DESCENDING,
        HIGH,
        LOW,
        WINDOW
    }
}

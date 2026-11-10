import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GenerateConnectedTextures {
    private static final int TILE = 32;
    private static final int COLUMNS = 8;
    private static final int ROWS = 6;

    private static final int TOP = 0x01;
    private static final int TOP_RIGHT = 0x02;
    private static final int RIGHT = 0x04;
    private static final int BOTTOM_RIGHT = 0x08;
    private static final int BOTTOM = 0x10;
    private static final int BOTTOM_LEFT = 0x20;
    private static final int LEFT = 0x40;
    private static final int TOP_LEFT = 0x80;

    private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
    private static final Path CTM_TEXTURE_ROOT = RESOURCE_ROOT.resolve("assets/flux_overdrive/textures/ctm/quantum");
    private static final Path BLOCK_TEXTURE_ROOT = RESOURCE_ROOT.resolve("assets/flux_overdrive/textures/block/quantum");
    private static final Path ATLAS_FILE = RESOURCE_ROOT.resolve("assets/minecraft/atlases/blocks.json");
    private static final List<String> LEGACY_CTM_IDS = List.of(
            "transmon_qubit_chain_casing",
            "transmon_qubit_chain_casing_glass",
            "weak_coupling_cavity_qed_casing",
            "weak_coupling_cavity_qed_casing_glass",
            "strong_coupling_cavity_qed_casing",
            "strong_coupling_cavity_qed_casing_glass",
            "quantum_entanglement_casing"
    );
    private static final List<String> LEGACY_DEVICE_IDS = List.of(
            "quantum_battery_controller_frame",
            "quantum_battery_controller_top",
            "quantum_battery_controller_core",
            "quantum_battery_entanglement_controller_side",
            "quantum_battery_entanglement_controller_top",
            "quantum_energy_constraint_interface",
            "quantum_energy_constraint_interface_3x3",
            "quantum_energy_constraint_interface_3x3_assembled",
            "quantum_energy_constraint_entanglement_port",
            "quantum_entanglement_antenna_side",
            "quantum_entanglement_antenna_top",
            "quantum_solid_cooling_plugin_bottom",
            "quantum_solid_cooling_plugin_side",
            "quantum_solid_cooling_plugin_top",
            "quantum_advanced_cooling_plugin_bottom",
            "quantum_advanced_cooling_plugin_side",
            "quantum_advanced_cooling_plugin_top",
            "quantum_entanglement_input_port",
            "quantum_entanglement_input_port_side",
            "quantum_entanglement_input_port_top",
            "quantum_entanglement_output_port",
            "quantum_entanglement_output_port_side",
            "quantum_entanglement_output_port_top"
    );

    private static final List<Style> STYLES = List.of(
            new Style("transmon_qubit_chain_casing", "battery/casing/transmon_qubit_chain/sheet", 0,
                    Motif.TRANSMON, false,
                    0xFF247A80, 0xFF18505A, 0xFF4FC2B8, 0xFFD7F4EA, 0xFF4CFFE0, 0xFFF1D264),
            new Style("transmon_qubit_chain_casing_glass", "battery/casing/transmon_qubit_chain_glass/sheet", 0,
                    Motif.TRANSMON, true,
                    0x3860D9C7, 0x50409C95, 0x8090E8D8, 0xC0E8FFF8, 0x9A64FFE3, 0x8AEEE080),
            new Style("weak_coupling_cavity_qed_casing", "battery/casing/weak_coupling_cavity_qed/sheet", 7,
                    Motif.WEAK, false,
                    0xFF365F9D, 0xFF244B7D, 0xFF6C9EE0, 0xFFE0ECFF, 0xFF55D5FF, 0xFFD08EFF),
            new Style("weak_coupling_cavity_qed_casing_glass", "battery/casing/weak_coupling_cavity_qed_glass/sheet", 7,
                    Motif.WEAK, true,
                    0x385B9FE0, 0x50416FB5, 0x8095C4F2, 0xC0EEF6FF, 0x9A72D9FF, 0x8AD3A0FF),
            new Style("strong_coupling_cavity_qed_casing", "battery/casing/strong_coupling_cavity_qed/sheet", 14,
                    Motif.STRONG, false,
                    0xFF82458F, 0xFF66366F, 0xFFC06DCB, 0xFFF8E4FA, 0xFF63F4DB, 0xFFFF8FCB),
            new Style("strong_coupling_cavity_qed_casing_glass", "battery/casing/strong_coupling_cavity_qed_glass/sheet", 14,
                    Motif.STRONG, true,
                    0x389C4FB5, 0x50743B8A, 0x80D58CE0, 0xC0FAEAFF, 0x9A76F7DE, 0x8AFF9DCC),
            new Style("quantum_entanglement_casing", "entanglement/casing/sheet", 21,
                    Motif.ENTANGLEMENT, false,
                    0xFF3D4458, 0xFF1B2234, 0xFF77839F, 0xFFDCE2F0, 0xFF72EAD8, 0xFFD0A0F5)
    );

    public static void main(String[] args) throws IOException {
        Files.createDirectories(CTM_TEXTURE_ROOT);
        Files.createDirectories(BLOCK_TEXTURE_ROOT);
        Files.createDirectories(ATLAS_FILE.getParent());

        List<Integer> masks = canonicalMasks();
        if (masks.size() != 47) {
            throw new IllegalStateException("Expected 47 CTM masks, got " + masks.size());
        }
        QuantumDeviceTextureGenerator.write(BLOCK_TEXTURE_ROOT, CTM_TEXTURE_ROOT, masks);

        for (Style style : STYLES) {
            writeTexture(style, masks);
        }
        Files.writeString(ATLAS_FILE, createAtlasJson(masks));
        removeLegacyOutputs();
    }

    private static void removeLegacyOutputs() throws IOException {
        Path legacyCtmRoot = CTM_TEXTURE_ROOT.getParent();
        Path legacyBlockRoot = BLOCK_TEXTURE_ROOT.getParent();
        for (String id : LEGACY_CTM_IDS) {
            Files.deleteIfExists(legacyCtmRoot.resolve(id + ".png"));
        }
        for (String id : LEGACY_DEVICE_IDS) {
            Files.deleteIfExists(legacyBlockRoot.resolve(id + ".png"));
        }
    }

    private static void writeTexture(Style style, List<Integer> masks) throws IOException {
        BufferedImage atlas = new BufferedImage(COLUMNS * TILE, ROWS * TILE, BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < masks.size(); index++) {
            BufferedImage tile = renderTile(style, masks.get(index), false);
            copy(tile, atlas, index % COLUMNS * TILE, index / COLUMNS * TILE);
        }

        BufferedImage item = renderTile(style, 0, true);
        copy(item, atlas, 7 * TILE, 5 * TILE);
        Path output = CTM_TEXTURE_ROOT.resolve(style.sheetPath() + ".png");
        Files.createDirectories(output.getParent());
        ImageIO.write(atlas, "png", output.toFile());
    }

    private static BufferedImage renderTile(Style style, int mask, boolean item) {
        BufferedImage image = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        drawBackground(image, style, item);

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        if (style.glass()) {
            drawGlassDetail(graphics, style, mask, item);
        } else {
            drawCasingDetail(graphics, style, mask, item);
        }
        drawFrame(graphics, style, mask);
        drawInnerCorners(graphics, style, mask);
        if (item) {
            drawItemMark(graphics, style);
        }
        graphics.dispose();
        return image;
    }

    private static void drawBackground(BufferedImage image, Style style, boolean item) {
        if (style.motif() != Motif.ENTANGLEMENT) {
            drawBatteryBackground(image, style, item);
            return;
        }

        int base = style.base();
        int alphaBoost = item && style.glass() ? 28 : 0;
        int seed = style.seed();
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int band = switch (style.motif()) {
                    case TRANSMON -> ((y >> 2) + (x >> 4) + seed) & 3;
                    case WEAK -> ((x >> 2) + (y >> 4) + seed) & 3;
                    case STRONG -> (((x + y) >> 3) + seed) & 3;
                    case ENTANGLEMENT -> (((x - y + TILE) >> 3) + seed) & 3;
                };
                int amount = style.glass() ? (band - 1) / 2 : band - 1;
                int alpha = Math.min(255, alpha(base) + alphaBoost);
                image.setRGB(x, y, argb(alpha,
                        clamp(red(base) + amount),
                        clamp(green(base) + amount),
                        clamp(blue(base) + amount)));
            }
        }
    }

    private static void drawBatteryBackground(BufferedImage image, Style style, boolean item) {
        int base = style.base();
        int alphaBoost = item && style.glass() ? 28 : 0;
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                int noise = textureNoise(x >> 1, y >> 1, style.seed());
                int grain = (noise >>> 28 & 7) - 3;
                int broadLight = (y < 5 ? 2 : y > 26 ? -2 : 0)
                        + (x < 4 ? 1 : x > 27 ? -1 : 0);
                if ((noise & 31) == 0) {
                    grain -= 5;
                } else if ((noise >>> 5 & 63) == 0) {
                    grain += 4;
                }
                int amount = broadLight + grain;
                if (style.glass()) {
                    amount /= 2;
                }
                int alphaVariation = style.glass() ? (noise >>> 11 & 3) - 1 : 0;
                int alpha = clamp(alpha(base) + alphaBoost + alphaVariation);
                image.setRGB(x, y, argb(alpha,
                        clamp(red(base) + amount),
                        clamp(green(base) + amount),
                        clamp(blue(base) + amount)));
            }
        }
    }

    private static void drawCasingDetail(Graphics2D graphics, Style style, int mask, boolean item) {
        if (item || mask != 0) {
            return;
        }

        if (style.motif() != Motif.ENTANGLEMENT) {
            drawBatteryCasingDetail(graphics, style);
            return;
        }

        switch (style.motif()) {
            case TRANSMON -> {
                graphics.setColor(color(withAlpha(style.mid(), 185)));
                graphics.drawLine(8, 12, 23, 12);
                graphics.drawLine(8, 19, 23, 19);
                graphics.setColor(color(style.accent()));
                graphics.drawLine(11, 16, 20, 16);
            }
            case WEAK -> {
                graphics.setColor(color(withAlpha(style.mid(), 185)));
                graphics.drawRoundRect(8, 8, 15, 15, 4, 4);
                graphics.setColor(color(style.accent()));
                graphics.drawLine(8, 16, 13, 16);
                graphics.setColor(color(style.secondary()));
                graphics.drawLine(18, 16, 23, 16);
            }
            case STRONG -> {
                graphics.setColor(color(withAlpha(style.mid(), 180)));
                graphics.drawRoundRect(9, 9, 13, 13, 3, 3);
                graphics.setColor(color(style.accent()));
                graphics.drawLine(9, 12, 16, 12);
                graphics.setColor(color(style.secondary()));
                graphics.drawLine(16, 19, 22, 19);
            }
            case ENTANGLEMENT -> {
                graphics.setColor(color(style.accent()));
                graphics.drawArc(7, 10, 12, 11, 40, 280);
                graphics.setColor(color(style.secondary()));
                graphics.drawArc(13, 10, 12, 11, 220, 280);
            }
        }
    }

    private static void drawBatteryCasingDetail(Graphics2D graphics, Style style) {
        graphics.setColor(color(style.shadow()));
        graphics.fillRect(8, 8, 16, 16);
        graphics.setColor(color(withAlpha(style.mid(), 220)));
        graphics.fillRect(9, 9, 14, 2);
        graphics.fillRect(9, 11, 2, 11);
        graphics.setColor(color(withAlpha(style.highlight(), 150)));
        graphics.fillRect(11, 10, 9, 1);
        graphics.setColor(color(style.shadow()));
        graphics.fillRect(11, 22, 12, 1);
        graphics.fillRect(22, 11, 1, 12);

        switch (style.motif()) {
            case TRANSMON -> {
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(11, 13, 10, 3);
                graphics.fillRect(11, 18, 10, 3);
                graphics.setColor(color(withAlpha(style.shadow(), 180)));
                graphics.fillRect(11, 15, 10, 1);
                graphics.fillRect(11, 20, 10, 1);
                graphics.setColor(color(style.accent()));
                graphics.fillRect(14, 12, 4, 10);
                graphics.setColor(color(style.highlight()));
                graphics.fillRect(15, 13, 2, 3);
            }
            case WEAK -> {
                graphics.setColor(color(style.mid()));
                graphics.fillRect(11, 11, 10, 2);
                graphics.fillRect(11, 19, 10, 2);
                graphics.fillRect(11, 13, 2, 6);
                graphics.fillRect(19, 13, 2, 6);
                graphics.setColor(color(style.accent()));
                graphics.fillRect(13, 13, 3, 3);
                graphics.fillRect(16, 16, 3, 3);
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(16, 13, 3, 3);
                graphics.fillRect(13, 16, 3, 3);
            }
            case STRONG -> {
                graphics.setColor(color(style.mid()));
                graphics.fillRect(12, 10, 8, 2);
                graphics.fillRect(10, 12, 12, 8);
                graphics.fillRect(12, 20, 8, 2);
                graphics.setColor(color(style.shadow()));
                graphics.fillRect(12, 12, 8, 8);
                graphics.setColor(color(style.accent()));
                graphics.fillRect(13, 13, 3, 6);
                graphics.fillRect(16, 13, 3, 3);
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(16, 16, 3, 3);
                graphics.setColor(color(style.highlight()));
                graphics.fillRect(14, 13, 2, 1);
            }
            case ENTANGLEMENT -> {
                // Handled by the original entanglement branch.
            }
        }
    }

    private static void drawGlassDetail(Graphics2D graphics, Style style, int mask, boolean item) {
        if (style.motif() != Motif.ENTANGLEMENT) {
            drawBatteryGlassDetail(graphics, style, mask, item);
            return;
        }

        if (item) {
            graphics.setColor(color(withAlpha(style.highlight(), 44)));
            graphics.drawLine(7, 24, 24, 7);
            return;
        }

        switch (style.motif()) {
            case TRANSMON -> {
                graphics.setColor(color(withAlpha(style.highlight(), 36)));
                if (!has(mask, TOP)) graphics.drawLine(7, 6, 24, 6);
                if (!has(mask, BOTTOM)) graphics.drawLine(7, 25, 24, 25);
            }
            case WEAK -> {
                graphics.setColor(color(withAlpha(style.secondary(), 38)));
                if (!has(mask, LEFT)) graphics.drawArc(5, 7, 18, 18, 105, 150);
                if (!has(mask, RIGHT)) graphics.drawArc(8, 7, 18, 18, 285, 150);
            }
            case STRONG -> {
                graphics.setColor(color(withAlpha(style.accent(), 38)));
                if (!has(mask, TOP) || !has(mask, LEFT)) graphics.drawLine(6, 18, 18, 6);
                graphics.setColor(color(withAlpha(style.secondary(), 30)));
                if (!has(mask, BOTTOM) || !has(mask, RIGHT)) graphics.drawLine(14, 25, 25, 14);
            }
            case ENTANGLEMENT -> {
                // Entanglement casing is opaque; this branch keeps the motif switch exhaustive.
            }
        }
    }

    private static void drawBatteryGlassDetail(Graphics2D graphics, Style style, int mask, boolean item) {
        int shift = Math.floorMod(style.seed() / 7, 3);
        graphics.setColor(color(withAlpha(style.highlight(), item ? 62 : 42)));
        if (item) {
            graphics.fillRect(9, 20, 3, 2);
            graphics.fillRect(12, 17, 3, 2);
            graphics.fillRect(15, 14, 3, 2);
            graphics.fillRect(18, 11, 4, 2);
            return;
        }

        graphics.fillRect(10 + shift, 11, 2, 2);
        graphics.fillRect(20 - shift, 19, 3, 2);
        if (!has(mask, TOP)) {
            graphics.fillRect(6 + shift, 6, 7, 2);
            graphics.fillRect(21, 7, 3, 1);
        }
        if (!has(mask, RIGHT)) {
            graphics.fillRect(24, 8 + shift, 2, 6);
            graphics.fillRect(23, 21, 1, 3);
        }
        if (!has(mask, BOTTOM)) {
            graphics.fillRect(18 - shift, 24, 7, 2);
            graphics.fillRect(7, 23, 3, 1);
        }
        if (!has(mask, LEFT)) {
            graphics.fillRect(6, 18 - shift, 2, 6);
            graphics.fillRect(7, 8, 1, 3);
        }
    }

    private static void drawFrame(Graphics2D graphics, Style style, int mask) {
        if (style.motif() != Motif.ENTANGLEMENT) {
            drawBatteryFrame(graphics, style, mask);
            return;
        }

        int width = frameWidth(style.motif());
        Color shadow = color(style.shadow());
        Color mid = color(style.mid());
        Color highlight = color(style.highlight());

        if (!has(mask, TOP)) {
            graphics.setColor(shadow);
            graphics.fillRect(0, 0, TILE, width);
            graphics.setColor(mid);
            graphics.fillRect(0, 1, TILE, width - 1);
            graphics.setColor(highlight);
            graphics.fillRect(0, width - 1, TILE, 1);
        }
        if (!has(mask, RIGHT)) {
            graphics.setColor(shadow);
            graphics.fillRect(TILE - width, 0, width, TILE);
            graphics.setColor(mid);
            graphics.fillRect(TILE - width, 0, width - 1, TILE);
            graphics.setColor(highlight);
            graphics.fillRect(TILE - width, 0, 1, TILE);
        }
        if (!has(mask, BOTTOM)) {
            graphics.setColor(shadow);
            graphics.fillRect(0, TILE - width, TILE, width);
            graphics.setColor(mid);
            graphics.fillRect(0, TILE - width, TILE, width - 1);
            graphics.setColor(highlight);
            graphics.fillRect(0, TILE - width, TILE, 1);
        }
        if (!has(mask, LEFT)) {
            graphics.setColor(shadow);
            graphics.fillRect(0, 0, width, TILE);
            graphics.setColor(mid);
            graphics.fillRect(1, 0, width - 1, TILE);
            graphics.setColor(highlight);
            graphics.fillRect(width - 1, 0, 1, TILE);
        }

        int primaryStart = switch (style.motif()) {
            case TRANSMON -> 8;
            case WEAK -> 5;
            case STRONG -> 12;
            case ENTANGLEMENT -> 7;
        };
        int primaryLength = 7;
        graphics.setColor(color(style.accent()));
        if (!has(mask, TOP)) graphics.fillRect(primaryStart, width - 1, primaryLength, 1);
        if (!has(mask, RIGHT)) graphics.fillRect(TILE - width, primaryStart, 1, primaryLength);
        if (!has(mask, BOTTOM)) graphics.fillRect(TILE - primaryStart - primaryLength, TILE - width, primaryLength, 1);
        if (!has(mask, LEFT)) graphics.fillRect(width - 1, TILE - primaryStart - primaryLength, 1, primaryLength);

        if (style.motif() == Motif.STRONG || style.motif() == Motif.ENTANGLEMENT) {
            int secondaryStart = style.motif() == Motif.STRONG ? 5 : 19;
            int secondaryLength = 5;
            graphics.setColor(color(style.secondary()));
            if (!has(mask, TOP)) graphics.fillRect(secondaryStart, width - 1, secondaryLength, 1);
            if (!has(mask, RIGHT)) graphics.fillRect(TILE - width, secondaryStart, 1, secondaryLength);
            if (!has(mask, BOTTOM)) graphics.fillRect(TILE - secondaryStart - secondaryLength, TILE - width, secondaryLength, 1);
            if (!has(mask, LEFT)) graphics.fillRect(width - 1, TILE - secondaryStart - secondaryLength, 1, secondaryLength);
        }
    }

    private static void drawBatteryFrame(Graphics2D graphics, Style style, int mask) {
        int width = 4;
        Color shadow = color(style.shadow());
        Color mid = color(style.mid());
        Color highlight = color(withAlpha(style.highlight(), Math.min(alpha(style.highlight()), 190)));
        int notch = 5 + Math.floorMod(style.seed() / 7, 4);

        if (!has(mask, TOP)) {
            graphics.setColor(shadow);
            graphics.fillRect(0, 0, TILE, width);
            graphics.setColor(mid);
            graphics.fillRect(0, 1, TILE, 2);
            graphics.setColor(highlight);
            graphics.fillRect(0, 3, TILE, 1);
            graphics.setColor(shadow);
            graphics.fillRect(notch, 1, 4, 1);
            graphics.fillRect(24, 2, 3, 1);
        }
        if (!has(mask, RIGHT)) {
            graphics.setColor(shadow);
            graphics.fillRect(TILE - width, 0, width, TILE);
            graphics.setColor(mid);
            graphics.fillRect(TILE - 3, 0, 2, TILE);
            graphics.setColor(highlight);
            graphics.fillRect(TILE - width, 0, 1, TILE);
            graphics.setColor(shadow);
            graphics.fillRect(TILE - 2, notch, 1, 4);
            graphics.fillRect(TILE - 3, 24, 1, 3);
        }
        if (!has(mask, BOTTOM)) {
            graphics.setColor(shadow);
            graphics.fillRect(0, TILE - width, TILE, width);
            graphics.setColor(mid);
            graphics.fillRect(0, TILE - 3, TILE, 2);
            graphics.setColor(highlight);
            graphics.fillRect(0, TILE - width, TILE, 1);
            graphics.setColor(shadow);
            graphics.fillRect(TILE - notch - 4, TILE - 2, 4, 1);
            graphics.fillRect(5, TILE - 3, 3, 1);
        }
        if (!has(mask, LEFT)) {
            graphics.setColor(shadow);
            graphics.fillRect(0, 0, width, TILE);
            graphics.setColor(mid);
            graphics.fillRect(1, 0, 2, TILE);
            graphics.setColor(highlight);
            graphics.fillRect(3, 0, 1, TILE);
            graphics.setColor(shadow);
            graphics.fillRect(1, TILE - notch - 4, 1, 4);
            graphics.fillRect(2, 5, 1, 3);
        }

        Color accent = color(withAlpha(style.accent(), Math.min(alpha(style.accent()), 210)));
        graphics.setColor(accent);
        if (!has(mask, TOP)) graphics.fillRect(19, 2, 3, 2);
        if (!has(mask, RIGHT)) graphics.fillRect(28, 19, 2, 3);
        if (!has(mask, BOTTOM)) graphics.fillRect(10, 28, 3, 2);
        if (!has(mask, LEFT)) graphics.fillRect(2, 10, 2, 3);

        if (style.motif() == Motif.STRONG) {
            graphics.setColor(color(withAlpha(style.secondary(), Math.min(alpha(style.secondary()), 205))));
            if (!has(mask, TOP)) graphics.fillRect(11, 2, 2, 2);
            if (!has(mask, RIGHT)) graphics.fillRect(28, 11, 2, 2);
            if (!has(mask, BOTTOM)) graphics.fillRect(19, 28, 2, 2);
            if (!has(mask, LEFT)) graphics.fillRect(2, 19, 2, 2);
        }
    }

    private static void drawInnerCorners(Graphics2D graphics, Style style, int mask) {
        if (style.motif() != Motif.ENTANGLEMENT) {
            drawBatteryInnerCorners(graphics, style, mask);
            return;
        }

        graphics.setColor(color(withAlpha(style.secondary(), Math.min(alpha(style.secondary()), 160))));
        graphics.setStroke(new BasicStroke(1));
        if (has(mask, TOP | RIGHT) && !has(mask, TOP_RIGHT)) {
            graphics.drawLine(27, 0, 27, 4);
            graphics.drawLine(27, 4, 31, 4);
        }
        if (has(mask, RIGHT | BOTTOM) && !has(mask, BOTTOM_RIGHT)) {
            graphics.drawLine(27, 27, 31, 27);
            graphics.drawLine(27, 27, 27, 31);
        }
        if (has(mask, BOTTOM | LEFT) && !has(mask, BOTTOM_LEFT)) {
            graphics.drawLine(0, 27, 4, 27);
            graphics.drawLine(4, 27, 4, 31);
        }
        if (has(mask, LEFT | TOP) && !has(mask, TOP_LEFT)) {
            graphics.drawLine(4, 0, 4, 4);
            graphics.drawLine(0, 4, 4, 4);
        }
    }

    private static void drawBatteryInnerCorners(Graphics2D graphics, Style style, int mask) {
        graphics.setColor(color(style.shadow()));
        if (has(mask, TOP | RIGHT) && !has(mask, TOP_RIGHT)) {
            graphics.fillRect(26, 0, 2, 6);
            graphics.fillRect(26, 4, 6, 2);
        }
        if (has(mask, RIGHT | BOTTOM) && !has(mask, BOTTOM_RIGHT)) {
            graphics.fillRect(26, 26, 6, 2);
            graphics.fillRect(26, 26, 2, 6);
        }
        if (has(mask, BOTTOM | LEFT) && !has(mask, BOTTOM_LEFT)) {
            graphics.fillRect(0, 26, 6, 2);
            graphics.fillRect(4, 26, 2, 6);
        }
        if (has(mask, LEFT | TOP) && !has(mask, TOP_LEFT)) {
            graphics.fillRect(4, 0, 2, 6);
            graphics.fillRect(0, 4, 6, 2);
        }

        graphics.setColor(color(withAlpha(style.mid(), Math.min(alpha(style.mid()), 185))));
        if (has(mask, TOP | RIGHT) && !has(mask, TOP_RIGHT)) graphics.fillRect(27, 4, 3, 1);
        if (has(mask, RIGHT | BOTTOM) && !has(mask, BOTTOM_RIGHT)) graphics.fillRect(27, 27, 1, 3);
        if (has(mask, BOTTOM | LEFT) && !has(mask, BOTTOM_LEFT)) graphics.fillRect(2, 27, 3, 1);
        if (has(mask, LEFT | TOP) && !has(mask, TOP_LEFT)) graphics.fillRect(4, 2, 1, 3);
    }

    private static void drawItemMark(Graphics2D graphics, Style style) {
        if (style.motif() != Motif.ENTANGLEMENT) {
            drawBatteryItemMark(graphics, style);
            return;
        }

        int centerAlpha = style.glass() ? 112 : 215;
        graphics.setColor(color(withAlpha(style.shadow(), centerAlpha)));
        graphics.fillRoundRect(7, 7, 18, 18, 5, 5);

        switch (style.motif()) {
            case TRANSMON -> {
                graphics.setColor(color(style.accent()));
                graphics.fillRect(9, 11, 14, 2);
                graphics.setColor(color(style.highlight()));
                graphics.fillRect(11, 15, 10, 2);
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(13, 19, 6, 2);
            }
            case WEAK -> {
                graphics.setColor(color(style.accent()));
                graphics.drawOval(9, 9, 13, 13);
                graphics.setColor(color(style.secondary()));
                graphics.drawArc(11, 11, 9, 9, 210, 240);
            }
            case STRONG -> {
                graphics.setColor(color(style.highlight()));
                graphics.drawRoundRect(9, 9, 13, 13, 4, 4);
                graphics.setColor(color(style.accent()));
                graphics.fillRect(9, 12, 6, 2);
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(17, 18, 6, 2);
            }
            case ENTANGLEMENT -> {
                graphics.setColor(color(style.accent()));
                graphics.drawOval(8, 11, 10, 10);
                graphics.setColor(color(style.secondary()));
                graphics.drawOval(14, 11, 10, 10);
            }
        }
    }

    private static void drawBatteryItemMark(Graphics2D graphics, Style style) {
        int plateAlpha = style.glass() ? 145 : 235;
        graphics.setColor(color(withAlpha(style.shadow(), plateAlpha)));
        graphics.fillRect(8, 7, 16, 18);
        graphics.fillRect(7, 8, 18, 16);
        graphics.setColor(color(withAlpha(style.mid(), style.glass() ? 150 : 220)));
        graphics.fillRect(9, 9, 14, 2);
        graphics.fillRect(9, 11, 2, 11);
        graphics.setColor(color(withAlpha(style.highlight(), style.glass() ? 120 : 180)));
        graphics.fillRect(11, 10, 8, 1);

        switch (style.motif()) {
            case TRANSMON -> {
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(11, 13, 10, 3);
                graphics.fillRect(11, 18, 10, 3);
                graphics.setColor(color(style.accent()));
                graphics.fillRect(14, 12, 4, 10);
                graphics.setColor(color(style.highlight()));
                graphics.fillRect(15, 13, 2, 2);
            }
            case WEAK -> {
                graphics.setColor(color(style.accent()));
                graphics.fillRect(11, 12, 10, 2);
                graphics.fillRect(11, 19, 10, 2);
                graphics.fillRect(11, 14, 2, 5);
                graphics.fillRect(19, 14, 2, 5);
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(14, 15, 4, 4);
                graphics.setColor(color(style.highlight()));
                graphics.fillRect(14, 15, 3, 1);
            }
            case STRONG -> {
                graphics.setColor(color(style.mid()));
                graphics.fillRect(13, 11, 6, 2);
                graphics.fillRect(11, 13, 10, 7);
                graphics.fillRect(13, 20, 6, 2);
                graphics.setColor(color(style.accent()));
                graphics.fillRect(13, 14, 3, 5);
                graphics.setColor(color(style.secondary()));
                graphics.fillRect(16, 14, 3, 5);
                graphics.setColor(color(style.shadow()));
                graphics.fillRect(15, 16, 2, 2);
            }
            case ENTANGLEMENT -> {
                // Handled by the original entanglement branch.
            }
        }
    }

    private static int textureNoise(int x, int y, int seed) {
        int value = x * 0x1F1F1F1F ^ y * 0x5F356495 ^ seed * 0x27D4EB2D;
        value ^= value >>> 15;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        return value;
    }

    private static String createAtlasJson(List<Integer> masks) {
        StringBuilder json = new StringBuilder("{\n  \"sources\": [\n");
        List<String> partPaths = QuantumDeviceTextureGenerator.entanglementPartPaths();
        int sourceCount = STYLES.size() + partPaths.size();
        int sourceIndex = 0;

        for (Style style : STYLES) {
            json.append("    {\n")
                    .append("      \"type\": \"minecraft:unstitch\",\n")
                    .append("      \"resource\": \"flux_overdrive:ctm/quantum/")
                    .append(style.sheetPath()).append("\",\n")
                    .append("      \"divisor_x\": 256,\n")
                    .append("      \"divisor_y\": 192,\n")
                    .append("      \"regions\": [\n");

            for (int index = 0; index < masks.size(); index++) {
                appendRegion(json, style.blockId(), String.format(Locale.ROOT, "%02x", masks.get(index)),
                        index % COLUMNS * TILE, index / COLUMNS * TILE, true);
            }
            appendRegion(json, style.blockId(), "item", 7 * TILE, 5 * TILE, false);
            json.append("      ]\n    }");
            if (++sourceIndex < sourceCount) {
                json.append(',');
            }
            json.append('\n');
        }

        for (String partPath : partPaths) {
            json.append("    {\n")
                    .append("      \"type\": \"minecraft:unstitch\",\n")
                    .append("      \"resource\": \"flux_overdrive:ctm/quantum/")
                    .append(partPath).append("/sheet\",\n")
                    .append("      \"divisor_x\": 256,\n")
                    .append("      \"divisor_y\": 192,\n")
                    .append("      \"regions\": [\n");
            for (int index = 0; index < masks.size(); index++) {
                appendRegion(json, "quantum/" + partPath,
                        String.format(Locale.ROOT, "%02x", masks.get(index)),
                        index % COLUMNS * TILE, index / COLUMNS * TILE,
                        index + 1 < masks.size());
            }
            json.append("      ]\n    }");
            if (++sourceIndex < sourceCount) {
                json.append(',');
            }
            json.append('\n');
        }
        return json.append("  ]\n}\n").toString();
    }

    private static void appendRegion(StringBuilder json, String blockId, String name,
                                     int x, int y, boolean trailingComma) {
        json.append("        { \"sprite\": \"flux_overdrive:block/ctm/")
                .append(blockId).append('/').append(name)
                .append("\", \"x\": ").append(x)
                .append(", \"y\": ").append(y)
                .append(", \"width\": 32, \"height\": 32 }");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static List<Integer> canonicalMasks() {
        List<Integer> masks = new ArrayList<>();
        for (int mask = 0; mask < 256; mask++) {
            if (normalize(mask) == mask) {
                masks.add(mask);
            }
        }
        return masks;
    }

    private static int normalize(int rawMask) {
        int mask = rawMask & (TOP | RIGHT | BOTTOM | LEFT);
        if (has(rawMask, TOP | RIGHT | TOP_RIGHT)) mask |= TOP_RIGHT;
        if (has(rawMask, RIGHT | BOTTOM | BOTTOM_RIGHT)) mask |= BOTTOM_RIGHT;
        if (has(rawMask, BOTTOM | LEFT | BOTTOM_LEFT)) mask |= BOTTOM_LEFT;
        if (has(rawMask, LEFT | TOP | TOP_LEFT)) mask |= TOP_LEFT;
        return mask;
    }

    private static int frameWidth(Motif motif) {
        return motif == Motif.TRANSMON ? 4 : 3;
    }

    private static boolean has(int mask, int bits) {
        return (mask & bits) == bits;
    }

    private static void copy(BufferedImage source, BufferedImage target, int x, int y) {
        Graphics2D graphics = target.createGraphics();
        graphics.drawImage(source, x, y, null);
        graphics.dispose();
    }

    private static Color color(int argb) {
        return new Color(argb, true);
    }

    private static int withAlpha(int argb, int alpha) {
        return (clamp(alpha) << 24) | argb & 0x00FFFFFF;
    }

    private static int alpha(int argb) {
        return argb >>> 24;
    }

    private static int red(int argb) {
        return argb >>> 16 & 0xFF;
    }

    private static int green(int argb) {
        return argb >>> 8 & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record Style(String blockId, String sheetPath, int seed, Motif motif, boolean glass,
                         int base, int shadow, int mid, int highlight, int accent, int secondary) {}

    private enum Motif {
        TRANSMON,
        WEAK,
        STRONG,
        ENTANGLEMENT
    }

    private GenerateConnectedTextures() {}
}

final class QuantumDeviceTextureGenerator {
    private static final int TILE = 16;
    private static final int ENTANGLEMENT_TILE = 32;
    private static final int CTM_COLUMNS = 8;
    private static final int CTM_ROWS = 6;
    private static final int CTM_TOP = 0x01;
    private static final int CTM_TOP_RIGHT = 0x02;
    private static final int CTM_RIGHT = 0x04;
    private static final int CTM_BOTTOM_RIGHT = 0x08;
    private static final int CTM_BOTTOM = 0x10;
    private static final int CTM_BOTTOM_LEFT = 0x20;
    private static final int CTM_LEFT = 0x40;
    private static final int CTM_TOP_LEFT = 0x80;
    private static final int ENTANGLEMENT_CASING_SEED = 21;
    // Keep these seeds tied to the former flat filenames so path cleanup does not redraw panels.
    private static final int SEED_BATTERY_CONTROLLER_FRAME = 0x024F1FA0;
    private static final int SEED_BATTERY_CONTROLLER_TOP = 0x717C2848;
    private static final int SEED_BATTERY_CONTROLLER_CORE = 0xBE01270C;
    private static final int SEED_ENTANGLEMENT_CONTROLLER_SIDE = 0xCFE13D97;
    private static final int SEED_ENTANGLEMENT_CONTROLLER_TOP = 0x59495915;
    private static final int SEED_CONSTRAINT_INTERFACE = 0xCAE6038A;
    private static final int SEED_CONSTRAINT_INTERFACE_3X3 = 0x19F12BF9;
    private static final int SEED_ENTANGLEMENT_CONSTRAINT_PORT = 0x354D724B;
    private static final int SEED_ENTANGLEMENT_ANTENNA_SIDE = 0xBF7F38E8;
    private static final int SEED_ENTANGLEMENT_ANTENNA_TOP = 0x69462FA4;
    private static final int SEED_SOLID_COOLING_BOTTOM = 0xEE24C939;
    private static final int SEED_SOLID_COOLING_SIDE = 0xB74977E5;
    private static final int SEED_SOLID_COOLING_TOP = 0x05E99D07;
    private static final int SEED_ADVANCED_COOLING_BOTTOM = 0x9161B838;
    private static final int SEED_ADVANCED_COOLING_SIDE = 0xC6A41AA4;
    private static final int SEED_ADVANCED_COOLING_TOP = 0xE5602668;
    private static final int SEED_QUENCH_FUSE_TIER_1_BOTTOM = 0x63D20B41;
    private static final int SEED_QUENCH_FUSE_TIER_1_SIDE = 0x1AF47C95;
    private static final int SEED_QUENCH_FUSE_TIER_1_TOP = 0x74B8E203;
    private static final int SEED_INPUT_PORT_FRONT = 0x3CF8646B;
    private static final int SEED_INPUT_PORT_SIDE = 0x7EAD3CAB;
    private static final int SEED_INPUT_PORT_TOP = 0x88372781;
    private static final int SEED_OUTPUT_PORT_FRONT = 0x2EEB270A;
    private static final int SEED_OUTPUT_PORT_SIDE = 0x0F76456C;
    private static final int SEED_OUTPUT_PORT_TOP = 0xE7B982A0;
    private static final int SEED_STABILITY_FEEDBACK_BOTTOM = 0x2F09B061;
    private static final int SEED_STABILITY_FEEDBACK_SIDE = 0x7C51E42A;
    private static final int SEED_STABILITY_FEEDBACK_TOP = 0x51F8A90D;
    private static final int SEED_TEMPERATURE_FEEDBACK_BOTTOM = 0x4A76D123;
    private static final int SEED_TEMPERATURE_FEEDBACK_SIDE = 0x1D93BF58;
    private static final int SEED_TEMPERATURE_FEEDBACK_TOP = 0x63C0E7A4;
    private static final int SEED_REDSTONE_PORT_FRONT = 0x5B72D90E;
    private static final int SEED_REDSTONE_PORT_SIDE = 0x248F6AC1;
    private static final int SEED_REDSTONE_PORT_TOP = 0x71A34DE9;

    private static final Palette BATTERY = new Palette(
            0xFF247A80, 0xFF18505A, 0xFF4FC2B8, 0xFFD7F4EA, 0xFF4CFFE0, 0xFFF1D264);
    private static final Palette ENTANGLEMENT = new Palette(
            0xFF3D4458, 0xFF1B2234, 0xFF77839F, 0xFFDCE2F0, 0xFF72EAD8, 0xFFD0A0F5);
    private static final Palette CONSTRAINT = new Palette(
            0xFF2F6EA5, 0xFF1D4A78, 0xFF62A2DA, 0xFFE0EEFF, 0xFF58D7FF, 0xFF63F1C1);
    private static final Palette CONSTRAINT_ARRAY = new Palette(
            0xFF754895, 0xFF60377D, 0xFFB47AC8, 0xFFF2E1FA, 0xFFC58CFF, 0xFF50EEE2);
    private static final Palette LINK = new Palette(
            0xFF51435D, 0xFF251B2D, 0xFF8F729A, 0xFFEADCE8, 0xFFF1CD74, 0xFFBC94F5);
    private static final Palette COOLING_SOLID = new Palette(
            0xFF237E8A, 0xFF174E58, 0xFF4DBBC6, 0xFFE0F4F3, 0xFF62F0FF, 0xFFF3DF75);
    private static final Palette COOLING_ADVANCED = new Palette(
            0xFF326FA8, 0xFF1D4A78, 0xFF69A4DB, 0xFFE3F0FF, 0xFF60D8FF, 0xFFC58FFF);
    private static final Palette QUENCH_FUSE_TIER_1 = new Palette(
            0xFF416F8E, 0xFF315D70, 0xFF78A9C0, 0xFFEAF4F8, 0xFFFF625B, 0xFFFFD166);
    private static final Palette INPUT = new Palette(
            0xFF315E59, 0xFF152D2B, 0xFF5A948B, 0xFFD4EEE9, 0xFF66F3D3, 0xFFB8F1E2);
    private static final Palette OUTPUT = new Palette(
            0xFF683D55, 0xFF321C2A, 0xFFAA6B8B, 0xFFF0DCE7, 0xFFFF80B9, 0xFFC89CF5);
    private static final Palette FEEDBACK_STABILITY = new Palette(
            0xFF3D9687, 0xFF286A64, 0xFF72C7B2, 0xFFDDF5EA, 0xFF68F5C4, 0xFFF3D56C);
    private static final Palette FEEDBACK_TEMPERATURE = new Palette(
            0xFFB96658, 0xFF74454A, 0xFFDD9175, 0xFFFFE9D6, 0xFFFFA05C, 0xFF69E5E0);
    private static final Palette REDSTONE = new Palette(
            0xFF713E4B, 0xFF361E2A, 0xFFB76A75, 0xFFF5DFE1, 0xFFFF5F75, 0xFF73E4D3);
    private static final Palette TIER_2_CASING = new Palette(
            0xFF365F9D, 0xFF244B7D, 0xFF6C9EE0, 0xFFE0ECFF, 0xFF55D5FF, 0xFFD08EFF);
    private static final Palette TIER_3_CASING = new Palette(
            0xFF82458F, 0xFF66366F, 0xFFC06DCB, 0xFFF8E4FA, 0xFF63F4DB, 0xFFFF8FCB);
    private static final List<EntanglementPart> ENTANGLEMENT_PARTS = List.of(
            new EntanglementPart("entanglement/controller/side", SEED_ENTANGLEMENT_CONTROLLER_SIDE,
                    ENTANGLEMENT, PanelMotif.LINK),
            new EntanglementPart("entanglement/controller/top", SEED_ENTANGLEMENT_CONTROLLER_TOP,
                    ENTANGLEMENT, PanelMotif.DUAL_RING),
            new EntanglementPart("entanglement/port/energy_constraint/all",
                    SEED_ENTANGLEMENT_CONSTRAINT_PORT, LINK, PanelMotif.LINK),
            new EntanglementPart("entanglement/antenna/side", SEED_ENTANGLEMENT_ANTENNA_SIDE,
                    ENTANGLEMENT, PanelMotif.ANTENNA),
            new EntanglementPart("entanglement/antenna/top", SEED_ENTANGLEMENT_ANTENNA_TOP,
                    ENTANGLEMENT, PanelMotif.BEACON),
            new EntanglementPart("entanglement/port/input/front", SEED_INPUT_PORT_FRONT,
                    INPUT, PanelMotif.PORT_IN),
            new EntanglementPart("entanglement/port/input/side", SEED_INPUT_PORT_SIDE,
                    INPUT, PanelMotif.RAILS),
            new EntanglementPart("entanglement/port/input/top", SEED_INPUT_PORT_TOP,
                    INPUT, PanelMotif.RING),
            new EntanglementPart("entanglement/port/output/front", SEED_OUTPUT_PORT_FRONT,
                    OUTPUT, PanelMotif.PORT_OUT),
            new EntanglementPart("entanglement/port/output/side", SEED_OUTPUT_PORT_SIDE,
                    OUTPUT, PanelMotif.RAILS),
            new EntanglementPart("entanglement/port/output/top", SEED_OUTPUT_PORT_TOP,
                    OUTPUT, PanelMotif.DUAL_RING),
            new EntanglementPart("entanglement/port/redstone/front", SEED_REDSTONE_PORT_FRONT,
                    REDSTONE, PanelMotif.LINK),
            new EntanglementPart("entanglement/port/redstone/side", SEED_REDSTONE_PORT_SIDE,
                    REDSTONE, PanelMotif.RAILS),
            new EntanglementPart("entanglement/port/redstone/top", SEED_REDSTONE_PORT_TOP,
                    REDSTONE, PanelMotif.RING)
    );

    static void write(Path root, Path ctmRoot, List<Integer> masks) throws IOException {
        write(root, "battery/controller/frame", SEED_BATTERY_CONTROLLER_FRAME, BATTERY, PanelMotif.FRAME);
        write(root, "battery/controller/top", SEED_BATTERY_CONTROLLER_TOP, BATTERY, PanelMotif.RING);
        write(root, "battery/controller/core", SEED_BATTERY_CONTROLLER_CORE, BATTERY, PanelMotif.CORE);

        write(root, "battery/energy_constraint_interface/all", SEED_CONSTRAINT_INTERFACE,
                CONSTRAINT, PanelMotif.RAILS);
        write(root, "battery/energy_constraint_interface/3x3/all", SEED_CONSTRAINT_INTERFACE_3X3,
                CONSTRAINT_ARRAY, PanelMotif.ARRAY);
        Files.deleteIfExists(root.resolve(
                "battery/energy_constraint_interface/3x3/assembled/all.png"));
        ImageIO.write(renderAssembledInterface(TIER_2_CASING, 0x3A32), "png",
                outputPath(root, "battery/energy_constraint_interface/3x3/assembled/tier_2").toFile());
        ImageIO.write(renderAssembledInterface(TIER_3_CASING, 0x3A33), "png",
                outputPath(root, "battery/energy_constraint_interface/3x3/assembled/tier_3").toFile());
        write(root, "battery/cooling_plugin/solid/bottom", SEED_SOLID_COOLING_BOTTOM,
                COOLING_SOLID, PanelMotif.CORE);
        write(root, "battery/cooling_plugin/solid/side", SEED_SOLID_COOLING_SIDE,
                COOLING_SOLID, PanelMotif.VENT);
        write(root, "battery/cooling_plugin/solid/top", SEED_SOLID_COOLING_TOP,
                COOLING_SOLID, PanelMotif.CRYO);
        write(root, "battery/cooling_plugin/advanced/bottom", SEED_ADVANCED_COOLING_BOTTOM,
                COOLING_ADVANCED, PanelMotif.ARRAY);
        write(root, "battery/cooling_plugin/advanced/side", SEED_ADVANCED_COOLING_SIDE,
                COOLING_ADVANCED, PanelMotif.CRYO);
        write(root, "battery/cooling_plugin/advanced/top", SEED_ADVANCED_COOLING_TOP,
                COOLING_ADVANCED, PanelMotif.RING);
        write(root, "battery/quench_fuse/tier_1/bottom", SEED_QUENCH_FUSE_TIER_1_BOTTOM,
                QUENCH_FUSE_TIER_1, PanelMotif.ARRAY);
        write(root, "battery/quench_fuse/tier_1/side", SEED_QUENCH_FUSE_TIER_1_SIDE,
                QUENCH_FUSE_TIER_1, PanelMotif.RAILS);
        write(root, "battery/quench_fuse/tier_1/top", SEED_QUENCH_FUSE_TIER_1_TOP,
                QUENCH_FUSE_TIER_1, PanelMotif.CORE);
        write(root, "battery/feedback_plugin/stability/bottom", SEED_STABILITY_FEEDBACK_BOTTOM,
                FEEDBACK_STABILITY, PanelMotif.ARRAY);
        write(root, "battery/feedback_plugin/stability/side", SEED_STABILITY_FEEDBACK_SIDE,
                FEEDBACK_STABILITY, PanelMotif.RAILS);
        write(root, "battery/feedback_plugin/stability/top", SEED_STABILITY_FEEDBACK_TOP,
                FEEDBACK_STABILITY, PanelMotif.DUAL_RING);
        write(root, "battery/feedback_plugin/temperature/bottom", SEED_TEMPERATURE_FEEDBACK_BOTTOM,
                FEEDBACK_TEMPERATURE, PanelMotif.ARRAY);
        write(root, "battery/feedback_plugin/temperature/side", SEED_TEMPERATURE_FEEDBACK_SIDE,
                FEEDBACK_TEMPERATURE, PanelMotif.RAILS);
        write(root, "battery/feedback_plugin/temperature/top", SEED_TEMPERATURE_FEEDBACK_TOP,
                FEEDBACK_TEMPERATURE, PanelMotif.CORE);

        for (EntanglementPart part : ENTANGLEMENT_PARTS) {
            writeEntanglementPart(root, ctmRoot, masks, part);
        }
    }

    static List<String> entanglementPartPaths() {
        return ENTANGLEMENT_PARTS.stream().map(EntanglementPart::path).toList();
    }

    private static void write(Path root, String path, int seed, Palette palette, PanelMotif motif)
            throws IOException {
        ImageIO.write(renderPanel(palette, motif, seed), "png", outputPath(root, path).toFile());
    }

    private static void writeEntanglementPart(Path root, Path ctmRoot, List<Integer> masks,
                                              EntanglementPart part) throws IOException {
        ImageIO.write(renderEntanglementPart(part.palette(), part.motif(), part.seed(), 0), "png",
                outputPath(root, part.path()).toFile());

        BufferedImage sheet = new BufferedImage(
                CTM_COLUMNS * ENTANGLEMENT_TILE, CTM_ROWS * ENTANGLEMENT_TILE,
                BufferedImage.TYPE_INT_ARGB);
        for (int index = 0; index < masks.size(); index++) {
            BufferedImage tile = renderEntanglementPart(
                    part.palette(), part.motif(), part.seed(), masks.get(index));
            Graphics2D graphics = sheet.createGraphics();
            graphics.drawImage(tile, index % CTM_COLUMNS * ENTANGLEMENT_TILE,
                    index / CTM_COLUMNS * ENTANGLEMENT_TILE, null);
            graphics.dispose();
        }
        ImageIO.write(sheet, "png", outputPath(ctmRoot, part.path() + "/sheet").toFile());
    }

    private static Path outputPath(Path root, String path) throws IOException {
        Path output = root.resolve(path + ".png");
        Files.createDirectories(output.getParent());
        return output;
    }

    private static BufferedImage renderPanel(Palette palette, PanelMotif motif, int seed) {
        BufferedImage image = new BufferedImage(TILE, TILE, BufferedImage.TYPE_INT_ARGB);
        fillBackground(image, palette, seed, motif);

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        if (isBatteryPanel(palette)) {
            drawBatteryPanelFrame(graphics, palette, motif, seed);
            drawBatteryPanelMotif(graphics, palette, motif, seed);
        } else {
            drawPanelFrame(graphics, palette, motif);
            drawPanelMotif(graphics, palette, motif);
        }
        graphics.dispose();
        return image;
    }

    private static BufferedImage renderEntanglementPart(Palette palette, PanelMotif motif,
                                                        int seed, int mask) {
        BufferedImage image = new BufferedImage(
                ENTANGLEMENT_TILE, ENTANGLEMENT_TILE, BufferedImage.TYPE_INT_ARGB);
        fillEntanglementPartBackground(image, palette, seed);

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        drawEntanglementCasingFrame(graphics, mask);
        graphics.scale(2.0, 2.0);
        drawEntanglementInnerFrame(graphics, palette, motif);
        drawPanelMotif(graphics, palette, motif);
        graphics.dispose();
        return image;
    }

    private static void fillEntanglementPartBackground(BufferedImage image, Palette palette, int seed) {
        int last = image.getWidth() - 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int edgeDistance = Math.min(Math.min(x, last - x), Math.min(y, last - y));
                float functionWeight = Math.clamp((edgeDistance - 2) / 5.0F, 0.0F, 1.0F);
                int casingBand = (((x - y + ENTANGLEMENT_TILE) >> 3)
                        + ENTANGLEMENT_CASING_SEED) & 3;
                int casingColor = shade(ENTANGLEMENT.base(), casingBand - 1);
                int functionGrain = (((x * 5 + y * 3 + seed) & 7) - 3) / 2;
                int functionColor = shade(palette.base(), functionGrain);
                image.setRGB(x, y, blend(casingColor, functionColor, functionWeight));
            }
        }
    }

    private static void drawEntanglementCasingFrame(Graphics2D graphics, int mask) {
        int width = 3;
        int last = ENTANGLEMENT_TILE - width;

        if (!hasCtm(mask, CTM_TOP)) {
            graphics.setColor(color(ENTANGLEMENT.shadow()));
            graphics.fillRect(0, 0, ENTANGLEMENT_TILE, width);
            graphics.setColor(color(ENTANGLEMENT.mid()));
            graphics.fillRect(0, 1, ENTANGLEMENT_TILE, width - 1);
            graphics.setColor(color(ENTANGLEMENT.highlight()));
            graphics.fillRect(0, width - 1, ENTANGLEMENT_TILE, 1);
        }
        if (!hasCtm(mask, CTM_RIGHT)) {
            graphics.setColor(color(ENTANGLEMENT.shadow()));
            graphics.fillRect(last, 0, width, ENTANGLEMENT_TILE);
            graphics.setColor(color(ENTANGLEMENT.mid()));
            graphics.fillRect(last, 0, width - 1, ENTANGLEMENT_TILE);
            graphics.setColor(color(ENTANGLEMENT.highlight()));
            graphics.fillRect(last, 0, 1, ENTANGLEMENT_TILE);
        }
        if (!hasCtm(mask, CTM_BOTTOM)) {
            graphics.setColor(color(ENTANGLEMENT.shadow()));
            graphics.fillRect(0, last, ENTANGLEMENT_TILE, width);
            graphics.setColor(color(ENTANGLEMENT.mid()));
            graphics.fillRect(0, last, ENTANGLEMENT_TILE, width - 1);
            graphics.setColor(color(ENTANGLEMENT.highlight()));
            graphics.fillRect(0, last, ENTANGLEMENT_TILE, 1);
        }
        if (!hasCtm(mask, CTM_LEFT)) {
            graphics.setColor(color(ENTANGLEMENT.shadow()));
            graphics.fillRect(0, 0, width, ENTANGLEMENT_TILE);
            graphics.setColor(color(ENTANGLEMENT.mid()));
            graphics.fillRect(1, 0, width - 1, ENTANGLEMENT_TILE);
            graphics.setColor(color(ENTANGLEMENT.highlight()));
            graphics.fillRect(width - 1, 0, 1, ENTANGLEMENT_TILE);
        }

        graphics.setColor(color(ENTANGLEMENT.accent()));
        if (!hasCtm(mask, CTM_TOP)) graphics.fillRect(7, width - 1, 7, 1);
        if (!hasCtm(mask, CTM_RIGHT)) graphics.fillRect(last, 7, 1, 7);
        if (!hasCtm(mask, CTM_BOTTOM)) graphics.fillRect(18, last, 7, 1);
        if (!hasCtm(mask, CTM_LEFT)) graphics.fillRect(width - 1, 18, 1, 7);

        graphics.setColor(color(ENTANGLEMENT.secondary()));
        if (!hasCtm(mask, CTM_TOP)) graphics.fillRect(19, width - 1, 5, 1);
        if (!hasCtm(mask, CTM_RIGHT)) graphics.fillRect(last, 19, 1, 5);
        if (!hasCtm(mask, CTM_BOTTOM)) graphics.fillRect(8, last, 5, 1);
        if (!hasCtm(mask, CTM_LEFT)) graphics.fillRect(width - 1, 8, 1, 5);

        graphics.setColor(color(withAlpha(ENTANGLEMENT.secondary(), 160)));
        if (hasCtm(mask, CTM_TOP | CTM_RIGHT) && !hasCtm(mask, CTM_TOP_RIGHT)) {
            graphics.drawLine(27, 0, 27, 4);
            graphics.drawLine(27, 4, 31, 4);
        }
        if (hasCtm(mask, CTM_RIGHT | CTM_BOTTOM) && !hasCtm(mask, CTM_BOTTOM_RIGHT)) {
            graphics.drawLine(27, 27, 31, 27);
            graphics.drawLine(27, 27, 27, 31);
        }
        if (hasCtm(mask, CTM_BOTTOM | CTM_LEFT) && !hasCtm(mask, CTM_BOTTOM_LEFT)) {
            graphics.drawLine(0, 27, 4, 27);
            graphics.drawLine(4, 27, 4, 31);
        }
        if (hasCtm(mask, CTM_LEFT | CTM_TOP) && !hasCtm(mask, CTM_TOP_LEFT)) {
            graphics.drawLine(4, 0, 4, 4);
            graphics.drawLine(0, 4, 4, 4);
        }
    }

    private static boolean hasCtm(int mask, int bits) {
        return (mask & bits) == bits;
    }

    private static void drawEntanglementInnerFrame(Graphics2D graphics, Palette palette,
                                                   PanelMotif motif) {
        graphics.setColor(color(blend(ENTANGLEMENT.shadow(), palette.shadow(), 0.55F)));
        graphics.drawRect(3, 3, 9, 9);
        graphics.setColor(color(blend(ENTANGLEMENT.mid(), palette.mid(), 0.6F)));
        graphics.drawLine(4, 4, 7, 4);
        graphics.drawLine(4, 4, 4, 7);
        graphics.drawLine(8, 11, 11, 11);
        graphics.drawLine(11, 8, 11, 11);

        int offset = 5 + motif.ordinal() % 3;
        graphics.setColor(color(palette.accent()));
        graphics.fillRect(offset, 3, 2, 1);
        graphics.setColor(color(palette.secondary()));
        graphics.fillRect(13 - offset, 12, 2, 1);
    }

    private static void fillBackground(BufferedImage image, Palette palette, int seed, PanelMotif motif) {
        if (isBatteryPanel(palette)) {
            fillBatteryBackground(image, palette, seed);
            return;
        }

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int grain = (((x * 5 + y * 3 + seed) & 7) - 3) / 2;
                image.setRGB(x, y, shade(palette.base(), grain));
            }
        }
    }

    private static boolean isBatteryPanel(Palette palette) {
        return palette == BATTERY || palette == CONSTRAINT || palette == CONSTRAINT_ARRAY
                || palette == COOLING_SOLID || palette == COOLING_ADVANCED
                || palette == FEEDBACK_STABILITY || palette == FEEDBACK_TEMPERATURE;
    }

    private static void fillBatteryBackground(BufferedImage image, Palette palette, int seed) {
        int edgeWidth = Math.max(3, Math.min(image.getWidth(), image.getHeight()) / 12);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int noise = panelNoise(x >> 1, y >> 1, seed ^ 0x4A7C15);
                int grain = (noise >>> 27 & 7) - 3;
                int edge = (y < edgeWidth ? 2 : y >= image.getHeight() - edgeWidth ? -2 : 0)
                        + (x < edgeWidth ? 1 : x >= image.getWidth() - edgeWidth ? -1 : 0);
                if ((noise & 0x1F) == 0) {
                    grain -= 3;
                } else if ((noise >>> 5 & 0x3F) == 0) {
                    grain += 3;
                }
                image.setRGB(x, y, shade(palette.base(), edge + grain));
            }
        }
    }

    private static void drawBatteryPanelFrame(Graphics2D graphics, Palette palette,
                                              PanelMotif motif, int seed) {
        graphics.setColor(color(palette.shadow()));
        graphics.fillRect(0, 0, TILE, 2);
        graphics.fillRect(0, 0, 2, TILE);
        graphics.fillRect(0, TILE - 2, TILE, 2);
        graphics.fillRect(TILE - 2, 0, 2, TILE);

        graphics.setColor(color(palette.mid()));
        graphics.fillRect(1, 1, TILE - 2, 1);
        graphics.fillRect(1, 2, 1, TILE - 4);
        graphics.setColor(color(palette.highlight()));
        graphics.fillRect(3, 1, 4, 1);
        graphics.fillRect(1, 3, 1, 4);

        graphics.setColor(color(shade(palette.base(), -18)));
        graphics.fillRect(3, 3, 10, 10);
        graphics.setColor(color(shade(palette.mid(), -8)));
        graphics.fillRect(4, 4, 8, 1);
        graphics.fillRect(4, 5, 1, 7);
        graphics.setColor(color(shade(palette.base(), -3)));
        graphics.fillRect(5, 5, 7, 7);

        int chip = 3 + Math.floorMod(seed, 5);
        graphics.setColor(color(palette.shadow()));
        graphics.fillRect(chip, 1, 2, 1);
        graphics.fillRect(13, 8 + Math.floorMod(seed >>> 4, 3), 1, 2);

        graphics.setColor(color(palette.accent()));
        switch (motif) {
            case FRAME -> graphics.fillRect(11, 2, 2, 1);
            case CORE, RING -> graphics.fillRect(2, 11, 1, 2);
            case RAILS, ARRAY -> graphics.fillRect(3, 13, 3, 1);
            case VENT, CRYO -> graphics.fillRect(10, 13, 3, 1);
            default -> {
                // Battery palettes only use the motifs above.
            }
        }
    }

    private static void drawBatteryPanelMotif(Graphics2D graphics, Palette palette,
                                              PanelMotif motif, int seed) {
        switch (motif) {
            case FRAME -> {
                graphics.setColor(color(palette.mid()));
                graphics.fillRect(5, 5, 6, 2);
                graphics.fillRect(5, 9, 6, 2);
                graphics.setColor(color(shade(palette.base(), -20)));
                graphics.fillRect(6, 7, 4, 2);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(5, 6, 3, 1);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(8, 9, 3, 1);
            }
            case CORE -> {
                graphics.setColor(color(palette.mid()));
                graphics.fillRect(6, 4, 4, 1);
                graphics.fillRect(4, 6, 8, 4);
                graphics.fillRect(6, 11, 4, 1);
                graphics.setColor(color(shade(palette.base(), -20)));
                graphics.fillRect(5, 6, 6, 5);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(6, 7, 4, 3);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(6, 7, 3, 1);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(9, 8, 2, 2);
            }
            case RING -> {
                drawBlockRing(graphics, palette, 4, 4, 8);
                graphics.setColor(color(shade(palette.base(), -20)));
                graphics.fillRect(6, 6, 4, 4);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(7, 7, 2, 2);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(5, 4, 3, 1);
            }
            case RAILS -> {
                graphics.setColor(color(shade(palette.base(), -20)));
                graphics.fillRect(4, 5, 8, 2);
                graphics.fillRect(4, 8, 8, 2);
                graphics.fillRect(4, 11, 8, 1);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(4, 5, 5, 1);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(6, 8, 6, 1);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(4, 11, 4, 1);
            }
            case ARRAY -> {
                graphics.setColor(color(shade(palette.base(), -20)));
                graphics.fillRect(4, 4, 3, 3);
                graphics.fillRect(9, 4, 3, 3);
                graphics.fillRect(4, 9, 3, 3);
                graphics.fillRect(9, 9, 3, 3);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(5, 5, 2, 2);
                graphics.fillRect(9, 9, 2, 2);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(9, 5, 2, 2);
                graphics.fillRect(5, 9, 2, 2);
                graphics.setColor(color(palette.mid()));
                graphics.fillRect(7, 7, 2, 2);
            }
            case VENT -> {
                graphics.setColor(color(shade(palette.base(), -20)));
                graphics.fillRect(4, 4, 8, 8);
                graphics.setColor(color(palette.mid()));
                graphics.fillRect(4, 4, 8, 1);
                graphics.fillRect(4, 4, 1, 8);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(5, 5, 6, 1);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(5, 8, 6, 2);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(6, 11, 4, 1);
            }
            case CRYO -> {
                graphics.setColor(color(shade(palette.base(), -20)));
                graphics.fillRect(5, 5, 6, 6);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(7, 4, 2, 8);
                graphics.fillRect(4, 7, 8, 2);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(5, 5, 2, 2);
                graphics.fillRect(9, 9, 2, 2);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(8, 5, 1, 3);
                graphics.fillRect(5, 8, 3, 1);
            }
            default -> drawPanelMotif(graphics, palette, motif);
        }

        if ((seed & 1) == 0) {
            graphics.setColor(color(shade(palette.highlight(), -25)));
            graphics.fillRect(3, 3, 1, 1);
        }
    }

    private static void drawBlockRing(Graphics2D graphics, Palette palette, int x, int y, int size) {
        graphics.setColor(color(palette.accent()));
        graphics.fillRect(x + 2, y, size - 4, 2);
        graphics.fillRect(x, y + 2, 2, size - 4);
        graphics.setColor(color(palette.secondary()));
        graphics.fillRect(x + size - 2, y + 2, 2, size - 4);
        graphics.fillRect(x + 2, y + size - 2, size - 4, 2);
    }

    private static void drawPanelFrame(Graphics2D graphics, Palette palette, PanelMotif motif) {
        graphics.setColor(color(palette.shadow()));
        graphics.drawRect(0, 0, TILE - 1, TILE - 1);

        graphics.setColor(color(palette.mid()));
        graphics.drawLine(1, 1, 5, 1);
        graphics.drawLine(1, 1, 1, 5);
        graphics.drawLine(10, 1, 14, 1);
        graphics.drawLine(14, 1, 14, 5);
        graphics.drawLine(1, 10, 1, 14);
        graphics.drawLine(1, 14, 5, 14);
        graphics.drawLine(14, 10, 14, 14);
        graphics.drawLine(10, 14, 14, 14);

        int offset = 3 + motif.ordinal() % 4;
        graphics.setColor(color(palette.accent()));
        graphics.fillRect(offset, 1, 4, 1);
        graphics.setColor(color(palette.secondary()));
        graphics.fillRect(TILE - offset - 4, TILE - 2, 4, 1);
    }

    private static void drawPanelMotif(Graphics2D graphics, Palette palette, PanelMotif motif) {
        switch (motif) {
            case FRAME -> {
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(4, 6, 8, 1);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(6, 9, 6, 1);
            }
            case CORE -> {
                graphics.setColor(color(palette.shadow()));
                graphics.fillRoundRect(4, 5, 8, 6, 3, 3);
                graphics.setColor(color(palette.highlight()));
                graphics.drawRoundRect(4, 5, 7, 5, 3, 3);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(5, 7, 6, 2);
            }
            case RING -> {
                graphics.setColor(color(palette.accent()));
                graphics.drawOval(4, 4, 7, 7);
                graphics.setColor(color(palette.secondary()));
                graphics.drawArc(3, 3, 9, 9, 205, 105);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(6, 12, 4, 1);
            }
            case DUAL_RING -> {
                graphics.setColor(color(palette.accent()));
                graphics.drawOval(3, 5, 6, 6);
                graphics.setColor(color(palette.secondary()));
                graphics.drawOval(7, 5, 6, 6);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(6, 7, 4, 2);
            }
            case RAILS -> {
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(3, 5, 9, 1);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(5, 8, 8, 1);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(3, 11, 7, 1);
            }
            case ARRAY -> {
                graphics.setColor(color(palette.accent()));
                graphics.drawRect(3, 4, 3, 3);
                graphics.drawRect(9, 4, 3, 3);
                graphics.setColor(color(palette.secondary()));
                graphics.drawRect(6, 9, 3, 3);
                graphics.drawLine(6, 6, 9, 6);
                graphics.drawLine(7, 8, 7, 9);
            }
            case LINK -> {
                graphics.setColor(color(palette.accent()));
                graphics.drawRoundRect(3, 5, 6, 6, 3, 3);
                graphics.setColor(color(palette.secondary()));
                graphics.drawRoundRect(7, 5, 6, 6, 3, 3);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(7, 7, 2, 2);
            }
            case ANTENNA -> {
                graphics.setColor(color(palette.secondary()));
                graphics.drawArc(3, 3, 10, 8, 205, 130);
                graphics.setColor(color(palette.accent()));
                graphics.drawArc(5, 6, 6, 5, 205, 130);
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(5, 12, 6, 1);
            }
            case BEACON -> {
                graphics.setColor(color(palette.accent()));
                graphics.drawOval(4, 4, 7, 7);
                graphics.setColor(color(palette.secondary()));
                graphics.drawOval(6, 6, 3, 3);
                graphics.setColor(color(palette.highlight()));
                graphics.drawLine(2, 8, 4, 8);
                graphics.drawLine(11, 8, 13, 8);
            }
            case VENT -> {
                graphics.setColor(color(palette.highlight()));
                graphics.fillRect(4, 4, 8, 1);
                graphics.setColor(color(palette.accent()));
                graphics.fillRect(3, 7, 9, 1);
                graphics.setColor(color(palette.secondary()));
                graphics.fillRect(4, 10, 8, 1);
                graphics.setColor(color(palette.mid()));
                graphics.fillRect(5, 13, 6, 1);
            }
            case CRYO -> {
                graphics.setColor(color(palette.accent()));
                graphics.drawLine(4, 4, 11, 11);
                graphics.drawLine(11, 4, 4, 11);
                graphics.setColor(color(palette.secondary()));
                graphics.drawLine(3, 8, 12, 8);
                graphics.setColor(color(palette.highlight()));
                graphics.drawLine(8, 3, 8, 12);
            }
            case PORT_IN -> drawPort(graphics, palette, true);
            case PORT_OUT -> drawPort(graphics, palette, false);
        }
    }

    private static void drawPort(Graphics2D graphics, Palette palette, boolean inward) {
        graphics.setColor(color(palette.accent()));
        drawChevron(graphics, 4, 8, inward);
        graphics.setColor(color(palette.secondary()));
        drawChevron(graphics, 11, 8, !inward);
        graphics.setColor(color(palette.highlight()));
        graphics.fillRect(7, 7, 2, 2);
    }

    private static void drawChevron(Graphics2D graphics, int centerX, int centerY, boolean pointsRight) {
        int direction = pointsRight ? 1 : -1;
        graphics.drawLine(centerX - 2 * direction, centerY - 3, centerX + direction, centerY);
        graphics.drawLine(centerX + direction, centerY, centerX - 2 * direction, centerY + 3);
    }

    private static BufferedImage renderAssembledInterface(Palette casing, int seed) {
        int size = 96;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        fillAssembledBackground(image, casing, seed);

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setColor(color(casing.shadow()));
        graphics.fillRect(0, 0, size, 4);
        graphics.fillRect(0, 0, 4, size);
        graphics.fillRect(0, size - 4, size, 4);
        graphics.fillRect(size - 4, 0, 4, size);
        graphics.setColor(color(casing.mid()));
        graphics.fillRect(2, 2, size - 4, 2);
        graphics.fillRect(2, 4, 2, size - 8);
        graphics.setColor(color(casing.highlight()));
        graphics.fillRect(16, 2, 24, 2);
        graphics.fillRect(2, 16, 2, 24);

        graphics.setColor(color(shade(CONSTRAINT_ARRAY.base(), -18)));
        graphics.fillRect(20, 20, 56, 56);
        graphics.setColor(color(CONSTRAINT_ARRAY.mid()));
        graphics.fillRect(22, 22, 52, 4);
        graphics.fillRect(22, 26, 4, 48);
        graphics.setColor(color(CONSTRAINT_ARRAY.shadow()));
        graphics.fillRect(26, 70, 48, 4);
        graphics.fillRect(70, 26, 4, 48);

        drawLargeBlockRing(graphics, CONSTRAINT_ARRAY, 28, 28, 40);
        graphics.setColor(color(CONSTRAINT_ARRAY.shadow()));
        graphics.fillRect(38, 38, 20, 20);
        graphics.setColor(color(CONSTRAINT_ARRAY.mid()));
        graphics.fillRect(42, 42, 12, 12);
        graphics.setColor(color(CONSTRAINT_ARRAY.accent()));
        graphics.fillRect(44, 44, 8, 8);
        graphics.setColor(color(CONSTRAINT_ARRAY.highlight()));
        graphics.fillRect(44, 44, 6, 2);

        graphics.setColor(color(CONSTRAINT_ARRAY.secondary()));
        graphics.fillRect(16, 44, 10, 8);
        graphics.fillRect(70, 44, 10, 8);
        graphics.fillRect(44, 16, 8, 10);
        graphics.fillRect(44, 70, 8, 10);
        graphics.setColor(color(CONSTRAINT_ARRAY.shadow()));
        graphics.fillRect(20, 46, 6, 4);
        graphics.fillRect(70, 46, 6, 4);

        graphics.setColor(color(shade(CONSTRAINT_ARRAY.mid(), -12)));
        graphics.fillRect(30, 10, 8, 4);
        graphics.fillRect(58, 82, 8, 4);
        graphics.fillRect(10, 58, 4, 8);
        graphics.fillRect(82, 30, 4, 8);
        graphics.dispose();
        return image;
    }

    private static void fillAssembledBackground(BufferedImage image, Palette casing, int seed) {
        int last = image.getWidth() - 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int edgeDistance = Math.min(Math.min(x, last - x), Math.min(y, last - y));
                float interfaceWeight = Math.clamp((edgeDistance - 6) / 18.0F, 0.0F, 1.0F);
                int noise = panelNoise(x >> 1, y >> 1, seed);
                int grain = (noise >>> 27 & 7) - 3;
                int casingColor = shade(casing.base(), grain);
                int interfaceColor = shade(CONSTRAINT_ARRAY.base(), grain);
                image.setRGB(x, y, blend(casingColor, interfaceColor, interfaceWeight));
            }
        }
    }

    private static int blend(int from, int to, float amount) {
        int alpha = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * amount);
        int red = Math.round((from >>> 16 & 0xFF) + ((to >>> 16 & 0xFF) - (from >>> 16 & 0xFF)) * amount);
        int green = Math.round((from >>> 8 & 0xFF) + ((to >>> 8 & 0xFF) - (from >>> 8 & 0xFF)) * amount);
        int blue = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static void drawLargeBlockRing(Graphics2D graphics, Palette palette,
                                           int x, int y, int size) {
        graphics.setColor(color(palette.accent()));
        graphics.fillRect(x + 5, y, size - 10, 4);
        graphics.fillRect(x, y + 5, 4, size - 10);
        graphics.setColor(color(palette.secondary()));
        graphics.fillRect(x + size - 4, y + 5, 4, size - 10);
        graphics.fillRect(x + 5, y + size - 4, size - 10, 4);
        graphics.setColor(color(palette.highlight()));
        graphics.fillRect(x + 6, y, 5, 1);
        graphics.fillRect(x, y + 6, 1, 5);
    }

    private static int panelNoise(int x, int y, int seed) {
        int value = x * 0x1F1F1F1F ^ y * 0x5F356495 ^ seed * 0x27D4EB2D;
        value ^= value >>> 15;
        value *= 0x85EBCA6B;
        value ^= value >>> 13;
        return value;
    }

    private static int shade(int argb, int amount) {
        int alpha = argb >>> 24;
        int red = clamp((argb >>> 16 & 0xFF) + amount);
        int green = clamp((argb >>> 8 & 0xFF) + amount);
        int blue = clamp((argb & 0xFF) + amount);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int withAlpha(int argb, int alpha) {
        return clamp(alpha) << 24 | argb & 0x00FFFFFF;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static Color color(int argb) {
        return new Color(argb, true);
    }

    private enum PanelMotif {
        FRAME,
        CORE,
        RING,
        DUAL_RING,
        RAILS,
        ARRAY,
        LINK,
        ANTENNA,
        BEACON,
        VENT,
        CRYO,
        PORT_IN,
        PORT_OUT
    }

    private record Palette(int base, int shadow, int mid, int highlight, int accent, int secondary) {}

    private record EntanglementPart(String path, int seed, Palette palette, PanelMotif motif) {}

    private QuantumDeviceTextureGenerator() {}
}

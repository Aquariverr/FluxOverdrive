package com.aquariverr.fluxod.client.model.ctm;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectedTextureAssetsTest {
    private static final double MIN_MEAN_LUMINANCE = 65.0;
    private static final double MIN_MEAN_CHROMA = 24.0;
    private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
    private static final Path CTM_ROOT = RESOURCE_ROOT.resolve(
            "assets/flux_overdrive/textures/ctm/quantum");
    private static final List<CtmSpec> STYLES = List.of(
            new CtmSpec("transmon_qubit_chain_casing", "battery/casing/transmon_qubit_chain/sheet", false, true),
            new CtmSpec("transmon_qubit_chain_casing_glass", "battery/casing/transmon_qubit_chain_glass/sheet", true, true),
            new CtmSpec("weak_coupling_cavity_qed_casing", "battery/casing/weak_coupling_cavity_qed/sheet", false, true),
            new CtmSpec("weak_coupling_cavity_qed_casing_glass", "battery/casing/weak_coupling_cavity_qed_glass/sheet", true, true),
            new CtmSpec("strong_coupling_cavity_qed_casing", "battery/casing/strong_coupling_cavity_qed/sheet", false, true),
            new CtmSpec("strong_coupling_cavity_qed_casing_glass", "battery/casing/strong_coupling_cavity_qed_glass/sheet", true, true),
            new CtmSpec("quantum_entanglement_casing", "entanglement/casing/sheet", false, true),
            part("entanglement/controller/side"),
            part("entanglement/controller/top"),
            part("entanglement/antenna/side"),
            part("entanglement/antenna/top"),
            part("entanglement/port/energy_constraint/all"),
            part("entanglement/port/input/front"),
            part("entanglement/port/input/side"),
            part("entanglement/port/input/top"),
            part("entanglement/port/output/front"),
            part("entanglement/port/output/side"),
            part("entanglement/port/output/top"),
            part("entanglement/port/redstone/front"),
            part("entanglement/port/redstone/side"),
            part("entanglement/port/redstone/top")
    );
    private static final List<String> LEGACY_SHEETS = List.of(
            "transmon_qubit_chain_casing",
            "transmon_qubit_chain_casing_glass",
            "weak_coupling_cavity_qed_casing",
            "weak_coupling_cavity_qed_casing_glass",
            "strong_coupling_cavity_qed_casing",
            "strong_coupling_cavity_qed_casing_glass",
            "quantum_entanglement_casing"
    );

    @Test
    void atlasesHaveExpectedDimensionsAndAlpha() throws IOException {
        for (CtmSpec style : STYLES) {
            Path path = CTM_ROOT.resolve(style.sheetPath() + ".png");
            BufferedImage image = ImageIO.read(path.toFile());
            assertEquals(256, image.getWidth(), style.blockId());
            assertEquals(192, image.getHeight(), style.blockId());

            boolean hasPartialAlpha = false;
            double luminance = 0.0;
            double chroma = 0.0;
            double[] luminances = new double[image.getWidth() * image.getHeight()];
            int darkPixels = 0;
            int colorfulPixels = 0;
            int pixelIndex = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    int alpha = pixel >>> 24;
                    if (alpha > 0 && alpha < 255) {
                        hasPartialAlpha = true;
                    }
                    int red = pixel >>> 16 & 0xFF;
                    int green = pixel >>> 8 & 0xFF;
                    int blue = pixel & 0xFF;
                    double pixelLuminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
                    int pixelChroma = Math.max(red, Math.max(green, blue))
                            - Math.min(red, Math.min(green, blue));
                    luminances[pixelIndex++] = pixelLuminance;
                    luminance += pixelLuminance;
                    chroma += pixelChroma;
                    if (pixelLuminance < 55.0) darkPixels++;
                    if (pixelLuminance >= 60.0 && pixelChroma >= 45) colorfulPixels++;
                }
            }
            assertEquals(style.glass(), hasPartialAlpha, style.blockId());
            int pixels = image.getWidth() * image.getHeight();
            assertTrue(luminance / pixels >= MIN_MEAN_LUMINANCE,
                    style.blockId() + " is too dark");
            assertTrue(chroma / pixels >= MIN_MEAN_CHROMA,
                    style.blockId() + " is too gray");
            if (style.blockId().contains("qubit") || style.blockId().contains("coupling")) {
                Arrays.sort(luminances);
                assertTrue(luminances[pixels / 2] >= 65.0,
                        style.blockId() + " has a dark visual midpoint");
                assertTrue(darkPixels <= pixels * 0.20,
                        style.blockId() + " contains too many dark pixels");
                if (!style.glass()) {
                    assertTrue(colorfulPixels >= pixels * 0.75,
                            style.blockId() + " contains too few visibly colored pixels");
                }
            }
        }

        Set<String> expected = STYLES.stream()
                .map(style -> style.sheetPath() + ".png")
                .collect(Collectors.toSet());
        try (var files = Files.walk(CTM_ROOT)) {
            Set<String> actual = files
                    .filter(Files::isRegularFile)
                    .map(CTM_ROOT::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(path -> path.endsWith(".png"))
                    .collect(Collectors.toSet());
            assertEquals(expected, actual, "Unexpected quantum CTM sheets");
        }

        Path legacyRoot = CTM_ROOT.getParent();
        for (String id : LEGACY_SHEETS) {
            assertTrue(Files.notExists(legacyRoot.resolve(id + ".png")),
                    "Legacy flat CTM sheet remains: " + id);
        }
    }

    @Test
    void atlasDefinitionContainsEveryCanonicalStateAndItemSprite() throws IOException {
        String atlas = Files.readString(RESOURCE_ROOT.resolve("assets/minecraft/atlases/blocks.json"));
        for (CtmSpec style : STYLES) {
            String id = style.blockId();
            assertTrue(atlas.contains("\"resource\": \"flux_overdrive:ctm/quantum/"
                    + style.sheetPath() + "\""), id + " sheet resource");
            for (int mask = 0; mask < 256; mask++) {
                if (ConnectedTextureMask.isCanonical(mask)) {
                    assertTrue(atlas.contains("flux_overdrive:block/ctm/" + id + "/"
                            + ConnectedTextureMask.spriteName(mask)), id + " mask " + mask);
                }
            }
            if (style.hasItem()) {
                assertTrue(atlas.contains("flux_overdrive:block/ctm/" + id + "/item"), id);
            }
        }
    }

    @Test
    void entanglementPartCtmKeepsItsPanelAndUsesEveryCasingBorder() throws IOException {
        BufferedImage casing = ImageIO.read(CTM_ROOT.resolve("entanglement/casing/sheet.png").toFile());
        List<Integer> masks = canonicalMasks();

        for (CtmSpec style : STYLES) {
            if (!style.blockId().startsWith("quantum/entanglement/")) continue;
            String partPath = style.blockId().substring("quantum/".length());
            BufferedImage base = ImageIO.read(RESOURCE_ROOT.resolve(
                    "assets/flux_overdrive/textures/block/quantum/" + partPath + ".png").toFile());
            BufferedImage sheet = ImageIO.read(CTM_ROOT.resolve(style.sheetPath() + ".png").toFile());

            for (int index = 0; index < masks.size(); index++) {
                int tileX = index % 8 * 32;
                int tileY = index / 8 * 32;
                for (int y = 0; y < 32; y++) {
                    for (int x = 0; x < 32; x++) {
                        int edgeDistance = Math.min(Math.min(x, 31 - x), Math.min(y, 31 - y));
                        if (edgeDistance < 3) {
                            assertEquals(casing.getRGB(tileX + x, tileY + y),
                                    sheet.getRGB(tileX + x, tileY + y),
                                    style.blockId() + " mask " + masks.get(index)
                                            + " border at " + x + "," + y);
                        } else if (edgeDistance >= 5) {
                            assertEquals(base.getRGB(x, y), sheet.getRGB(tileX + x, tileY + y),
                                    style.blockId() + " mask " + masks.get(index)
                                            + " panel at " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyEntanglementPartIsWrappedAndOrientedWithWorldLockedUvs() throws IOException {
        String events = Files.readString(Path.of(
                "src/main/java/com/aquariverr/fluxod/client/model/ctm/ConnectedTextureClientEvents.java"));
        for (String block : List.of(
                "QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER",
                "QUANTUM_ENTANGLEMENT_ANTENNA",
                "QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT",
                "QUANTUM_ENTANGLEMENT_INPUT_PORT",
                "QUANTUM_ENTANGLEMENT_OUTPUT_PORT",
                "QUANTUM_ENTANGLEMENT_REDSTONE_PORT")) {
            assertTrue(events.contains("wrapEntanglementPart(event, RegistryBlocks." + block + ".get()"),
                    block + " CTM wrapper");
        }

        Path blockstates = RESOURCE_ROOT.resolve("assets/flux_overdrive/blockstates");
        for (String blockstate : List.of(
                "quantum_battery_entanglement_controller.json",
                "quantum_energy_constraint_entanglement_port.json",
                "quantum_entanglement_input_port.json",
                "quantum_entanglement_output_port.json",
                "quantum_entanglement_redstone_port.json")) {
            String json = Files.readString(blockstates.resolve(blockstate));
            assertEquals(4, count(json, "\"uvlock\": true"), blockstate);
        }
    }

    private static int count(String text, String needle) {
        int count = 0;
        for (int offset = 0; (offset = text.indexOf(needle, offset)) >= 0; offset += needle.length()) {
            count++;
        }
        return count;
    }

    private static List<Integer> canonicalMasks() {
        return java.util.stream.IntStream.range(0, 256)
                .filter(ConnectedTextureMask::isCanonical)
                .boxed()
                .toList();
    }

    private static CtmSpec part(String path) {
        return new CtmSpec("quantum/" + path, path + "/sheet", false, false);
    }

    private record CtmSpec(String blockId, String sheetPath, boolean glass, boolean hasItem) {}
}

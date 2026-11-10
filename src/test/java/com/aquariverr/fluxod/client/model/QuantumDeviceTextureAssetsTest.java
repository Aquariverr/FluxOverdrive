package com.aquariverr.fluxod.client.model;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumDeviceTextureAssetsTest {
    private static final double MIN_MEAN_LUMINANCE = 65.0;
    private static final double MIN_MEAN_CHROMA = 24.0;
    private static final Path TEXTURE_ROOT = Path.of(
            "src/main/resources/assets/flux_overdrive/textures/block/quantum");
    private static final List<TextureSpec> TEXTURES = List.of(
            texture("battery/controller/frame"),
            texture("battery/controller/top"),
            texture("battery/controller/core"),
            entanglementTexture("entanglement/controller/side"),
            entanglementTexture("entanglement/controller/top"),
            texture("battery/energy_constraint_interface/all"),
            texture("battery/energy_constraint_interface/3x3/all"),
            new TextureSpec("battery/energy_constraint_interface/3x3/assembled/tier_2", 96),
            new TextureSpec("battery/energy_constraint_interface/3x3/assembled/tier_3", 96),
            entanglementTexture("entanglement/port/energy_constraint/all"),
            entanglementTexture("entanglement/antenna/side"),
            entanglementTexture("entanglement/antenna/top"),
            texture("battery/cooling_plugin/solid/bottom"),
            texture("battery/cooling_plugin/solid/side"),
            texture("battery/cooling_plugin/solid/top"),
            texture("battery/cooling_plugin/advanced/bottom"),
            texture("battery/cooling_plugin/advanced/side"),
            texture("battery/cooling_plugin/advanced/top"),
            texture("battery/quench_fuse/tier_1/bottom"),
            texture("battery/quench_fuse/tier_1/side"),
            texture("battery/quench_fuse/tier_1/top"),
            texture("battery/feedback_plugin/stability/bottom"),
            texture("battery/feedback_plugin/stability/side"),
            texture("battery/feedback_plugin/stability/top"),
            texture("battery/feedback_plugin/temperature/bottom"),
            texture("battery/feedback_plugin/temperature/side"),
            texture("battery/feedback_plugin/temperature/top"),
            entanglementTexture("entanglement/port/input/front"),
            entanglementTexture("entanglement/port/input/side"),
            entanglementTexture("entanglement/port/input/top"),
            entanglementTexture("entanglement/port/output/front"),
            entanglementTexture("entanglement/port/output/side"),
            entanglementTexture("entanglement/port/output/top"),
            entanglementTexture("entanglement/port/redstone/front"),
            entanglementTexture("entanglement/port/redstone/side"),
            entanglementTexture("entanglement/port/redstone/top")
    );
    private static final List<String> LEGACY_IDS = List.of(
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

    @Test
    void texturesHaveExpectedDimensionsAreOpaqueAndDoNotDuplicate() throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Set<String> hashes = new HashSet<>();

        for (TextureSpec texture : TEXTURES) {
            Path path = TEXTURE_ROOT.resolve(texture.path() + ".png");
            BufferedImage image = ImageIO.read(path.toFile());
            assertEquals(texture.size(), image.getWidth(), texture.path());
            assertEquals(texture.size(), image.getHeight(), texture.path());

            double luminance = 0.0;
            double chroma = 0.0;
            double[] luminances = new double[image.getWidth() * image.getHeight()];
            int darkPixels = 0;
            int colorfulPixels = 0;
            int pixelIndex = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int pixel = image.getRGB(x, y);
                    assertEquals(255, pixel >>> 24, texture.path() + " alpha");
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
            int pixels = image.getWidth() * image.getHeight();
            assertTrue(luminance / pixels >= MIN_MEAN_LUMINANCE,
                    texture.path() + " is too dark");
            assertTrue(chroma / pixels >= MIN_MEAN_CHROMA,
                    texture.path() + " is too gray");
            if (texture.path().startsWith("battery/")) {
                Arrays.sort(luminances);
                assertTrue(luminances[pixels / 2] >= 65.0,
                        texture.path() + " has a dark visual midpoint");
                assertTrue(darkPixels <= pixels * 0.20,
                        texture.path() + " contains too many dark pixels");
                assertTrue(colorfulPixels >= pixels * 0.75,
                        texture.path() + " contains too few visibly colored pixels");
            }

            String hash = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
            assertTrue(hashes.add(hash), texture.path() + " duplicates another quantum device texture");
        }

        Set<String> expected = TEXTURES.stream()
                .map(texture -> texture.path() + ".png")
                .collect(Collectors.toSet());
        // The space-aperture artwork has its own dimensions/visibility checks in MiniatureUniverseAssetsTest.
        expected.add("battery/miniature_universe/core.png");
        try (var files = Files.walk(TEXTURE_ROOT)) {
            Set<String> actual = files
                    .filter(Files::isRegularFile)
                    .map(TEXTURE_ROOT::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .filter(path -> path.endsWith(".png"))
                    .collect(Collectors.toSet());
            assertEquals(expected, actual, "Unexpected quantum texture files");
        }

        Path legacyRoot = TEXTURE_ROOT.getParent();
        for (String id : LEGACY_IDS) {
            assertTrue(Files.notExists(legacyRoot.resolve(id + ".png")),
                    "Legacy flat texture remains: " + id);
        }
    }

    @Test
    void formedInterfaceUsesTierSpecificSingleFaceModels() throws IOException {
        Path modelRoot = Path.of("src/main/resources/assets/flux_overdrive/models/block");
        String shared = Files.readString(modelRoot.resolve(
                "quantum_energy_constraint_interface_3x3_assembled.json"));
        assertTrue(shared.contains("\"ambientocclusion\": false"));
        assertTrue(shared.contains("\"from\": [-16, -16, -0.02]"));
        assertTrue(shared.contains("\"to\": [32, 32, 0]"));
        assertTrue(shared.contains("\"north\":"));
        for (String face : List.of("down", "up", "south", "west", "east")) {
            assertTrue(!shared.contains("\"" + face + "\":"), "Unexpected assembled face " + face);
        }

        for (int tier : new int[]{2, 3}) {
            String tierModel = Files.readString(modelRoot.resolve(
                    "quantum_energy_constraint_interface_3x3_assembled_tier_" + tier + ".json"));
            assertTrue(tierModel.contains(
                    "\"parent\": \"flux_overdrive:block/quantum_energy_constraint_interface_3x3_assembled\""));
            assertTrue(tierModel.contains(
                    "\"panel\": \"flux_overdrive:block/quantum/battery/energy_constraint_interface/3x3/assembled/tier_"
                            + tier + "\""));
        }
    }

    @Test
    void entanglementPartsUseTheSameBorderAsTheCasing() throws IOException {
        BufferedImage casingSheet = ImageIO.read(Path.of(
                "src/main/resources/assets/flux_overdrive/textures/ctm/quantum/entanglement/casing/sheet.png")
                .toFile());

        for (TextureSpec texture : TEXTURES) {
            if (!texture.path().startsWith("entanglement/")) continue;
            BufferedImage image = ImageIO.read(TEXTURE_ROOT.resolve(texture.path() + ".png").toFile());
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int edgeDistance = Math.min(Math.min(x, 31 - x), Math.min(y, 31 - y));
                    if (edgeDistance < 3) {
                        assertEquals(casingSheet.getRGB(x, y), image.getRGB(x, y),
                                texture.path() + " perimeter at " + x + "," + y);
                    }
                }
            }
        }
    }

    private static TextureSpec texture(String path) {
        return new TextureSpec(path, 16);
    }

    private static TextureSpec entanglementTexture(String path) {
        return new TextureSpec(path, 32);
    }

    private record TextureSpec(String path, int size) {}
}

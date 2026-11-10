package com.aquariverr.fluxod;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluxExtractCrystalAssetsTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void hasEveryRegisteredBlockResource() {
        for (String path : new String[] {
                "assets/flux_overdrive/blockstates/flux_extract_crystal.json",
                "assets/flux_overdrive/models/block/flux_extract_crystal.json",
                "assets/flux_overdrive/models/item/flux_extract_crystal.json",
                "assets/flux_overdrive/textures/block/flux_crystal_parts.png",
                "assets/flux_overdrive/textures/block/flux_crystal_particle.png",
                "data/flux_overdrive/recipe/flux_extract_crystal.json",
                "data/flux_overdrive/loot_table/blocks/flux_extract_crystal.json"
        }) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve(path)), path);
        }
    }

    @Test
    void recipeUsesFluxPlugAndLootCopiesDeviceConfiguration() throws IOException {
        String recipe = Files.readString(RESOURCES.resolve(
                "data/flux_overdrive/recipe/flux_extract_crystal.json"));
        String loot = Files.readString(RESOURCES.resolve(
                "data/flux_overdrive/loot_table/blocks/flux_extract_crystal.json"));

        assertTrue(recipe.contains("\"item\": \"fluxnetworks:flux_plug\""));
        assertTrue(recipe.contains("\"id\": \"flux_overdrive:flux_extract_crystal\""));
        assertTrue(loot.contains("fluxnetworks:device_config"));
        assertTrue(loot.contains("fluxnetworks:stored_energy"));
    }

    @Test
    void extractModelsUseSharedTexturesAndNetworkTint() throws IOException {
        for (String id : new String[] {"flux_extract_crystal", "flux_auto_extract_crystal"}) {
            var model = JsonParser.parseString(Files.readString(RESOURCES.resolve(
                    "assets/flux_overdrive/models/block/" + id + ".json"))).getAsJsonObject();
            var textures = model.getAsJsonObject("textures");
            assertEquals("flux_overdrive:block/flux_crystal_parts", textures.get("parts").getAsString());
            assertEquals("flux_overdrive:block/flux_crystal_particle", textures.get("particle").getAsString());
            for (var textureId : textures.asMap().values()) {
                String path = textureId.getAsString().replace("flux_overdrive:", "");
                BufferedImage texture = ImageIO.read(RESOURCES.resolve(
                        "assets/flux_overdrive/textures/" + path + ".png").toFile());
                assertTrue(texture != null && texture.getWidth() > 0 && texture.getHeight() > 0, path);
            }
            int tintedFaces = 0;
            for (var element : model.getAsJsonArray("elements")) {
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").asMap().values()) {
                    var definition = face.getAsJsonObject();
                    assertEquals("#parts", definition.get("texture").getAsString());
                    if (definition.has("tintindex")) {
                        assertEquals(1, definition.get("tintindex").getAsInt());
                        tintedFaces++;
                    }
                }
            }
            assertTrue(tintedFaces > 0, id + " must retain network-colored crystal faces");
        }
    }

    @Test
    void translationsAndMineableTagContainCrystal() throws IOException {
        String english = Files.readString(RESOURCES.resolve("assets/flux_overdrive/lang/en_us.json"));
        String chinese = Files.readString(RESOURCES.resolve("assets/flux_overdrive/lang/zh_cn.json"));
        String mineable = Files.readString(RESOURCES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"));

        assertTrue(english.contains("Flux Extract Crystal"));
        assertTrue(chinese.contains("通量提取水晶"));
        assertTrue(mineable.contains("flux_overdrive:flux_extract_crystal"));
    }

    @Test
    void automaticRecipeUsesManualExtractCrystalAsItsCore() throws IOException {
        String recipe = Files.readString(RESOURCES.resolve(
                "data/flux_overdrive/recipe/flux_auto_extract_crystal.json"));

        assertTrue(recipe.contains("\"item\": \"flux_overdrive:flux_extract_crystal\""));
        assertTrue(recipe.contains("\"id\": \"flux_overdrive:flux_auto_extract_crystal\""));
    }
}

package com.aquariverr.fluxod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniatureUniverseAssetsTest {

    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final String ID = "flux_overdrive:miniature_universe";
    private static final String MODEL = "flux_overdrive:block/miniature_universe";

    @Test
    void lootKeepsAllStateInOnePluginItem() throws IOException {
        JsonObject loot = readJson("data/flux_overdrive/loot_table/blocks/miniature_universe.json");
        assertEquals("minecraft:block", loot.get("type").getAsString());
        assertFalse(loot.has("functions"));
        JsonArray pools = loot.getAsJsonArray("pools");
        assertEquals(1, pools.size());
        JsonObject pool = pools.get(0).getAsJsonObject();
        assertEquals(1.0, pool.get("rolls").getAsDouble());
        assertFalse(pool.has("bonus_rolls"));
        assertFalse(pool.has("functions"));
        JsonArray entries = pool.getAsJsonArray("entries");
        assertEquals(1, entries.size());
        JsonObject entry = entries.get(0).getAsJsonObject();
        assertEquals("minecraft:item", entry.get("type").getAsString());
        assertEquals(ID, entry.get("name").getAsString());
        JsonArray functions = entry.getAsJsonArray("functions");
        assertEquals(1, functions.size());
        JsonObject copy = functions.get(0).getAsJsonObject();
        assertEquals("minecraft:copy_components", copy.get("function").getAsString());
        assertEquals("block_entity", copy.get("source").getAsString());
        assertEquals(List.of("flux_overdrive:miniature_universe_state"),
                copy.getAsJsonArray("include").asList().stream().map(JsonElement::getAsString).toList());
    }

    @Test
    void modelsResolveToVisibleExistingQuantumTextures() throws IOException {
        JsonObject blockstate = readJson("assets/flux_overdrive/blockstates/miniature_universe.json");
        assertEquals(MODEL, blockstate.getAsJsonObject("variants").getAsJsonObject("")
                .get("model").getAsString());
        JsonObject item = readJson("assets/flux_overdrive/models/item/miniature_universe.json");
        assertEquals(MODEL, item.get("parent").getAsString());
        JsonObject block = readJson("assets/flux_overdrive/models/block/miniature_universe.json");
        assertEquals("minecraft:block/cube_bottom_top", block.get("parent").getAsString());
        JsonObject textures = block.getAsJsonObject("textures");
        for (String face : List.of("bottom", "side", "top")) {
            String texture = textures.get(face).getAsString();
            assertTrue(texture.startsWith("flux_overdrive:block/quantum/"), texture);
            Path path = RESOURCES.resolve("assets/flux_overdrive/textures/"
                    + texture.substring("flux_overdrive:".length()) + ".png");
            assertTrue(Files.isRegularFile(path), texture);
            BufferedImage image = ImageIO.read(path.toFile());
            assertNotNull(image, texture);
            if (texture.endsWith("miniature_universe/core")) {
                assertEquals(32, image.getWidth());
                assertEquals(32, image.getHeight());
                for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) assertEquals(255, image.getRGB(x, y) >>> 24);
            }
            boolean visible = false;
            for (int y = 0; y < image.getHeight() && !visible; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) != 0) {
                        visible = true;
                        break;
                    }
                }
            }
            assertTrue(visible, texture + " has no visible pixels");
        }
    }

    @Test
    void miningAndUnavailableStatesAreLocalized() throws IOException {
        JsonObject mineable = readJson("data/minecraft/tags/block/mineable/pickaxe.json");
        assertTrue(mineable.getAsJsonArray("values").asList().stream()
                .map(JsonElement::getAsString).anyMatch(ID::equals));
        for (String locale : List.of("en_us", "zh_cn")) {
            JsonObject language = readJson("assets/flux_overdrive/lang/" + locale + ".json");
            for (String key : List.of(
                    "block.flux_overdrive.miniature_universe",
                    "tooltip.flux_overdrive.miniature_universe.ready",
                    "message.flux_overdrive.miniature_universe.ready",
                    "message.flux_overdrive.miniature_universe.unknown_format",
                    "message.flux_overdrive.miniature_universe.invalid_state")) {
                assertTrue(language.has(key), locale + ": " + key);
                assertFalse(language.get(key).getAsString().isBlank(), locale + ": " + key);
            }
        }
    }

    @Test
    void wipCraftingAndAllFourUpgradeRecipesAreUnavailable() {
        assertFalse(Files.exists(RESOURCES.resolve("data/flux_overdrive/recipe/miniature_universe.json")));
        for (int size : List.of(3, 5, 7, 9)) {
            assertFalse(Files.exists(RESOURCES.resolve("data/flux_overdrive/recipe/miniature_upgrade/"
                    + size + "_to_" + (size + 2) + ".json")));
        }
    }

    private static JsonObject readJson(String path) throws IOException {
        return JsonParser.parseString(Files.readString(RESOURCES.resolve(path))).getAsJsonObject();
    }
}

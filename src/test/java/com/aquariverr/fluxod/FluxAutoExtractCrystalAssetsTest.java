package com.aquariverr.fluxod;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FluxAutoExtractCrystalAssetsTest {

    private static final Path RESOURCES = Path.of("src/main/resources");

    @Test
    void hasEveryRegisteredBlockResource() {
        for (String path : new String[] {
                "assets/flux_overdrive/blockstates/flux_auto_extract_crystal.json",
                "assets/flux_overdrive/models/block/flux_auto_extract_crystal.json",
                "assets/flux_overdrive/models/item/flux_auto_extract_crystal.json",
                "data/flux_overdrive/recipe/flux_auto_extract_crystal.json",
                "data/flux_overdrive/loot_table/blocks/flux_auto_extract_crystal.json"
        }) {
            assertTrue(Files.isRegularFile(RESOURCES.resolve(path)), path);
        }
    }

    @Test
    void recipeUsesExtractCrystalAndLootCopiesDeviceConfiguration() throws IOException {
        String recipe = Files.readString(RESOURCES.resolve(
                "data/flux_overdrive/recipe/flux_auto_extract_crystal.json"));
        String loot = Files.readString(RESOURCES.resolve(
                "data/flux_overdrive/loot_table/blocks/flux_auto_extract_crystal.json"));

        assertTrue(recipe.contains("\"item\": \"flux_overdrive:flux_extract_crystal\""));
        assertTrue(recipe.contains("\"id\": \"flux_overdrive:flux_auto_extract_crystal\""));
        assertTrue(loot.contains("fluxnetworks:device_config"));
        assertTrue(loot.contains("fluxnetworks:stored_energy"));
    }

    @Test
    void translationsAndMineableTagContainCrystal() throws IOException {
        String english = Files.readString(RESOURCES.resolve("assets/flux_overdrive/lang/en_us.json"));
        String chinese = Files.readString(RESOURCES.resolve("assets/flux_overdrive/lang/zh_cn.json"));
        String mineable = Files.readString(RESOURCES.resolve("data/minecraft/tags/block/mineable/pickaxe.json"));

        assertTrue(english.contains("Flux Auto Extract Crystal"));
        assertTrue(chinese.contains("自动提取水晶"));
        assertTrue(mineable.contains("flux_overdrive:flux_auto_extract_crystal"));
    }
}

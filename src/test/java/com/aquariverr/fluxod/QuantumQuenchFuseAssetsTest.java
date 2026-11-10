package com.aquariverr.fluxod;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumQuenchFuseAssetsTest {

    private static final Path RESOURCE_ROOT = Path.of("src/main/resources");
    private static final String ID = "quantum_quench_fuse_tier_1";

    @Test
    void tierOneFuseHasCompleteBlockAssets() {
        for (String path : List.of(
                "assets/flux_overdrive/blockstates/" + ID + ".json",
                "assets/flux_overdrive/models/block/" + ID + ".json",
                "assets/flux_overdrive/models/item/" + ID + ".json",
                "data/flux_overdrive/loot_table/blocks/" + ID + ".json")) {
            assertTrue(Files.isRegularFile(RESOURCE_ROOT.resolve(path)), path);
        }
        assertFalse(Files.exists(RESOURCE_ROOT.resolve("data/flux_overdrive/recipe/" + ID + ".json")));
    }

    @Test
    void burnoutLootDoesNotDependOnBlockEntityData() throws IOException {
        String loot = Files.readString(RESOURCE_ROOT.resolve(
                "data/flux_overdrive/loot_table/blocks/" + ID + ".json"));

        assertTrue(loot.contains("flux_overdrive:" + ID));
        assertFalse(loot.contains("copy_components"));
        assertFalse(loot.contains("block_entity_data"));
    }

    @Test
    void fuseBehaviorIsLocalized() throws IOException {
        for (String locale : List.of("en_us", "zh_cn")) {
            String language = Files.readString(RESOURCE_ROOT.resolve(
                    "assets/flux_overdrive/lang/" + locale + ".json"));
            assertTrue(language.contains("block.flux_overdrive." + ID), locale);
            assertTrue(language.contains("tooltip.flux_overdrive.quantum_battery_plugin.quench_fuse_tier_1"),
                    locale);
            assertTrue(language.contains("tooltip.flux_overdrive.quantum_battery_plugin.tier_1_ring_only"),
                    locale);
        }
    }
}

package com.aquariverr.fluxod;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuantumFeedbackAssetsTest {

    private static final Path RESOURCE_ROOT = Path.of("src/main/resources");

    @Test
    void feedbackLootPreservesTheCurveDataComponent() throws IOException {
        for (String id : new String[]{
                "quantum_stability_feedback_plugin",
                "quantum_temperature_feedback_plugin"
        }) {
            String loot = Files.readString(RESOURCE_ROOT.resolve(
                    "data/flux_overdrive/loot_table/blocks/" + id + ".json"));
            assertTrue(loot.contains("flux_overdrive:quantum_feedback_curve"), id);
            assertTrue(Files.exists(RESOURCE_ROOT.resolve(
                    "assets/flux_overdrive/blockstates/" + id + ".json")), id);
            assertTrue(Files.exists(RESOURCE_ROOT.resolve(
                    "assets/flux_overdrive/models/block/" + id + ".json")), id);
            assertTrue(Files.exists(RESOURCE_ROOT.resolve(
                    "assets/flux_overdrive/models/item/" + id + ".json")), id);
            assertFalse(Files.exists(RESOURCE_ROOT.resolve(
                    "data/flux_overdrive/recipe/" + id + ".json")), id);
        }
    }
}

package com.aquariverr.fluxod.integration.mekanism;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemicalCoolantTagAssetsTest {

    private static final Path TAG_ROOT = Path.of(
            "src/main/resources/data/flux_overdrive/tags/mekanism/chemical");
    private static final Path LEGACY_TAG_ROOT = Path.of(
            "src/main/resources/data/flux_overdrive/tags/chemical");
    private static final List<String> TEMPERATURE_TAGS = List.of(
            "quantum_coolants_253k.json",
            "quantum_coolants_263k.json",
            "quantum_coolants_273k.json"
    );

    @Test
    void chemicalTagsUseTheMekanismRegistryDirectory() {
        for (String fileName : TEMPERATURE_TAGS) {
            assertTrue(Files.isRegularFile(TAG_ROOT.resolve(fileName)), fileName);
            assertFalse(Files.exists(LEGACY_TAG_ROOT.resolve(fileName)), fileName);
        }
    }

    @Test
    void sodiumIsA253KelvinCoolant() throws IOException {
        String tag = Files.readString(TAG_ROOT.resolve("quantum_coolants_253k.json"));
        assertTrue(tag.contains("\"mekanism:sodium\""));
    }
}

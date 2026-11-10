package com.aquariverr.fluxod;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoLinkBlockBlacklistTest {

    @Test
    void treatsValidResourceLocationsAsExactBlockIds() {
        AutoLinkBlockBlacklist blacklist = parse(List.of(
                "minecraft:furnace",
                "example:block.name"));

        assertTrue(matches(blacklist, "minecraft:furnace"));
        assertTrue(matches(blacklist, "example:block.name"));
        assertFalse(matches(blacklist, "minecraft:blast_furnace"));
        assertFalse(matches(blacklist, "example:blockXname"));
    }

    @Test
    void matchesBlockTagsPrefixedWithHash() {
        AutoLinkBlockBlacklist blacklist = parse(List.of("#c:energy_cables"));

        assertTrue(blacklist.matches("example:cable", "c:energy_cables"::equals));
        assertFalse(matches(blacklist, "example:cable"));
    }

    @Test
    void automaticallyClassifiesAndFullyMatchesRegularExpressions() {
        AutoLinkBlockBlacklist blacklist = parse(List.of("test:(copper|tin)_(wire|cable)"));

        assertTrue(matches(blacklist, "test:copper_wire"));
        assertTrue(matches(blacklist, "test:tin_cable"));
        assertFalse(matches(blacklist, "prefix_test:copper_wire"));
        assertFalse(matches(blacklist, "test:copper_wire_extra"));
    }

    @Test
    void validatesEverySupportedEntryType() {
        assertTrue(isValid("minecraft:furnace"));
        assertTrue(isValid("#c:energy_cables"));
        assertTrue(isValid("gtceu:.+_(wire|cable)"));

        assertFalse(isValid(null));
        assertFalse(isValid(""));
        assertFalse(isValid("   "));
        assertFalse(isValid("#"));
        assertFalse(isValid("#.*"));
        assertFalse(isValid("[unterminated"));
    }

    @Test
    void rejectsInvalidEntriesDuringParsing() {
        assertThrows(IllegalArgumentException.class, () -> parse(List.of("")));
        assertThrows(IllegalArgumentException.class, () -> parse(List.of("#")));
        assertThrows(IllegalArgumentException.class, () -> parse(List.of("[unterminated")));
    }

    @Test
    void defaultRulesCoverOnlyTheIntendedCableFamilies() {
        assertEquals(11, AutoLinkBlockBlacklist.DEFAULT_ENTRIES.size());
        AutoLinkBlockBlacklist blacklist = parse(AutoLinkBlockBlacklist.DEFAULT_ENTRIES);

        for (String blockId : List.of(
                "ae2:pattern_provider",
                "advanced_ae:small_adv_pattern_provider",
                "extendedae:ex_pattern_provider_part",
                "appflux:flux_accessor",
                "extendedae:ex_pattern_provider",
                "ae2:cable_pattern_provider",
                "advanced_ae:small_adv_pattern_provider_part",
                "appflux:flux_accessor_part")) {
            assertTrue(matches(blacklist, blockId), blockId);
        }

        for (String tier : List.of("basic", "advanced", "elite", "ultimate")) {
            String blockId = "mekanism:" + tier + "_universal_cable";
            assertTrue(matches(blacklist, blockId), blockId);
        }

        for (String blockId : List.of(
                "sfm:cable",
                "sfm:cable_facade",
                "sfm:fancy_cable",
                "sfm:fancy_cable_facade",
                "sfm:tough_cable",
                "sfm:tough_cable_facade",
                "sfm:tough_fancy_cable",
                "sfm:tough_fancy_cable_facade",
                "sfm:tunnelled_cable",
                "sfm:tunnelled_cable_facade",
                "sfm:tunnelled_fancy_cable",
                "sfm:tunnelled_fancy_cable_facade")) {
            assertTrue(matches(blacklist, blockId), blockId);
        }

        for (String blockId : List.of(
                "gtceu:copper_single_wire",
                "gtceu:annealed_copper_hex_cable",
                "gtceu:uranium_triplatinum_octal_wire")) {
            assertTrue(matches(blacklist, blockId), blockId);
        }

        for (String blockId : List.of(
                "sfm:tunnelled_manager",
                "mekanism:basic_pressurized_tube",
                "gtceu:bronze_machine_casing",
                "other:copper_single_wire")) {
            assertFalse(matches(blacklist, blockId), blockId);
        }
    }

    private static boolean matches(AutoLinkBlockBlacklist blacklist, String blockId) {
        return blacklist.matches(blockId, ignored -> false);
    }

    private static AutoLinkBlockBlacklist parse(List<? extends String> entries) {
        return AutoLinkBlockBlacklist.parse(entries, AutoLinkBlockBlacklistTest::normalizeResourceLocation);
    }

    private static boolean isValid(Object value) {
        return AutoLinkBlockBlacklist.isValidEntry(value, AutoLinkBlockBlacklistTest::normalizeResourceLocation);
    }

    private static String normalizeResourceLocation(String value) {
        int separator = value.indexOf(':');
        String namespace = separator < 0 ? "minecraft" : value.substring(0, separator);
        String path = separator < 0 ? value : value.substring(separator + 1);
        if (!Pattern.matches("[a-z0-9_.-]+", namespace)
                || !Pattern.matches("[a-z0-9/._-]+", path)) {
            return null;
        }
        return namespace + ":" + path;
    }
}

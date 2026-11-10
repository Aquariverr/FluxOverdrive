package com.aquariverr.fluxod;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuantumConfigMigrationTest {

    @Test
    void migratesLegacyDefaults() {
        assertEquals(new QuantumConfigMigration(
                        5_000_000L,
                        250_000_000L,
                        1_000_000L,
                        10_000_000L,
                        1_000_000_000L),
                QuantumConfigMigration.migrateLegacyDefaults(
                        1_000_000L,
                        100_000_000L,
                        1_000L,
                        10_000L,
                        100_000L));
    }

    @Test
    void preservesCustomizedValues() {
        QuantumConfigMigration customized = new QuantumConfigMigration(
                7_000_000L,
                300_000_000L,
                2_000_000L,
                20_000_000L,
                2_000_000_000L);

        assertEquals(customized, QuantumConfigMigration.migrateLegacyDefaults(
                customized.stabilizerCapacity(),
                customized.advancedStabilizerCapacity(),
                customized.tier1Cost(),
                customized.tier2Cost(),
                customized.tier3Cost()));
    }
}

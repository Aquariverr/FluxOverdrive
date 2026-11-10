package com.aquariverr.fluxod;

record QuantumConfigMigration(long stabilizerCapacity,
                              long advancedStabilizerCapacity,
                              long tier1Cost,
                              long tier2Cost,
                              long tier3Cost) {

    static QuantumConfigMigration migrateLegacyDefaults(long stabilizerCapacity,
                                                        long advancedStabilizerCapacity,
                                                        long tier1Cost,
                                                        long tier2Cost,
                                                        long tier3Cost) {
        return new QuantumConfigMigration(
                replace(stabilizerCapacity, 1_000_000L, 5_000_000L),
                replace(advancedStabilizerCapacity, 100_000_000L, 250_000_000L),
                replace(tier1Cost, 1_000L, 1_000_000L),
                replace(tier2Cost, 10_000L, 10_000_000L),
                replace(tier3Cost, 100_000L, 1_000_000_000L));
    }

    private static long replace(long value, long legacyDefault, long replacement) {
        return value == legacyDefault ? replacement : value;
    }
}

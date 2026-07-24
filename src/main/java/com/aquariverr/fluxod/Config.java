package com.aquariverr.fluxod;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nonnull;

public class Config {
    public static long enderCapacity = 64_000_000_000L;
    public static long enderTransfer = 640_000_000L;
    public static long netherCapacity = 8_000_000_000L;
    public static long netherTransfer = 80_000_000L;
    public static long feStorageCapacity = 1_000_000L;
    public static long feStorageTransfer = 50_000L;
    public static long autoLinkRadius = 16L;
    public static long autoLinkScanCooldown = 20L;
    public static long maxCrystalLinks = 256L;

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.LongValue ENDER_CAPACITY = BUILDER
            .worldRestart()
            .comment("Maximum energy capacity of the Ender Flux Storage (FE)")
            .defineInRange("enderCapacity", 64_000_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue ENDER_TRANSFER = BUILDER
            .worldRestart()
            .comment("Default transfer limit of the Ender Flux Storage (FE/t)")
            .defineInRange("enderTransfer", 640_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue NETHER_CAPACITY = BUILDER
            .worldRestart()
            .comment("Maximum energy capacity of the Nether Flux Storage (FE)")
            .defineInRange("netherCapacity", 8_000_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue NETHER_TRANSFER = BUILDER
            .worldRestart()
            .comment("Default transfer limit of the Nether Flux Storage (FE/t)")
            .defineInRange("netherTransfer", 80_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue FE_STORAGE_CAPACITY = BUILDER
            .worldRestart()
            .comment("Default maximum FE capacity of the Flux FE Storage (FE)")
            .defineInRange("feStorageCapacity", 1_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue FE_STORAGE_TRANSFER = BUILDER
            .worldRestart()
            .comment("Default transfer limit of the Flux FE Storage (FE/t)")
            .defineInRange("feStorageTransfer", 50_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue AUTO_LINK_RADIUS = BUILDER
            .comment("Connection range of the Flux Auto Link Crystal (blocks)")
            .defineInRange("autoLinkRadius", 16L, 1, 64);

    public static final ModConfigSpec.LongValue AUTO_LINK_SCAN_COOLDOWN = BUILDER
            .comment("Cooldown between redstone-triggered scans (ticks)")
            .defineInRange("autoLinkScanCooldown", 20L, 0, 200);

    public static final ModConfigSpec.LongValue MAX_CRYSTAL_LINKS = BUILDER
            .worldRestart()
            .comment("Maximum number of targets stored by one link crystal")
            .defineInRange("maxCrystalLinks", 256L, 1, 1024);

    public static final ModConfigSpec SPEC = BUILDER.build();

    static void onConfigEvent(@Nonnull ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (event.getConfig().getSpec() == SPEC) {
            enderCapacity = ENDER_CAPACITY.get();
            enderTransfer = ENDER_TRANSFER.get();
            netherCapacity = NETHER_CAPACITY.get();
            netherTransfer = NETHER_TRANSFER.get();
            feStorageCapacity = FE_STORAGE_CAPACITY.get();
            feStorageTransfer = FE_STORAGE_TRANSFER.get();
            autoLinkRadius = AUTO_LINK_RADIUS.get();
            autoLinkScanCooldown = AUTO_LINK_SCAN_COOLDOWN.get();
            maxCrystalLinks = MAX_CRYSTAL_LINKS.get();
            FluxOverdrive.LOGGER.info("Flux Overdrive config loaded");
        }
    }
}

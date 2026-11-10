package com.aquariverr.fluxod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public static BigInteger quantumTier1Capacity = new BigInteger("1000000000000");
    public static BigInteger quantumTier2Capacity = new BigInteger("1000000000000000000000");
    @Nullable
    public static BigInteger quantumTier3Capacity;
    public static long quantumStabilizerCapacity = 5_000_000L;
    public static long quantumStabilizer3x3Capacity = 250_000_000L;
    public static long quantumTier1StabilizationCost = 1_000_000L;
    public static long quantumTier2StabilizationCost = 10_000_000L;
    public static long quantumTier3StabilizationCost = 1_000_000_000L;
    public static double quantumInstabilityLeakThreshold = 30.0;
    public static double quantumInstabilityLeakChance = 0.05;
    public static double quantumInstabilityLeakRatio = 0.01;
    public static double quantumMeltdownExplosionRadius = 8.0;
    public static double quantumMeltdownRadiusPerOrder = 10.0;
    public static double quantumMeltdownMaxRadius = 500.0;
    public static double quantumMeltdownMaxBlockDestructionRadius = 500.0;
    public static boolean quantumInstabilityPenaltiesEnabled = true;
    public static double quantumSolidCoolingRate = 80.0;
    public static double quantumAdvancedCoolingRate = 80.0;
    public static int quantumCoolantConsumptionMultiplier = 10;
    public static double quantumIceCooling = 3_000.0;
    public static double quantumPackedIceCooling = 27_000.0;
    public static double quantumBlueIceCooling = 243_000.0;
    public static double quantumFluidCoolingPerMb = 150.0;
    public static double quantumChemicalCoolingPerUnit = 450.0;
    public static int quantumCoolingFluidTankCapacity = 1_600_000;
    public static long quantumCoolingChemicalTankCapacity = 1_600_000L;
    public static int entanglementMaxItemTransfer = Integer.MAX_VALUE;
    public static int entanglementMaxFluidTransfer = Integer.MAX_VALUE;
    public static long entanglementMaxChemicalTransfer = Long.MAX_VALUE;
    public static BigInteger miniatureInputCapacity = com.aquariverr.fluxod.miniature.model.FactoryLimits.MAX_INVENTORY_AMOUNT;
    private static volatile LoadedAutoLinkBlockBlacklist autoLinkBlockBlacklist =
            loadAutoLinkBlockBlacklist(AutoLinkBlockBlacklist.DEFAULT_ENTRIES);

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static boolean legacyQuantumMigrationInProgress;

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
            .comment("Connection range shared by the Flux Auto Link and Auto Extract Crystals (blocks)")
            .defineInRange("autoLinkRadius", 16L, 1, 64);

    public static final ModConfigSpec.LongValue AUTO_LINK_SCAN_COOLDOWN = BUILDER
            .comment("Cooldown between redstone-triggered automatic crystal scans (ticks)")
            .defineInRange("autoLinkScanCooldown", 20L, 0, 200);

    public static final ModConfigSpec.LongValue MAX_CRYSTAL_LINKS = BUILDER
            .worldRestart()
            .comment("Maximum number of targets stored by one link crystal")
            .defineInRange("maxCrystalLinks", 256L, 1, 1024);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> AUTO_LINK_BLOCK_BLACKLIST = BUILDER
            .comment("Blocks ignored by the Flux Auto Link and Auto Extract Crystals",
                    "Entries starting with # are block tags (#c:cables); valid resource locations are exact block IDs",
                    "(minecraft:furnace). All other entries are Java regular expressions matched against the entire block ID.")
            .defineListAllowEmpty("autoLinkBlockBlacklist", AutoLinkBlockBlacklist.DEFAULT_ENTRIES,
                    () -> "minecraft:air", Config::isAutoLinkBlockBlacklistEntry);

    public static final ModConfigSpec.ConfigValue<String> QUANTUM_TIER_1_CAPACITY = BUILDER
            .worldRestart()
            .comment("Tier I Quantum Battery energy capacity, or 'unlimited'")
            .define("quantumTier1Capacity", "1000000000000", Config::isEnergyCapacity);

    public static final ModConfigSpec.ConfigValue<String> QUANTUM_TIER_2_CAPACITY = BUILDER
            .worldRestart()
            .comment("Tier II Quantum Battery energy capacity, or 'unlimited'")
            .define("quantumTier2Capacity", "1000000000000000000000", Config::isEnergyCapacity);

    public static final ModConfigSpec.ConfigValue<String> QUANTUM_TIER_3_CAPACITY = BUILDER
            .worldRestart()
            .comment("Tier III Quantum Battery energy capacity, or 'unlimited'")
            .define("quantumTier3Capacity", "unlimited", Config::isEnergyCapacity);

    public static final ModConfigSpec.LongValue QUANTUM_CONSTRAINT_INTERFACE_CAPACITY = BUILDER
            .worldRestart()
            .comment("Energy capacity of a Quantum Energy Constraint Interface (FE)")
            .translation("flux_overdrive.configuration.quantumConstraintInterfaceCapacity")
            .defineInRange("quantumConstraintInterfaceCapacity", 5_000_000L, 1, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue QUANTUM_CONSTRAINT_INTERFACE_3X3_CAPACITY = BUILDER
            .worldRestart()
            .comment("Energy capacity of a 3x3 Quantum Energy Constraint Interface (FE)")
            .translation("flux_overdrive.configuration.quantumConstraintInterface3x3Capacity")
            .defineInRange("quantumConstraintInterface3x3Capacity", 250_000_000L, 1, Long.MAX_VALUE);

    /**
     * @deprecated renamed to {@link #QUANTUM_CONSTRAINT_INTERFACE_CAPACITY}; the old value is
     * migrated automatically on load, keep the key so existing config files are not corrupted.
     */
    @Deprecated
    public static final ModConfigSpec.LongValue LEGACY_QUANTUM_STABILIZER_CAPACITY = BUILDER
            .worldRestart()
            .comment("DEPRECATED: renamed to quantumConstraintInterfaceCapacity; the value is migrated automatically")
            .defineInRange("quantumStabilizerCapacity", 5_000_000L, 1, Long.MAX_VALUE);

    /**
     * @deprecated renamed to {@link #QUANTUM_CONSTRAINT_INTERFACE_3X3_CAPACITY}; the old value is
     * migrated automatically on load, keep the key so existing config files are not corrupted.
     */
    @Deprecated
    public static final ModConfigSpec.LongValue LEGACY_QUANTUM_STABILIZER_3X3_CAPACITY = BUILDER
            .worldRestart()
            .comment("DEPRECATED: renamed to quantumConstraintInterface3x3Capacity; the value is migrated automatically")
            .defineInRange("quantumStabilizer3x3Capacity", 250_000_000L, 1, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue QUANTUM_TIER_1_STABILIZATION_COST = BUILDER
            .worldRestart()
            .comment("Tier I Quantum Battery ring stabilization cost (FE/t)")
            .defineInRange("quantumTier1StabilizationCost", 1_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue QUANTUM_TIER_2_STABILIZATION_COST = BUILDER
            .worldRestart()
            .comment("Tier II Quantum Battery ring stabilization cost (FE/t)")
            .defineInRange("quantumTier2StabilizationCost", 10_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.LongValue QUANTUM_TIER_3_STABILIZATION_COST = BUILDER
            .worldRestart()
            .comment("Tier III Quantum Battery ring stabilization cost (FE/t)")
            .defineInRange("quantumTier3StabilizationCost", 1_000_000_000L, 0, Long.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue QUANTUM_INSTABILITY_PENALTIES_ENABLED = BUILDER
            .worldRestart()
            .comment("Master switch for Quantum Battery instability penalties: random energy loss below the "
                    + "leak threshold and quench at 0% stability")
            .translation("flux_overdrive.configuration.quantumInstabilityPenaltiesEnabled")
            .define("quantumInstabilityPenaltiesEnabled", true);

    public static final ModConfigSpec.DoubleValue QUANTUM_INSTABILITY_LEAK_THRESHOLD = BUILDER
            .worldRestart()
            .comment("Stability percentage below which a Quantum Battery randomly loses stored energy (0-100)")
            .translation("flux_overdrive.configuration.quantumInstabilityLeakThreshold")
            .defineInRange("quantumInstabilityLeakThreshold", 30.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_INSTABILITY_LEAK_CHANCE = BUILDER
            .worldRestart()
            .comment("Chance per tick of an unstable Quantum Battery leaking energy (0-1)")
            .translation("flux_overdrive.configuration.quantumInstabilityLeakChance")
            .defineInRange("quantumInstabilityLeakChance", 0.05, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_INSTABILITY_LEAK_RATIO = BUILDER
            .worldRestart()
            .comment("Maximum fraction of stored energy lost per leak event (0-1)")
            .translation("flux_overdrive.configuration.quantumInstabilityLeakRatio")
            .defineInRange("quantumInstabilityLeakRatio", 0.01, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_MELTDOWN_EXPLOSION_RADIUS = BUILDER
            .worldRestart()
            .comment("Base quench explosion radius in blocks when a Quantum Battery reaches 0% stability")
            .translation("flux_overdrive.configuration.quantumMeltdownExplosionRadius")
            .defineInRange("quantumMeltdownExplosionRadius", 8.0, 1.0, 1000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_MELTDOWN_RADIUS_PER_ORDER = BUILDER
            .worldRestart()
            .comment("Additional quench explosion radius in blocks per order of magnitude (10x) of stored energy")
            .translation("flux_overdrive.configuration.quantumMeltdownRadiusPerOrder")
            .defineInRange("quantumMeltdownRadiusPerOrder", 10.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_MELTDOWN_MAX_RADIUS = BUILDER
            .worldRestart()
            .comment("Maximum quench visual-effect radius in blocks for a Quantum Battery; entity damage extends to twice this radius")
            .translation("flux_overdrive.configuration.quantumMeltdownMaxRadius")
            .defineInRange("quantumMeltdownMaxRadius", 500.0, 1.0, 10000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_MELTDOWN_MAX_BLOCK_DESTRUCTION_RADIUS = BUILDER
            .worldRestart()
            .comment("Maximum radius of the three-dimensional, resistance-aware block destruction "
                    + "sphere for a Quantum Battery quench. Only chunks loaded when the quench starts "
                    + "are eligible and work is spread across ticks; radius 500 can take a long time")
             .translation("flux_overdrive.configuration.quantumMeltdownMaxBlockDestructionRadius")
             .defineInRange("quantumMeltdownMaxBlockDestructionRadius", 500.0, 1.0, 500.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_SOLID_COOLING_RATE = BUILDER
            .worldRestart()
            .comment("Maximum cooling supplied by one solid Quantum Cooling Plugin (K/t)")
            .translation("flux_overdrive.configuration.quantumSolidCoolingRate")
            .defineInRange("quantumSolidCoolingRate", 80.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_ADVANCED_COOLING_RATE = BUILDER
            .worldRestart()
            .comment("Maximum shared cooling supplied by one Advanced Quantum Cooling Plugin (K/t)")
            .translation("flux_overdrive.configuration.quantumAdvancedCoolingRate")
            .defineInRange("quantumAdvancedCoolingRate", 80.0, 0.0, 1_000_000.0);

    public static final ModConfigSpec.IntValue QUANTUM_COOLANT_CONSUMPTION_MULTIPLIER = BUILDER
            .worldRestart()
            .comment("Coolant consumed for the same cooling output relative to the baseline (1 = baseline)")
            .translation("flux_overdrive.configuration.quantumCoolantConsumptionMultiplier")
            .defineInRange("quantumCoolantConsumptionMultiplier", 10, 1, 1_000_000);

    public static final ModConfigSpec.DoubleValue QUANTUM_ICE_COOLING = BUILDER
            .worldRestart()
            .comment("Cooling provided by one 273 K solid coolant item (K)")
            .translation("flux_overdrive.configuration.quantumIceCooling")
            .defineInRange("quantumIceCooling", 3_000.0, 0.0, 1_000_000_000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_PACKED_ICE_COOLING = BUILDER
            .worldRestart()
            .comment("Cooling provided by one 263 K solid coolant item (K)")
            .translation("flux_overdrive.configuration.quantumPackedIceCooling")
            .defineInRange("quantumPackedIceCooling", 27_000.0, 0.0, 1_000_000_000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_BLUE_ICE_COOLING = BUILDER
            .worldRestart()
            .comment("Cooling provided by one 253 K solid coolant item (K)")
            .translation("flux_overdrive.configuration.quantumBlueIceCooling")
            .defineInRange("quantumBlueIceCooling", 243_000.0, 0.0, 1_000_000_000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_FLUID_COOLING_PER_MB = BUILDER
            .worldRestart()
            .comment("Cooling provided by one mB of tagged fluid coolant (K)")
            .translation("flux_overdrive.configuration.quantumFluidCoolingPerMb")
            .defineInRange("quantumFluidCoolingPerMb", 150.0, 0.0, 1_000_000_000.0);

    public static final ModConfigSpec.DoubleValue QUANTUM_CHEMICAL_COOLING_PER_UNIT = BUILDER
            .worldRestart()
            .comment("Cooling provided by one unit of tagged Mekanism chemical coolant (K)")
            .translation("flux_overdrive.configuration.quantumChemicalCoolingPerUnit")
            .defineInRange("quantumChemicalCoolingPerUnit", 450.0, 0.0, 1_000_000_000.0);

    public static final ModConfigSpec.IntValue QUANTUM_COOLING_FLUID_TANK_CAPACITY = BUILDER
            .worldRestart()
            .comment("Advanced Quantum Cooling Plugin fluid tank capacity (mB)")
            .translation("flux_overdrive.configuration.quantumCoolingFluidTankCapacity")
            .defineInRange("quantumCoolingFluidTankCapacity", 1_600_000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.LongValue QUANTUM_COOLING_CHEMICAL_TANK_CAPACITY = BUILDER
            .worldRestart()
            .comment("Advanced Quantum Cooling Plugin chemical tank capacity (Mekanism units)")
            .translation("flux_overdrive.configuration.quantumCoolingChemicalTankCapacity")
            .defineInRange("quantumCoolingChemicalTankCapacity", 1_600_000L, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.IntValue ENTANGLEMENT_MAX_ITEM_TRANSFER = BUILDER
            .comment("Maximum items per entanglement resource operation; active ports initiate one transfer per tick")
            .defineInRange("entanglementMaxItemTransfer", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue ENTANGLEMENT_MAX_FLUID_TRANSFER = BUILDER
            .comment("Maximum fluid amount per entanglement resource operation; active ports initiate one transfer per tick")
            .defineInRange("entanglementMaxFluidTransfer", Integer.MAX_VALUE, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.LongValue ENTANGLEMENT_MAX_CHEMICAL_TRANSFER = BUILDER
            .comment("Maximum chemical amount per entanglement resource operation; active ports initiate one transfer per tick")
            .defineInRange("entanglementMaxChemicalTransfer", Long.MAX_VALUE, 1L, Long.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MINIATURE_INPUT_CAPACITY = BUILDER
            .comment("Maximum cached input amount per resource in a Miniature Universe (items, mB or chemical units).",
                    "Positive decimal integer, at most 1000000000000000000000000000000. Lowering this limit preserves existing contents.")
            .translation("flux_overdrive.configuration.miniatureInputCapacity")
            .define("miniatureInputCapacity", miniatureInputCapacity.toString(), Config::isMiniatureInputCapacity);

    public static final ModConfigSpec SPEC = BUILDER.build();

    static void onConfigEvent(@Nonnull ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        if (event.getConfig().getSpec() == SPEC) {
            if (legacyQuantumMigrationInProgress) return;
            QuantumConfigMigration quantumConfig = migrateLegacyQuantumDefaults();
            migrateLegacyStabilizerKeys(quantumConfig);
            enderCapacity = ENDER_CAPACITY.get();
            enderTransfer = ENDER_TRANSFER.get();
            netherCapacity = NETHER_CAPACITY.get();
            netherTransfer = NETHER_TRANSFER.get();
            feStorageCapacity = FE_STORAGE_CAPACITY.get();
            feStorageTransfer = FE_STORAGE_TRANSFER.get();
            autoLinkRadius = AUTO_LINK_RADIUS.get();
            autoLinkScanCooldown = AUTO_LINK_SCAN_COOLDOWN.get();
            maxCrystalLinks = MAX_CRYSTAL_LINKS.get();
            quantumTier1Capacity = parseEnergyCapacity(QUANTUM_TIER_1_CAPACITY.get());
            quantumTier2Capacity = parseEnergyCapacity(QUANTUM_TIER_2_CAPACITY.get());
            quantumTier3Capacity = parseEnergyCapacity(QUANTUM_TIER_3_CAPACITY.get());
            quantumStabilizerCapacity = QUANTUM_CONSTRAINT_INTERFACE_CAPACITY.get();
            quantumStabilizer3x3Capacity = QUANTUM_CONSTRAINT_INTERFACE_3X3_CAPACITY.get();
            quantumTier1StabilizationCost = quantumConfig.tier1Cost();
            quantumTier2StabilizationCost = quantumConfig.tier2Cost();
            quantumTier3StabilizationCost = quantumConfig.tier3Cost();
            quantumInstabilityLeakThreshold = QUANTUM_INSTABILITY_LEAK_THRESHOLD.get();
            quantumInstabilityLeakChance = QUANTUM_INSTABILITY_LEAK_CHANCE.get();
            quantumInstabilityLeakRatio = QUANTUM_INSTABILITY_LEAK_RATIO.get();
            quantumMeltdownExplosionRadius = QUANTUM_MELTDOWN_EXPLOSION_RADIUS.get();
            quantumMeltdownRadiusPerOrder = QUANTUM_MELTDOWN_RADIUS_PER_ORDER.get();
            quantumMeltdownMaxRadius = QUANTUM_MELTDOWN_MAX_RADIUS.get();
            quantumMeltdownMaxBlockDestructionRadius = QUANTUM_MELTDOWN_MAX_BLOCK_DESTRUCTION_RADIUS.get();
            quantumInstabilityPenaltiesEnabled = QUANTUM_INSTABILITY_PENALTIES_ENABLED.get();
            quantumSolidCoolingRate = QUANTUM_SOLID_COOLING_RATE.get();
            quantumAdvancedCoolingRate = QUANTUM_ADVANCED_COOLING_RATE.get();
            quantumCoolantConsumptionMultiplier = QUANTUM_COOLANT_CONSUMPTION_MULTIPLIER.get();
            quantumIceCooling = QUANTUM_ICE_COOLING.get();
            quantumPackedIceCooling = QUANTUM_PACKED_ICE_COOLING.get();
            quantumBlueIceCooling = QUANTUM_BLUE_ICE_COOLING.get();
            quantumFluidCoolingPerMb = QUANTUM_FLUID_COOLING_PER_MB.get();
            quantumChemicalCoolingPerUnit = QUANTUM_CHEMICAL_COOLING_PER_UNIT.get();
            quantumCoolingFluidTankCapacity = QUANTUM_COOLING_FLUID_TANK_CAPACITY.get();
            quantumCoolingChemicalTankCapacity = QUANTUM_COOLING_CHEMICAL_TANK_CAPACITY.get();
            entanglementMaxItemTransfer = ENTANGLEMENT_MAX_ITEM_TRANSFER.get();
            entanglementMaxFluidTransfer = ENTANGLEMENT_MAX_FLUID_TRANSFER.get();
            entanglementMaxChemicalTransfer = ENTANGLEMENT_MAX_CHEMICAL_TRANSFER.get();
            miniatureInputCapacity = new BigInteger(MINIATURE_INPUT_CAPACITY.get());
            autoLinkBlockBlacklist = loadAutoLinkBlockBlacklist(AUTO_LINK_BLOCK_BLACKLIST.get());
            FluxOverdrive.LOGGER.info(
                    "Flux Overdrive config loaded; quantum constraint interfaces={} FE/{} FE, ring costs={}/{}/{} FE/t",
                    quantumStabilizerCapacity, quantumStabilizer3x3Capacity,
                    quantumTier1StabilizationCost, quantumTier2StabilizationCost,
                    quantumTier3StabilizationCost);
        }
    }

    private static void migrateLegacyStabilizerKeys(QuantumConfigMigration quantumConfig) {
        boolean changed = false;
        if (quantumConfig.stabilizerCapacity() != QUANTUM_CONSTRAINT_INTERFACE_CAPACITY.getDefault()) {
            QUANTUM_CONSTRAINT_INTERFACE_CAPACITY.set(quantumConfig.stabilizerCapacity());
            changed = true;
        }
        if (quantumConfig.advancedStabilizerCapacity() != QUANTUM_CONSTRAINT_INTERFACE_3X3_CAPACITY.getDefault()) {
            QUANTUM_CONSTRAINT_INTERFACE_3X3_CAPACITY.set(quantumConfig.advancedStabilizerCapacity());
            changed = true;
        }
        if (changed) {
            QUANTUM_CONSTRAINT_INTERFACE_CAPACITY.save();
            FluxOverdrive.LOGGER.info("Migrated legacy quantum stabilizer capacity keys to constraint interface naming");
        }
    }

    private static QuantumConfigMigration migrateLegacyQuantumDefaults() {
        QuantumConfigMigration migrated = QuantumConfigMigration.migrateLegacyDefaults(
                LEGACY_QUANTUM_STABILIZER_CAPACITY.get(),
                LEGACY_QUANTUM_STABILIZER_3X3_CAPACITY.get(),
                QUANTUM_TIER_1_STABILIZATION_COST.get(),
                QUANTUM_TIER_2_STABILIZATION_COST.get(),
                QUANTUM_TIER_3_STABILIZATION_COST.get());
        boolean changed = migrated.stabilizerCapacity() != LEGACY_QUANTUM_STABILIZER_CAPACITY.get()
                || migrated.advancedStabilizerCapacity() != LEGACY_QUANTUM_STABILIZER_3X3_CAPACITY.get()
                || migrated.tier1Cost() != QUANTUM_TIER_1_STABILIZATION_COST.get()
                || migrated.tier2Cost() != QUANTUM_TIER_2_STABILIZATION_COST.get()
                || migrated.tier3Cost() != QUANTUM_TIER_3_STABILIZATION_COST.get();
        if (!changed) return migrated;

        legacyQuantumMigrationInProgress = true;
        try {
            LEGACY_QUANTUM_STABILIZER_CAPACITY.set(migrated.stabilizerCapacity());
            LEGACY_QUANTUM_STABILIZER_3X3_CAPACITY.set(migrated.advancedStabilizerCapacity());
            QUANTUM_TIER_1_STABILIZATION_COST.set(migrated.tier1Cost());
            QUANTUM_TIER_2_STABILIZATION_COST.set(migrated.tier2Cost());
            QUANTUM_TIER_3_STABILIZATION_COST.set(migrated.tier3Cost());
            LEGACY_QUANTUM_STABILIZER_CAPACITY.save();
        } finally {
            legacyQuantumMigrationInProgress = false;
        }
        FluxOverdrive.LOGGER.info("Migrated legacy Quantum Battery stabilization defaults");
        return migrated;
    }

    public static boolean isAutoLinkBlockBlacklisted(BlockState state) {
        return autoLinkBlockBlacklist.matches(state);
    }

    private static boolean isAutoLinkBlockBlacklistEntry(Object value) {
        return AutoLinkBlockBlacklist.isValidEntry(value, Config::normalizeResourceLocation);
    }

    private static LoadedAutoLinkBlockBlacklist loadAutoLinkBlockBlacklist(List<? extends String> entries) {
        AutoLinkBlockBlacklist rules = AutoLinkBlockBlacklist.parse(entries, Config::normalizeResourceLocation);
        Map<String, TagKey<Block>> blockTags = new LinkedHashMap<>();
        for (String tagId : rules.blockTags()) {
            blockTags.put(tagId, TagKey.create(Registries.BLOCK, ResourceLocation.parse(tagId)));
        }
        return new LoadedAutoLinkBlockBlacklist(rules, Map.copyOf(blockTags));
    }

    @Nullable
    private static String normalizeResourceLocation(String value) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(value);
        return resourceLocation == null ? null : resourceLocation.toString();
    }

    @Nullable
    public static BigInteger quantumCapacityForTier(int tier) {
        return switch (tier) {
            case 1 -> quantumTier1Capacity;
            case 2 -> quantumTier2Capacity;
            case 3 -> quantumTier3Capacity;
            default -> BigInteger.ZERO;
        };
    }

    public static long quantumStabilizationCostForTier(int tier) {
        return switch (tier) {
            case 1 -> quantumTier1StabilizationCost;
            case 2 -> quantumTier2StabilizationCost;
            case 3 -> quantumTier3StabilizationCost;
            default -> 0;
        };
    }

    static boolean isMiniatureInputCapacity(Object value) {
        if (!(value instanceof String string) || !string.matches("[0-9]{1,31}")) return false;
        BigInteger amount = new BigInteger(string);
        return amount.signum() > 0 && amount.compareTo(com.aquariverr.fluxod.miniature.model.FactoryLimits.MAX_INVENTORY_AMOUNT) <= 0;
    }

    private static boolean isEnergyCapacity(Object value) {
        if (!(value instanceof String string)) return false;
        if (string.equalsIgnoreCase("unlimited")) return true;
        try {
            return new BigInteger(string).signum() > 0;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    @Nullable
    private static BigInteger parseEnergyCapacity(String value) {
        return value.equalsIgnoreCase("unlimited") ? null : new BigInteger(value);
    }

    private record LoadedAutoLinkBlockBlacklist(AutoLinkBlockBlacklist rules,
                                                Map<String, TagKey<Block>> blockTags) {
        private boolean matches(BlockState state) {
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return rules.matches(blockId, tagId -> {
                TagKey<Block> blockTag = blockTags.get(tagId);
                return blockTag != null && state.is(blockTag);
            });
        }
    }
}

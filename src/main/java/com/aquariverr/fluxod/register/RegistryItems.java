package com.aquariverr.fluxod.register;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.item.FluxAutoLinkCrystalItem;
import com.aquariverr.fluxod.item.FluxAutoExtractCrystalItem;
import com.aquariverr.fluxod.item.FluxExtractCrystalItem;
import com.aquariverr.fluxod.item.FluxLinkCrystalItem;
import com.aquariverr.fluxod.item.FluxLinkToolItem;
import com.aquariverr.fluxod.item.FluxStorageTerminalItem;
import com.aquariverr.fluxod.item.QuantumBatteryPluginItem;
import sonar.fluxnetworks.common.item.FluxDeviceItem;
import sonar.fluxnetworks.common.item.FluxStorageItem;

public class RegistryItems {
    private static final ResourceLocation ENDER_FLUX_DUST_KEY = FluxOverdrive.location("ender_flux_dust");
    private static final ResourceLocation ENDER_FLUX_CORE_KEY = FluxOverdrive.location("ender_flux_core");
    private static final ResourceLocation NETHER_FLUX_DUST_KEY = FluxOverdrive.location("nether_flux_dust");
    private static final ResourceLocation NETHER_FLUX_CORE_KEY = FluxOverdrive.location("nether_flux_core");
    private static final ResourceLocation FLUX_LINK_CRYSTAL_KEY = FluxOverdrive.location("flux_link_crystal");
    private static final ResourceLocation FLUX_EXTRACT_CRYSTAL_KEY = FluxOverdrive.location("flux_extract_crystal");
    private static final ResourceLocation FLUX_AUTO_LINK_CRYSTAL_KEY = FluxOverdrive.location("flux_auto_link_crystal");
    private static final ResourceLocation FLUX_AUTO_EXTRACT_CRYSTAL_KEY =
            FluxOverdrive.location("flux_auto_extract_crystal");
    private static final ResourceLocation FLUX_LINK_TOOL_KEY = FluxOverdrive.location("flux_link_tool");

    public static final DeferredItem<BlockItem> ENDER_FLUX_BLOCK = holder(RegistryBlocks.ENDER_FLUX_BLOCK_KEY);
    public static final DeferredItem<BlockItem> NETHER_FLUX_BLOCK = holder(RegistryBlocks.NETHER_FLUX_BLOCK_KEY);
    public static final DeferredItem<FluxStorageItem> ENDER_FLUX_STORAGE = holder(RegistryBlocks.ENDER_FLUX_STORAGE_KEY);
    public static final DeferredItem<FluxStorageItem> NETHER_FLUX_STORAGE = holder(RegistryBlocks.NETHER_FLUX_STORAGE_KEY);
    public static final DeferredItem<FluxStorageItem> FLUX_STORAGE_TERMINAL = holder(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY);
    public static final DeferredItem<FluxDeviceItem> FLUX_FE_STORAGE = holder(RegistryBlocks.FLUX_FE_STORAGE_KEY);
    public static final DeferredItem<Item> ENDER_FLUX_DUST = holder(ENDER_FLUX_DUST_KEY);
    public static final DeferredItem<Item> ENDER_FLUX_CORE = holder(ENDER_FLUX_CORE_KEY);
    public static final DeferredItem<Item> NETHER_FLUX_DUST = holder(NETHER_FLUX_DUST_KEY);
    public static final DeferredItem<Item> NETHER_FLUX_CORE = holder(NETHER_FLUX_CORE_KEY);
    public static final DeferredItem<FluxLinkCrystalItem> FLUX_LINK_CRYSTAL = holder(FLUX_LINK_CRYSTAL_KEY);
    public static final DeferredItem<FluxExtractCrystalItem> FLUX_EXTRACT_CRYSTAL = holder(FLUX_EXTRACT_CRYSTAL_KEY);
    public static final DeferredItem<FluxAutoLinkCrystalItem> FLUX_AUTO_LINK_CRYSTAL = holder(FLUX_AUTO_LINK_CRYSTAL_KEY);
    public static final DeferredItem<FluxAutoExtractCrystalItem> FLUX_AUTO_EXTRACT_CRYSTAL =
            holder(FLUX_AUTO_EXTRACT_CRYSTAL_KEY);
    public static final DeferredItem<FluxLinkToolItem> FLUX_LINK_TOOL = holder(FLUX_LINK_TOOL_KEY);
    public static final DeferredItem<BlockItem> TRANSMON_QUBIT_CHAIN_CASING = holder(RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_KEY);
    public static final DeferredItem<BlockItem> TRANSMON_QUBIT_CHAIN_CASING_GLASS = holder(RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS_KEY);
    public static final DeferredItem<BlockItem> WEAK_COUPLING_CAVITY_QED_CASING = holder(RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_KEY);
    public static final DeferredItem<BlockItem> WEAK_COUPLING_CAVITY_QED_CASING_GLASS = holder(RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS_KEY);
    public static final DeferredItem<BlockItem> STRONG_COUPLING_CAVITY_QED_CASING = holder(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_KEY);
    public static final DeferredItem<BlockItem> STRONG_COUPLING_CAVITY_QED_CASING_GLASS = holder(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS_KEY);
    public static final DeferredItem<FluxDeviceItem> QUANTUM_BATTERY_CONTROLLER = holder(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> QUANTUM_ENERGY_CONSTRAINT_INTERFACE =
            holder(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3 =
            holder(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> QUANTUM_SOLID_COOLING_PLUGIN =
            holder(RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> QUANTUM_ADVANCED_COOLING_PLUGIN =
            holder(RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> MINIATURE_UNIVERSE =
            holder(RegistryBlocks.MINIATURE_UNIVERSE_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> QUANTUM_QUENCH_FUSE_TIER_1 =
            holder(RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> QUANTUM_STABILITY_FEEDBACK_PLUGIN =
            holder(RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY);
    public static final DeferredItem<QuantumBatteryPluginItem> QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN =
            holder(RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN_KEY);
    public static final DeferredItem<BlockItem> QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER =
            holder(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY);
    public static final DeferredItem<BlockItem> QUANTUM_ENTANGLEMENT_CASING =
            holder(RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING_KEY);
    public static final DeferredItem<BlockItem> QUANTUM_ENTANGLEMENT_ANTENNA =
            holder(RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA_KEY);
    public static final DeferredItem<BlockItem> QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT =
            holder(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY);
    public static final DeferredItem<BlockItem> QUANTUM_ENTANGLEMENT_INPUT_PORT =
            holder(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY);
    public static final DeferredItem<BlockItem> QUANTUM_ENTANGLEMENT_OUTPUT_PORT =
            holder(RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT_KEY);
    public static final DeferredItem<BlockItem> QUANTUM_ENTANGLEMENT_REDSTONE_PORT =
            holder(RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT_KEY);

    static <T extends Item> DeferredItem<T> holder(ResourceLocation location) {
        return DeferredItem.createItem(location);
    }

    static void register(RegisterEvent.RegisterHelper<Item> helper) {
        Item.Properties normalProps = new Item.Properties().fireResistant();

        helper.register(RegistryBlocks.ENDER_FLUX_BLOCK_KEY, new BlockItem(RegistryBlocks.ENDER_FLUX_BLOCK.get(), normalProps));
        helper.register(RegistryBlocks.NETHER_FLUX_BLOCK_KEY, new BlockItem(RegistryBlocks.NETHER_FLUX_BLOCK.get(), normalProps));
        helper.register(RegistryBlocks.ENDER_FLUX_STORAGE_KEY, new FluxStorageItem(RegistryBlocks.ENDER_FLUX_STORAGE.get(), normalProps));
        helper.register(RegistryBlocks.NETHER_FLUX_STORAGE_KEY, new FluxStorageItem(RegistryBlocks.NETHER_FLUX_STORAGE.get(), normalProps));
        helper.register(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY, new FluxStorageTerminalItem(RegistryBlocks.FLUX_STORAGE_TERMINAL.get(), normalProps));
        helper.register(RegistryBlocks.FLUX_FE_STORAGE_KEY, new FluxDeviceItem(RegistryBlocks.FLUX_FE_STORAGE.get(), normalProps));

        helper.register(ENDER_FLUX_DUST_KEY, new Item(normalProps));
        helper.register(ENDER_FLUX_CORE_KEY, new Item(normalProps));
        helper.register(NETHER_FLUX_DUST_KEY, new Item(normalProps));
        helper.register(NETHER_FLUX_CORE_KEY, new Item(normalProps));

        helper.register(FLUX_LINK_CRYSTAL_KEY, new FluxLinkCrystalItem(RegistryBlocks.FLUX_LINK_CRYSTAL.get(), normalProps));
        helper.register(FLUX_EXTRACT_CRYSTAL_KEY,
                new FluxExtractCrystalItem(RegistryBlocks.FLUX_EXTRACT_CRYSTAL.get(), normalProps));
        helper.register(FLUX_AUTO_LINK_CRYSTAL_KEY, new FluxAutoLinkCrystalItem(RegistryBlocks.FLUX_AUTO_LINK_CRYSTAL.get(), normalProps));
        helper.register(FLUX_AUTO_EXTRACT_CRYSTAL_KEY,
                new FluxAutoExtractCrystalItem(RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL.get(), normalProps));
        helper.register(FLUX_LINK_TOOL_KEY,
                new FluxLinkToolItem(new Item.Properties().fireResistant().stacksTo(1)));
        helper.register(RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_KEY, new BlockItem(RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING.get(), normalProps));
        helper.register(RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS_KEY, new BlockItem(RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS.get(), normalProps));
        helper.register(RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_KEY, new BlockItem(RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING.get(), normalProps));
        helper.register(RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS_KEY, new BlockItem(RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS.get(), normalProps));
        helper.register(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_KEY, new BlockItem(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING.get(), normalProps));
        helper.register(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS_KEY, new BlockItem(RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER_KEY,
                new FluxDeviceItem(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get(),
                        new Item.Properties().fireResistant().stacksTo(1)));
        helper.register(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN.get(), normalProps));
        helper.register(RegistryBlocks.MINIATURE_UNIVERSE_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.MINIATURE_UNIVERSE.get(),
                        new Item.Properties().fireResistant().stacksTo(1)));
        helper.register(RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN_KEY,
                new QuantumBatteryPluginItem(RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY,
                new BlockItem(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get(),
                        new Item.Properties().fireResistant().stacksTo(1)));
        helper.register(RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING_KEY,
                new BlockItem(RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA_KEY,
                new BlockItem(RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY,
                new BlockItem(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY,
                new BlockItem(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT_KEY,
                new BlockItem(RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get(), normalProps));
        helper.register(RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT_KEY,
                new BlockItem(RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get(), normalProps));
    }

    private RegistryItems() {}
}

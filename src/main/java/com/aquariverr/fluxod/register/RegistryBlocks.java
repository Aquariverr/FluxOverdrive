package com.aquariverr.fluxod.register;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.FluxAutoLinkCrystalBlock;
import com.aquariverr.fluxod.block.FluxAutoExtractCrystalBlock;
import com.aquariverr.fluxod.block.FluxExtractCrystalBlock;
import com.aquariverr.fluxod.block.FluxFEStorageBlock;
import com.aquariverr.fluxod.block.FluxLinkCrystalBlock;
import com.aquariverr.fluxod.block.FluxStorageBlock;
import com.aquariverr.fluxod.block.FluxStorageTerminalBlock;
import com.aquariverr.fluxod.block.QuantumEnergyConstraintInterfaceBlock;
import com.aquariverr.fluxod.block.QuantumEnergyConstraintInterface3x3Block;
import com.aquariverr.fluxod.block.QuantumBatteryControllerBlock;
import com.aquariverr.fluxod.block.QuantumBatteryStructureBlock;
import com.aquariverr.fluxod.block.QuantumBatteryStructureGlassBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementPortBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementResourcePortBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementStructureBlock;
import com.aquariverr.fluxod.block.QuantumSolidCoolingPluginBlock;
import com.aquariverr.fluxod.block.MiniatureUniverseBlock;
import com.aquariverr.fluxod.block.QuantumAdvancedCoolingPluginBlock;
import com.aquariverr.fluxod.block.QuantumFeedbackPluginBlock;
import com.aquariverr.fluxod.block.QuantumQuenchFusePluginBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementRedstonePortBlock;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackMetric;

public class RegistryBlocks {
    public static final ResourceLocation ENDER_FLUX_BLOCK_KEY = FluxOverdrive.location("ender_flux_block");
    public static final ResourceLocation NETHER_FLUX_BLOCK_KEY = FluxOverdrive.location("nether_flux_block");
    public static final ResourceLocation ENDER_FLUX_STORAGE_KEY = FluxOverdrive.location("ender_flux_storage");
    public static final ResourceLocation NETHER_FLUX_STORAGE_KEY = FluxOverdrive.location("nether_flux_storage");
    public static final ResourceLocation FLUX_STORAGE_TERMINAL_KEY = FluxOverdrive.location("flux_storage_terminal");
    public static final ResourceLocation FLUX_FE_STORAGE_KEY = FluxOverdrive.location("flux_fe_storage");
    public static final ResourceLocation FLUX_LINK_CRYSTAL_KEY = FluxOverdrive.location("flux_link_crystal");
    public static final ResourceLocation FLUX_EXTRACT_CRYSTAL_KEY = FluxOverdrive.location("flux_extract_crystal");
    public static final ResourceLocation FLUX_AUTO_LINK_CRYSTAL_KEY = FluxOverdrive.location("flux_auto_link_crystal");
    public static final ResourceLocation FLUX_AUTO_EXTRACT_CRYSTAL_KEY = FluxOverdrive.location("flux_auto_extract_crystal");
    public static final ResourceLocation TRANSMON_QUBIT_CHAIN_CASING_KEY = FluxOverdrive.location("transmon_qubit_chain_casing");
    public static final ResourceLocation TRANSMON_QUBIT_CHAIN_CASING_GLASS_KEY = FluxOverdrive.location("transmon_qubit_chain_casing_glass");
    public static final ResourceLocation WEAK_COUPLING_CAVITY_QED_CASING_KEY = FluxOverdrive.location("weak_coupling_cavity_qed_casing");
    public static final ResourceLocation WEAK_COUPLING_CAVITY_QED_CASING_GLASS_KEY = FluxOverdrive.location("weak_coupling_cavity_qed_casing_glass");
    public static final ResourceLocation STRONG_COUPLING_CAVITY_QED_CASING_KEY = FluxOverdrive.location("strong_coupling_cavity_qed_casing");
    public static final ResourceLocation STRONG_COUPLING_CAVITY_QED_CASING_GLASS_KEY = FluxOverdrive.location("strong_coupling_cavity_qed_casing_glass");
    public static final ResourceLocation QUANTUM_BATTERY_CONTROLLER_KEY = FluxOverdrive.location("quantum_battery_controller");
    public static final ResourceLocation QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY =
            FluxOverdrive.location("quantum_energy_constraint_interface");
    public static final ResourceLocation QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3_KEY =
            FluxOverdrive.location("quantum_energy_constraint_interface_3x3");
    public static final ResourceLocation QUANTUM_SOLID_COOLING_PLUGIN_KEY =
            FluxOverdrive.location("quantum_solid_cooling_plugin");
    public static final ResourceLocation QUANTUM_ADVANCED_COOLING_PLUGIN_KEY =
            FluxOverdrive.location("quantum_advanced_cooling_plugin");
    public static final ResourceLocation MINIATURE_UNIVERSE_KEY = FluxOverdrive.location("miniature_universe");
    public static final ResourceLocation QUANTUM_QUENCH_FUSE_TIER_1_KEY =
            FluxOverdrive.location("quantum_quench_fuse_tier_1");
    public static final ResourceLocation QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY =
            FluxOverdrive.location("quantum_stability_feedback_plugin");
    public static final ResourceLocation QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN_KEY =
            FluxOverdrive.location("quantum_temperature_feedback_plugin");
    public static final ResourceLocation QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY =
            FluxOverdrive.location("quantum_battery_entanglement_controller");
    public static final ResourceLocation QUANTUM_ENTANGLEMENT_CASING_KEY =
            FluxOverdrive.location("quantum_entanglement_casing");
    public static final ResourceLocation QUANTUM_ENTANGLEMENT_ANTENNA_KEY =
            FluxOverdrive.location("quantum_entanglement_antenna");
    public static final ResourceLocation QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY =
            FluxOverdrive.location("quantum_energy_constraint_entanglement_port");
    public static final ResourceLocation QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY =
            FluxOverdrive.location("quantum_entanglement_input_port");
    public static final ResourceLocation QUANTUM_ENTANGLEMENT_OUTPUT_PORT_KEY =
            FluxOverdrive.location("quantum_entanglement_output_port");
    public static final ResourceLocation QUANTUM_ENTANGLEMENT_REDSTONE_PORT_KEY =
            FluxOverdrive.location("quantum_entanglement_redstone_port");

    public static final DeferredBlock<Block> ENDER_FLUX_BLOCK = holder(ENDER_FLUX_BLOCK_KEY);
    public static final DeferredBlock<Block> NETHER_FLUX_BLOCK = holder(NETHER_FLUX_BLOCK_KEY);
    public static final DeferredBlock<FluxStorageBlock.Ender> ENDER_FLUX_STORAGE = holder(ENDER_FLUX_STORAGE_KEY);
    public static final DeferredBlock<FluxStorageBlock.Nether> NETHER_FLUX_STORAGE = holder(NETHER_FLUX_STORAGE_KEY);
    public static final DeferredBlock<FluxStorageTerminalBlock> FLUX_STORAGE_TERMINAL = holder(FLUX_STORAGE_TERMINAL_KEY);
    public static final DeferredBlock<FluxFEStorageBlock> FLUX_FE_STORAGE = holder(FLUX_FE_STORAGE_KEY);
    public static final DeferredBlock<FluxLinkCrystalBlock> FLUX_LINK_CRYSTAL = holder(FLUX_LINK_CRYSTAL_KEY);
    public static final DeferredBlock<FluxExtractCrystalBlock> FLUX_EXTRACT_CRYSTAL = holder(FLUX_EXTRACT_CRYSTAL_KEY);
    public static final DeferredBlock<FluxAutoLinkCrystalBlock> FLUX_AUTO_LINK_CRYSTAL = holder(FLUX_AUTO_LINK_CRYSTAL_KEY);
    public static final DeferredBlock<FluxAutoExtractCrystalBlock> FLUX_AUTO_EXTRACT_CRYSTAL =
            holder(FLUX_AUTO_EXTRACT_CRYSTAL_KEY);
    public static final DeferredBlock<QuantumBatteryStructureBlock> TRANSMON_QUBIT_CHAIN_CASING = holder(TRANSMON_QUBIT_CHAIN_CASING_KEY);
    public static final DeferredBlock<QuantumBatteryStructureGlassBlock> TRANSMON_QUBIT_CHAIN_CASING_GLASS = holder(TRANSMON_QUBIT_CHAIN_CASING_GLASS_KEY);
    public static final DeferredBlock<QuantumBatteryStructureBlock> WEAK_COUPLING_CAVITY_QED_CASING = holder(WEAK_COUPLING_CAVITY_QED_CASING_KEY);
    public static final DeferredBlock<QuantumBatteryStructureGlassBlock> WEAK_COUPLING_CAVITY_QED_CASING_GLASS = holder(WEAK_COUPLING_CAVITY_QED_CASING_GLASS_KEY);
    public static final DeferredBlock<QuantumBatteryStructureBlock> STRONG_COUPLING_CAVITY_QED_CASING = holder(STRONG_COUPLING_CAVITY_QED_CASING_KEY);
    public static final DeferredBlock<QuantumBatteryStructureGlassBlock> STRONG_COUPLING_CAVITY_QED_CASING_GLASS = holder(STRONG_COUPLING_CAVITY_QED_CASING_GLASS_KEY);
    public static final DeferredBlock<QuantumBatteryControllerBlock> QUANTUM_BATTERY_CONTROLLER = holder(QUANTUM_BATTERY_CONTROLLER_KEY);
    public static final DeferredBlock<QuantumEnergyConstraintInterfaceBlock> QUANTUM_ENERGY_CONSTRAINT_INTERFACE =
            holder(QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY);
    public static final DeferredBlock<QuantumEnergyConstraintInterface3x3Block> QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3 =
            holder(QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3_KEY);
    public static final DeferredBlock<QuantumSolidCoolingPluginBlock> QUANTUM_SOLID_COOLING_PLUGIN =
            holder(QUANTUM_SOLID_COOLING_PLUGIN_KEY);
    public static final DeferredBlock<QuantumAdvancedCoolingPluginBlock> QUANTUM_ADVANCED_COOLING_PLUGIN =
            holder(QUANTUM_ADVANCED_COOLING_PLUGIN_KEY);
    public static final DeferredBlock<MiniatureUniverseBlock> MINIATURE_UNIVERSE = holder(MINIATURE_UNIVERSE_KEY);
    public static final DeferredBlock<QuantumQuenchFusePluginBlock> QUANTUM_QUENCH_FUSE_TIER_1 =
            holder(QUANTUM_QUENCH_FUSE_TIER_1_KEY);
    public static final DeferredBlock<QuantumFeedbackPluginBlock> QUANTUM_STABILITY_FEEDBACK_PLUGIN =
            holder(QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY);
    public static final DeferredBlock<QuantumFeedbackPluginBlock> QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN =
            holder(QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN_KEY);
    public static final DeferredBlock<QuantumEntanglementControllerBlock> QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER =
            holder(QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY);
    public static final DeferredBlock<QuantumEntanglementStructureBlock> QUANTUM_ENTANGLEMENT_CASING =
            holder(QUANTUM_ENTANGLEMENT_CASING_KEY);
    public static final DeferredBlock<QuantumEntanglementStructureBlock> QUANTUM_ENTANGLEMENT_ANTENNA =
            holder(QUANTUM_ENTANGLEMENT_ANTENNA_KEY);
    public static final DeferredBlock<QuantumEntanglementPortBlock> QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT =
            holder(QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY);
    public static final DeferredBlock<QuantumEntanglementResourcePortBlock> QUANTUM_ENTANGLEMENT_INPUT_PORT =
            holder(QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY);
    public static final DeferredBlock<QuantumEntanglementResourcePortBlock> QUANTUM_ENTANGLEMENT_OUTPUT_PORT =
            holder(QUANTUM_ENTANGLEMENT_OUTPUT_PORT_KEY);
    public static final DeferredBlock<QuantumEntanglementRedstonePortBlock> QUANTUM_ENTANGLEMENT_REDSTONE_PORT =
            holder(QUANTUM_ENTANGLEMENT_REDSTONE_PORT_KEY);

    static <T extends Block> DeferredBlock<T> holder(ResourceLocation location) {
        return DeferredBlock.createBlock(location);
    }

    static void register(RegisterEvent.RegisterHelper<Block> helper) {
        BlockBehaviour.Properties normalProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(1.0F, 1000.0F);

        BlockBehaviour.Properties deviceProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(1.0F, 1000.0F)
                .noOcclusion();

        BlockBehaviour.Properties quantumCasingProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(3.0F, 1200.0F)
                .pushReaction(PushReaction.BLOCK)
                .dynamicShape()
                .requiresCorrectToolForDrops();

        BlockBehaviour.Properties quantumGlassProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_LIGHT_BLUE)
                .sound(SoundType.GLASS)
                .strength(1.5F, 1200.0F)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK)
                .dynamicShape()
                .requiresCorrectToolForDrops();

        BlockBehaviour.Properties quantumInterfaceProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(3.0F, 1200.0F)
                .pushReaction(PushReaction.BLOCK)
                .dynamicShape()
                .requiresCorrectToolForDrops();

        BlockBehaviour.Properties quantumControllerProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(3.0F, 1200.0F)
                .pushReaction(PushReaction.BLOCK)
                .requiresCorrectToolForDrops();

        BlockBehaviour.Properties entanglementControllerProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(3.0F, 1200.0F)
                .pushReaction(PushReaction.BLOCK)
                .requiresCorrectToolForDrops();

        BlockBehaviour.Properties entanglementCasingProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(3.0F, 1200.0F)
                .pushReaction(PushReaction.BLOCK)
                .requiresCorrectToolForDrops();

        BlockBehaviour.Properties entanglementDeviceProps = BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .sound(SoundType.METAL)
                .strength(3.0F, 1200.0F)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK)
                .requiresCorrectToolForDrops();

        helper.register(ENDER_FLUX_BLOCK_KEY, new Block(normalProps));
        helper.register(NETHER_FLUX_BLOCK_KEY, new Block(normalProps));
        helper.register(ENDER_FLUX_STORAGE_KEY, new FluxStorageBlock.Ender(deviceProps));
        helper.register(NETHER_FLUX_STORAGE_KEY, new FluxStorageBlock.Nether(deviceProps));
        helper.register(FLUX_STORAGE_TERMINAL_KEY, new FluxStorageTerminalBlock(deviceProps));
        helper.register(FLUX_FE_STORAGE_KEY, new FluxFEStorageBlock(deviceProps));
        helper.register(FLUX_LINK_CRYSTAL_KEY, new FluxLinkCrystalBlock(deviceProps));
        helper.register(FLUX_EXTRACT_CRYSTAL_KEY, new FluxExtractCrystalBlock(deviceProps));
        helper.register(FLUX_AUTO_LINK_CRYSTAL_KEY, new FluxAutoLinkCrystalBlock(deviceProps));
        helper.register(FLUX_AUTO_EXTRACT_CRYSTAL_KEY, new FluxAutoExtractCrystalBlock(deviceProps));
        helper.register(TRANSMON_QUBIT_CHAIN_CASING_KEY, new QuantumBatteryStructureBlock(quantumCasingProps));
        helper.register(TRANSMON_QUBIT_CHAIN_CASING_GLASS_KEY, new QuantumBatteryStructureGlassBlock(quantumGlassProps));
        helper.register(WEAK_COUPLING_CAVITY_QED_CASING_KEY, new QuantumBatteryStructureBlock(quantumCasingProps));
        helper.register(WEAK_COUPLING_CAVITY_QED_CASING_GLASS_KEY, new QuantumBatteryStructureGlassBlock(quantumGlassProps));
        helper.register(STRONG_COUPLING_CAVITY_QED_CASING_KEY, new QuantumBatteryStructureBlock(quantumCasingProps));
        helper.register(STRONG_COUPLING_CAVITY_QED_CASING_GLASS_KEY, new QuantumBatteryStructureGlassBlock(quantumGlassProps));
        helper.register(QUANTUM_BATTERY_CONTROLLER_KEY, new QuantumBatteryControllerBlock(quantumControllerProps));
        helper.register(QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY,
                new QuantumEnergyConstraintInterfaceBlock(quantumInterfaceProps));
        helper.register(QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3_KEY,
                new QuantumEnergyConstraintInterface3x3Block(quantumInterfaceProps));
        helper.register(QUANTUM_SOLID_COOLING_PLUGIN_KEY,
                new QuantumSolidCoolingPluginBlock(quantumInterfaceProps));
        helper.register(QUANTUM_ADVANCED_COOLING_PLUGIN_KEY,
                new QuantumAdvancedCoolingPluginBlock(quantumInterfaceProps));
        helper.register(MINIATURE_UNIVERSE_KEY, new MiniatureUniverseBlock(quantumInterfaceProps));
        helper.register(QUANTUM_QUENCH_FUSE_TIER_1_KEY,
                new QuantumQuenchFusePluginBlock(quantumInterfaceProps));
        helper.register(QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY,
                new QuantumFeedbackPluginBlock(quantumInterfaceProps, QuantumFeedbackMetric.STABILITY));
        helper.register(QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN_KEY,
                new QuantumFeedbackPluginBlock(quantumInterfaceProps, QuantumFeedbackMetric.TEMPERATURE));
        helper.register(QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY,
                new QuantumEntanglementControllerBlock(entanglementControllerProps));
        helper.register(QUANTUM_ENTANGLEMENT_CASING_KEY,
                new QuantumEntanglementStructureBlock(entanglementCasingProps));
        helper.register(QUANTUM_ENTANGLEMENT_ANTENNA_KEY,
                new QuantumEntanglementStructureBlock(entanglementDeviceProps));
        helper.register(QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY,
                new QuantumEntanglementPortBlock(entanglementDeviceProps));
        helper.register(QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY,
                new QuantumEntanglementResourcePortBlock(entanglementDeviceProps));
        helper.register(QUANTUM_ENTANGLEMENT_OUTPUT_PORT_KEY,
                new QuantumEntanglementResourcePortBlock(entanglementDeviceProps));
        helper.register(QUANTUM_ENTANGLEMENT_REDSTONE_PORT_KEY,
                new QuantumEntanglementRedstonePortBlock(entanglementDeviceProps));
    }

    private RegistryBlocks() {}
}

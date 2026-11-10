package com.aquariverr.fluxod.register;

import com.mojang.datafixers.DSL;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.RegisterEvent;
import com.aquariverr.fluxod.block.entity.TileFluxAutoLinkCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxAutoExtractCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxExtractCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxLinkCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxFEStorage;
import com.aquariverr.fluxod.block.entity.TileFluxStorage;
import com.aquariverr.fluxod.block.entity.TileFluxStorageTerminal;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEnergyConstraintInterface;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.block.entity.TileQuantumEnergyConstraintEntanglementPort;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementResourcePort;
import com.aquariverr.fluxod.block.entity.TileQuantumSolidCoolingPlugin;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.block.entity.TileQuantumAdvancedCoolingPlugin;
import com.aquariverr.fluxod.block.entity.TileQuantumFeedbackPlugin;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import sonar.fluxnetworks.api.FluxCapabilities;

import java.util.Set;

public class RegistryBlockEntityTypes {
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxStorage.Ender>> ENDER_FLUX_STORAGE =
            holder(RegistryBlocks.ENDER_FLUX_STORAGE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxStorage.Nether>> NETHER_FLUX_STORAGE =
            holder(RegistryBlocks.NETHER_FLUX_STORAGE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxStorageTerminal>> FLUX_STORAGE_TERMINAL =
            holder(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxFEStorage>> FLUX_FE_STORAGE =
            holder(RegistryBlocks.FLUX_FE_STORAGE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxLinkCrystal>> FLUX_LINK_CRYSTAL =
            holder(RegistryBlocks.FLUX_LINK_CRYSTAL_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxExtractCrystal>> FLUX_EXTRACT_CRYSTAL =
            holder(RegistryBlocks.FLUX_EXTRACT_CRYSTAL_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxAutoLinkCrystal>> FLUX_AUTO_LINK_CRYSTAL =
            holder(RegistryBlocks.FLUX_AUTO_LINK_CRYSTAL_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileFluxAutoExtractCrystal>>
            FLUX_AUTO_EXTRACT_CRYSTAL = holder(RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumBatteryController>> QUANTUM_BATTERY_CONTROLLER =
            holder(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumEnergyConstraintInterface>>
            QUANTUM_ENERGY_CONSTRAINT_INTERFACE = holder(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumSolidCoolingPlugin>>
            QUANTUM_SOLID_COOLING_PLUGIN = holder(RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumAdvancedCoolingPlugin>>
            QUANTUM_ADVANCED_COOLING_PLUGIN = holder(RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileMiniatureUniverse>>
            MINIATURE_UNIVERSE = holder(RegistryBlocks.MINIATURE_UNIVERSE_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumFeedbackPlugin>>
            QUANTUM_FEEDBACK_PLUGIN = holder(RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumEntanglementController>>
            QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER = holder(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumEnergyConstraintEntanglementPort>>
            QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT =
            holder(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TileQuantumEntanglementResourcePort>>
            QUANTUM_ENTANGLEMENT_RESOURCE_PORT = holder(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY);

    static <T extends BlockEntity> DeferredHolder<BlockEntityType<?>, BlockEntityType<T>> holder(ResourceLocation location) {
        return DeferredHolder.create(BuiltInRegistries.BLOCK_ENTITY_TYPE.key(), location);
    }

    static void register(RegisterEvent.RegisterHelper<BlockEntityType<?>> helper) {
        helper.register(RegistryBlocks.ENDER_FLUX_STORAGE_KEY, new BlockEntityType<>(
                TileFluxStorage.Ender::new,
                Set.of(RegistryBlocks.ENDER_FLUX_STORAGE.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.NETHER_FLUX_STORAGE_KEY, new BlockEntityType<>(
                TileFluxStorage.Nether::new,
                Set.of(RegistryBlocks.NETHER_FLUX_STORAGE.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_STORAGE_TERMINAL_KEY, new BlockEntityType<>(
                TileFluxStorageTerminal::new,
                Set.of(RegistryBlocks.FLUX_STORAGE_TERMINAL.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_FE_STORAGE_KEY, new BlockEntityType<>(
                TileFluxFEStorage::new,
                Set.of(RegistryBlocks.FLUX_FE_STORAGE.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_LINK_CRYSTAL_KEY, new BlockEntityType<>(
                TileFluxLinkCrystal::new,
                Set.of(RegistryBlocks.FLUX_LINK_CRYSTAL.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_EXTRACT_CRYSTAL_KEY, new BlockEntityType<>(
                TileFluxExtractCrystal::new,
                Set.of(RegistryBlocks.FLUX_EXTRACT_CRYSTAL.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_AUTO_LINK_CRYSTAL_KEY, new BlockEntityType<>(
                TileFluxAutoLinkCrystal::new,
                Set.of(RegistryBlocks.FLUX_AUTO_LINK_CRYSTAL.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL_KEY, new BlockEntityType<>(
                TileFluxAutoExtractCrystal::new,
                Set.of(RegistryBlocks.FLUX_AUTO_EXTRACT_CRYSTAL.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER_KEY, new BlockEntityType<>(
                TileQuantumBatteryController::new,
                Set.of(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY, new BlockEntityType<>(
                TileQuantumEnergyConstraintInterface::new,
                Set.of(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(),
                        RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN_KEY, new BlockEntityType<>(
                TileQuantumSolidCoolingPlugin::new,
                Set.of(RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN_KEY, new BlockEntityType<>(
                TileQuantumAdvancedCoolingPlugin::new,
                Set.of(RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.MINIATURE_UNIVERSE_KEY, new BlockEntityType<>(
                TileMiniatureUniverse::new, Set.of(RegistryBlocks.MINIATURE_UNIVERSE.get()), DSL.remainderType()));
        helper.register(RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY, new BlockEntityType<>(
                TileQuantumFeedbackPlugin::new,
                Set.of(RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN.get(),
                        RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY, new BlockEntityType<>(
                TileQuantumEntanglementController::new,
                Set.of(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY, new BlockEntityType<>(
                TileQuantumEnergyConstraintEntanglementPort::new,
                Set.of(RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get()),
                DSL.remainderType()
        ));
        helper.register(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY, new BlockEntityType<>(
                TileQuantumEntanglementResourcePort::new,
                Set.of(RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get(),
                        RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get()),
                DSL.remainderType()
        ));
    }

    static void registerBlockCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MINIATURE_UNIVERSE.get(),
                (be, side) -> be.itemHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, MINIATURE_UNIVERSE.get(),
                (be, side) -> be.fluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FLUX_STORAGE_TERMINAL.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, FLUX_FE_STORAGE.get(),
                (be, side) -> be.getInventory());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, FLUX_FE_STORAGE.get(),
                (be, side) -> be.getEnergyCapability(Capabilities.EnergyStorage.BLOCK, side));
        event.registerBlockEntity(FluxCapabilities.BLOCK, FLUX_FE_STORAGE.get(),
                (be, side) -> be.getEnergyCapability(FluxCapabilities.BLOCK, side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(),
                (be, side) -> be.getEnergyCapability(side));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get(),
                (be, side) -> be.getEnergyCapability(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, QUANTUM_SOLID_COOLING_PLUGIN.get(),
                (be, side) -> be.getInputHandler());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, QUANTUM_ADVANCED_COOLING_PLUGIN.get(),
                (be, side) -> be.getInputFluidHandler());
        QuantumCoolingChemicalIntegration.registerCapability(event, QUANTUM_ADVANCED_COOLING_PLUGIN.get());
        QuantumCoolingChemicalIntegration.registerMiniatureCapability(event, MINIATURE_UNIVERSE.get());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, QUANTUM_ENTANGLEMENT_RESOURCE_PORT.get(),
                (be, side) -> be.getItemCapability(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, QUANTUM_ENTANGLEMENT_RESOURCE_PORT.get(),
                (be, side) -> be.getFluidCapability(side));
        QuantumCoolingChemicalIntegration.registerEntanglementCapability(
                event, QUANTUM_ENTANGLEMENT_RESOURCE_PORT.get());
    }

    private RegistryBlockEntityTypes() {}
}

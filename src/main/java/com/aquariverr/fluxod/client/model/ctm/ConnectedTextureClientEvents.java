package com.aquariverr.fluxod.client.model.ctm;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.client.render.QuantumBatteryControllerRenderer;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = FluxOverdrive.MODID, value = Dist.CLIENT)
public final class ConnectedTextureClientEvents {

    @SubscribeEvent
    static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(QuantumBatteryControllerRenderer.ADVANCED_INTERFACE_TIER_2_MODEL);
        event.register(QuantumBatteryControllerRenderer.ADVANCED_INTERFACE_TIER_3_MODEL);
    }

    @SubscribeEvent
    static void modifyBakingResult(ModelEvent.ModifyBakingResult event) {
        wrap(event, RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING.get(), RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_KEY);
        wrap(event, RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS.get(), RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS_KEY);
        wrap(event, RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING.get(), RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_KEY);
        wrap(event, RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS.get(), RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS_KEY);
        wrap(event, RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING.get(), RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_KEY);
        wrap(event, RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS.get(), RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS_KEY);
        wrap(event, RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING.get(),
                RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING_KEY,
                QuantumEntanglementConnectedTexture::compute);
        wrapEntanglementPart(event, RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get(),
                RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER_KEY,
                "entanglement/controller/side", "entanglement/controller/top");
        wrapEntanglementPart(event, RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA.get(),
                RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA_KEY,
                "entanglement/antenna/side", "entanglement/antenna/top");
        wrapEntanglementPart(event, RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get(),
                RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT_KEY,
                "entanglement/port/energy_constraint/all");
        wrapEntanglementPart(event, RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get(),
                RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT_KEY,
                "entanglement/port/input/front", "entanglement/port/input/side",
                "entanglement/port/input/top");
        wrapEntanglementPart(event, RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get(),
                RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT_KEY,
                "entanglement/port/output/front", "entanglement/port/output/side",
                "entanglement/port/output/top");
        wrapEntanglementPart(event, RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get(),
                RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT_KEY,
                "entanglement/port/redstone/front", "entanglement/port/redstone/side",
                "entanglement/port/redstone/top");
        wrapDynamic(event, RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(),
                RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_KEY);
        wrapDynamic(event, RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3.get(),
                RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3_KEY);
        wrapDynamic(event, RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN.get(),
                RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN_KEY);
        wrapDynamic(event, RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN.get(),
                RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN_KEY);
        wrapDynamic(event, RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1.get(),
                RegistryBlocks.QUANTUM_QUENCH_FUSE_TIER_1_KEY);
        wrapDynamic(event, RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN.get(),
                RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN_KEY);
        wrapDynamic(event, RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN.get(),
                RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN_KEY);
        wrapDynamic(event, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get(),
                RegistryBlocks.QUANTUM_BATTERY_CONTROLLER_KEY, true);
    }

    @SubscribeEvent
    static void bakingCompleted(ModelEvent.BakingCompleted event) {
        // Baking runs on a worker. GPU meshes can only be disposed after application on the render thread.
        QuantumBatteryControllerRenderer.clearCache();
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, ResourceLocation blockId) {
        wrap(event, block, blockId, ConnectedTextureLogic::compute);
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, ResourceLocation blockId,
                             ConnectedTextureBakedModel.ConnectionComputer connectionComputer) {
        ConnectedTextureSet textures = ConnectedTextureSet.bake(blockId, event.getTextureGetter());
        if (textures == null) {
            return;
        }
        wrap(event, block, blockId, List.of(textures), connectionComputer);
    }

    private static void wrapEntanglementPart(ModelEvent.ModifyBakingResult event, Block block,
                                             ResourceLocation blockId, String... texturePaths) {
        List<ConnectedTextureSet> textureSets = new ArrayList<>(texturePaths.length);
        for (String texturePath : texturePaths) {
            ConnectedTextureSet textures = ConnectedTextureSet.bake(
                    FluxOverdrive.location("quantum/" + texturePath),
                    FluxOverdrive.location("block/quantum/" + texturePath),
                    event.getTextureGetter());
            if (textures == null) {
                return;
            }
            textureSets.add(textures);
        }
        wrap(event, block, blockId, textureSets, QuantumEntanglementConnectedTexture::compute);
    }

    private static void wrap(ModelEvent.ModifyBakingResult event, Block block, ResourceLocation blockId,
                             List<ConnectedTextureSet> textureSets,
                             ConnectedTextureBakedModel.ConnectionComputer connectionComputer) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(blockId, state);
            BakedModel original = models.get(location);
            if (original == null) {
                FluxOverdrive.LOGGER.error("Missing block model {} for connected texture", location);
                continue;
            }
            models.put(location, new ConnectedTextureBakedModel(original, textureSets, connectionComputer));
        }
    }

    private static void wrapDynamic(ModelEvent.ModifyBakingResult event, Block block, ResourceLocation blockId) {
        wrapDynamic(event, block, blockId, false);
    }

    private static void wrapDynamic(ModelEvent.ModifyBakingResult event, Block block, ResourceLocation blockId,
                                    boolean controllerModel) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (BlockState state : block.getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(blockId, state);
            BakedModel original = models.get(location);
            if (original == null) {
                FluxOverdrive.LOGGER.error("Missing block model {} for dynamic structure rendering", location);
                continue;
            }
            models.put(location, new DynamicStructureBakedModel(original, controllerModel));
        }
    }

    private ConnectedTextureClientEvents() {}
}

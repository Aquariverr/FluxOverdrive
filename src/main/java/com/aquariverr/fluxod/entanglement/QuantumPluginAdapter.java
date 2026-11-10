package com.aquariverr.fluxod.entanglement;

import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public interface QuantumPluginAdapter {

    ResourceLocation id();

    boolean matches(BlockState state, @Nullable BlockEntity blockEntity);

    default String localEndpointId(BlockState state, @Nullable BlockEntity blockEntity) {
        return QuantumPluginEndpointKey.MAIN_ENDPOINT;
    }

    default int inputResources(BlockState state, @Nullable BlockEntity blockEntity) {
        return 0;
    }

    default int outputResources(BlockState state, @Nullable BlockEntity blockEntity) {
        return 0;
    }

    default boolean supportsRemoteMenu(BlockState state, @Nullable BlockEntity blockEntity) {
        return false;
    }

    default boolean openRemoteMenu(ServerPlayer player, BlockEntity blockEntity,
                                   TileQuantumEntanglementController accessController,
                                   QuantumPluginEndpointKey endpointKey) {
        return false;
    }
}

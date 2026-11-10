package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record SelectEntanglementPluginPayload(int containerId, BlockPos controllerPos,
                                              int directoryRevision, CompoundTag endpointKey)
        implements CustomPacketPayload {

    public static final Type<SelectEntanglementPluginPayload> TYPE =
            new Type<>(FluxOverdrive.location("select_entanglement_plugin"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SelectEntanglementPluginPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SelectEntanglementPluginPayload::containerId,
                    BlockPos.STREAM_CODEC, SelectEntanglementPluginPayload::controllerPos,
                    ByteBufCodecs.VAR_INT, SelectEntanglementPluginPayload::directoryRevision,
                    ByteBufCodecs.COMPOUND_TAG, SelectEntanglementPluginPayload::endpointKey,
                    SelectEntanglementPluginPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

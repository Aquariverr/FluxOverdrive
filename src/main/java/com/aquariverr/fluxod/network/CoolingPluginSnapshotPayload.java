package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record CoolingPluginSnapshotPayload(int containerId, CompoundTag snapshot)
        implements CustomPacketPayload {

    public static final Type<CoolingPluginSnapshotPayload> TYPE =
            new Type<>(FluxOverdrive.location("cooling_plugin_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CoolingPluginSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, CoolingPluginSnapshotPayload::containerId,
                    ByteBufCodecs.COMPOUND_TAG, CoolingPluginSnapshotPayload::snapshot,
                    CoolingPluginSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

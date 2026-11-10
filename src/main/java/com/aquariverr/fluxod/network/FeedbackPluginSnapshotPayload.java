package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record FeedbackPluginSnapshotPayload(int containerId, CompoundTag snapshot)
        implements CustomPacketPayload {

    public static final Type<FeedbackPluginSnapshotPayload> TYPE =
            new Type<>(FluxOverdrive.location("feedback_plugin_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FeedbackPluginSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FeedbackPluginSnapshotPayload::containerId,
                    ByteBufCodecs.COMPOUND_TAG, FeedbackPluginSnapshotPayload::snapshot,
                    FeedbackPluginSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

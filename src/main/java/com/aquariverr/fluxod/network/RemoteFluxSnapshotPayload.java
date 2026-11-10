package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record RemoteFluxSnapshotPayload(GlobalPos targetPos, CompoundTag updateTag)
        implements CustomPacketPayload {

    public static final Type<RemoteFluxSnapshotPayload> TYPE =
            new Type<>(FluxOverdrive.location("remote_flux_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteFluxSnapshotPayload> STREAM_CODEC =
            StreamCodec.composite(
                    GlobalPos.STREAM_CODEC, RemoteFluxSnapshotPayload::targetPos,
                    ByteBufCodecs.COMPOUND_TAG, RemoteFluxSnapshotPayload::updateTag,
                    RemoteFluxSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

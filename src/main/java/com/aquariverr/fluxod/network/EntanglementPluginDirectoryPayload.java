package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EntanglementPluginDirectoryPayload(int containerId, CompoundTag snapshot)
        implements CustomPacketPayload {

    public static final Type<EntanglementPluginDirectoryPayload> TYPE =
            new Type<>(FluxOverdrive.location("entanglement_plugin_directory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntanglementPluginDirectoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, EntanglementPluginDirectoryPayload::containerId,
                    ByteBufCodecs.COMPOUND_TAG, EntanglementPluginDirectoryPayload::snapshot,
                    EntanglementPluginDirectoryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

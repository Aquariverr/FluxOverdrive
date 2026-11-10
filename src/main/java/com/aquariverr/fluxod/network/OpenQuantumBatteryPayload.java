package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenQuantumBatteryPayload(BlockPos controllerPos) implements CustomPacketPayload {

    public static final Type<OpenQuantumBatteryPayload> TYPE =
            new Type<>(FluxOverdrive.location("open_quantum_battery"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenQuantumBatteryPayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, OpenQuantumBatteryPayload::controllerPos,
                    OpenQuantumBatteryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

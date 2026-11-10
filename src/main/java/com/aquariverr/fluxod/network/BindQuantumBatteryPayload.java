package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record BindQuantumBatteryPayload(int containerId, BlockPos controllerPos,
                                        int targetX, int targetY, int targetZ, String dimensionId)
        implements CustomPacketPayload {

    public static final Type<BindQuantumBatteryPayload> TYPE =
            new Type<>(FluxOverdrive.location("bind_quantum_battery"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BindQuantumBatteryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BindQuantumBatteryPayload::containerId,
                    BlockPos.STREAM_CODEC, BindQuantumBatteryPayload::controllerPos,
                    ByteBufCodecs.VAR_INT, BindQuantumBatteryPayload::targetX,
                    ByteBufCodecs.VAR_INT, BindQuantumBatteryPayload::targetY,
                    ByteBufCodecs.VAR_INT, BindQuantumBatteryPayload::targetZ,
                    ByteBufCodecs.stringUtf8(128), BindQuantumBatteryPayload::dimensionId,
                    BindQuantumBatteryPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

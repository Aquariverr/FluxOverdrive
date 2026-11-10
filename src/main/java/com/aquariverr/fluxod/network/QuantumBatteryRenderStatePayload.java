package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record QuantumBatteryRenderStatePayload(BlockPos controllerPos,
                                               int energyLogBasisPoints,
                                               int stabilityBasisPoints,
                                               int leakEventSequence,
                                               boolean constraintPowered,
                                               boolean instabilityPenaltiesEnabled)
        implements CustomPacketPayload {

    public static final Type<QuantumBatteryRenderStatePayload> TYPE =
            new Type<>(FluxOverdrive.location("quantum_battery_render_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuantumBatteryRenderStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, QuantumBatteryRenderStatePayload::controllerPos,
                    ByteBufCodecs.VAR_INT, QuantumBatteryRenderStatePayload::energyLogBasisPoints,
                    ByteBufCodecs.VAR_INT, QuantumBatteryRenderStatePayload::stabilityBasisPoints,
                    ByteBufCodecs.VAR_INT, QuantumBatteryRenderStatePayload::leakEventSequence,
                    ByteBufCodecs.BOOL, QuantumBatteryRenderStatePayload::constraintPowered,
                    ByteBufCodecs.BOOL, QuantumBatteryRenderStatePayload::instabilityPenaltiesEnabled,
                    QuantumBatteryRenderStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

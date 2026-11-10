package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackCurve;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

public record UpdateFeedbackCurvePayload(int containerId, List<QuantumFeedbackCurve.Point> points)
        implements CustomPacketPayload {

    public UpdateFeedbackCurvePayload {
        points = List.copyOf(points);
        if (points.size() < QuantumFeedbackCurve.MIN_POINTS
                || points.size() > QuantumFeedbackCurve.MAX_POINTS) {
            throw new IllegalArgumentException("Feedback curves require 2-32 points");
        }
    }

    public static final Type<UpdateFeedbackCurvePayload> TYPE =
            new Type<>(FluxOverdrive.location("update_feedback_curve"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFeedbackCurvePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public UpdateFeedbackCurvePayload decode(RegistryFriendlyByteBuf buffer) {
                    int containerId = buffer.readVarInt();
                    int pointCount = buffer.readVarInt();
                    if (pointCount < QuantumFeedbackCurve.MIN_POINTS
                            || pointCount > QuantumFeedbackCurve.MAX_POINTS) {
                        throw new DecoderException("Invalid feedback curve point count: " + pointCount);
                    }
                    List<QuantumFeedbackCurve.Point> points = new ArrayList<>(pointCount);
                    for (int index = 0; index < pointCount; index++) {
                        int threshold = buffer.readVarInt();
                        int signal = buffer.readUnsignedByte();
                        if (signal > QuantumFeedbackCurve.MAX_SIGNAL) {
                            throw new DecoderException("Invalid feedback signal: " + signal);
                        }
                        points.add(new QuantumFeedbackCurve.Point(threshold, signal));
                    }
                    return new UpdateFeedbackCurvePayload(containerId, points);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buffer, UpdateFeedbackCurvePayload payload) {
                    buffer.writeVarInt(payload.containerId());
                    buffer.writeVarInt(payload.points().size());
                    for (QuantumFeedbackCurve.Point point : payload.points()) {
                        buffer.writeVarInt(point.threshold());
                        buffer.writeByte(point.signal());
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}

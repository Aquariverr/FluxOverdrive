package com.aquariverr.fluxod.entanglement;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public record QuantumPluginEndpointKey(ResourceLocation adapterId, BlockPos relativePos,
                                       String localEndpointId) {

    private static final String ADAPTER_TAG = "Adapter";
    private static final String POSITION_TAG = "RelativePos";
    private static final String LOCAL_ID_TAG = "LocalEndpoint";
    public static final String MAIN_ENDPOINT = "main";

    public QuantumPluginEndpointKey {
        relativePos = relativePos.immutable();
        localEndpointId = localEndpointId == null || localEndpointId.isBlank()
                ? MAIN_ENDPOINT
                : localEndpointId;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(ADAPTER_TAG, adapterId.toString());
        tag.putLong(POSITION_TAG, relativePos.asLong());
        tag.putString(LOCAL_ID_TAG, localEndpointId);
        return tag;
    }

    public void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeResourceLocation(adapterId);
        buffer.writeBlockPos(relativePos);
        buffer.writeUtf(localEndpointId);
    }

    public static QuantumPluginEndpointKey read(RegistryFriendlyByteBuf buffer) {
        return new QuantumPluginEndpointKey(buffer.readResourceLocation(), buffer.readBlockPos(), buffer.readUtf());
    }

    @Nullable
    public static QuantumPluginEndpointKey load(CompoundTag tag) {
        if (!tag.contains(ADAPTER_TAG) || !tag.contains(POSITION_TAG)) return null;
        ResourceLocation adapterId = ResourceLocation.tryParse(tag.getString(ADAPTER_TAG));
        if (adapterId == null) return null;
        String localId = tag.contains(LOCAL_ID_TAG) ? tag.getString(LOCAL_ID_TAG) : MAIN_ENDPOINT;
        return new QuantumPluginEndpointKey(adapterId, BlockPos.of(tag.getLong(POSITION_TAG)), localId);
    }
}

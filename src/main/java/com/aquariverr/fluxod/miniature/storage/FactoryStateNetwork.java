package com.aquariverr.fluxod.miniature.storage;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.CustomData;

public final class FactoryStateNetwork {

    public static final String OMITTED_TAG = "synchronizationOmitted";
    public static final StreamCodec<RegistryFriendlyByteBuf, CustomData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CustomData.CODEC)
                    .map(value -> value, FactoryStateNetwork::forSynchronization);

    private static CustomData forSynchronization(CustomData saved) {
        try {
            FactoryStateCodec.requireEncodable(saved.copyTag());
            return saved;
        } catch (IllegalArgumentException exception) {
            // Unreadable historical data stays on disk and in the server item; only its client view is reduced.
            CompoundTag unavailable = new CompoundTag();
            unavailable.putBoolean(OMITTED_TAG, true);
            return CustomData.of(unavailable);
        }
    }

    private FactoryStateNetwork() {}
}

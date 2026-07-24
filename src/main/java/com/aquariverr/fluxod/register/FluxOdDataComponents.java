package com.aquariverr.fluxod.register;

import com.mojang.serialization.Codec;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.CustomData;

public class FluxOdDataComponents {

    public static final DeferredRegister<DataComponentType<?>> REGISTRY =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, FluxOverdrive.MODID);

    public static final DataComponentType<Long> TOTAL_CAPACITY = DataComponentType.<Long>builder()
            .persistent(Codec.LONG)
            .networkSynchronized(ByteBufCodecs.VAR_LONG)
            .build();

    public static final DataComponentType<GlobalPos> FLUX_LINK_BINDER = DataComponentType.<GlobalPos>builder()
            .persistent(GlobalPos.CODEC)
            .networkSynchronized(GlobalPos.STREAM_CODEC)
            .build();

    public static final DataComponentType<CustomData> LINK_TARGETS = DataComponentType.<CustomData>builder()
            .persistent(CustomData.CODEC)
            .build();

    static {
        REGISTRY.register("total_capacity", () -> TOTAL_CAPACITY);
        REGISTRY.register("flux_link_binder", () -> FLUX_LINK_BINDER);
        REGISTRY.register("link_targets", () -> LINK_TARGETS);
    }

    private FluxOdDataComponents() {}
}

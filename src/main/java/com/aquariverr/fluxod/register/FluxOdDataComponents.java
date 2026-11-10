package com.aquariverr.fluxod.register;

import com.mojang.serialization.Codec;
import com.aquariverr.fluxod.miniature.storage.FactoryStateNetwork;
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

    public static final DataComponentType<GlobalPos> QUANTUM_BATTERY_BINDER = DataComponentType.<GlobalPos>builder()
            .persistent(GlobalPos.CODEC)
            .networkSynchronized(GlobalPos.STREAM_CODEC)
            .build();

    public static final DataComponentType<CustomData> LINK_TARGETS = DataComponentType.<CustomData>builder()
            .persistent(CustomData.CODEC)
            .build();

    public static final DataComponentType<CustomData> QUANTUM_BATTERY_DATA = DataComponentType.<CustomData>builder()
            .persistent(CustomData.CODEC)
            .build();

    public static final DataComponentType<CustomData> QUANTUM_FEEDBACK_CURVE = DataComponentType.<CustomData>builder()
            .persistent(CustomData.CODEC)
            .build();

    public static final DataComponentType<CustomData> MINIATURE_UNIVERSE_STATE = DataComponentType.<CustomData>builder()
            .persistent(CustomData.CODEC)
            .networkSynchronized(FactoryStateNetwork.STREAM_CODEC)
            .build();

    static {
        REGISTRY.register("total_capacity", () -> TOTAL_CAPACITY);
        REGISTRY.register("flux_link_binder", () -> FLUX_LINK_BINDER);
        REGISTRY.register("quantum_battery_binder", () -> QUANTUM_BATTERY_BINDER);
        REGISTRY.register("link_targets", () -> LINK_TARGETS);
        REGISTRY.register("quantum_battery_data", () -> QUANTUM_BATTERY_DATA);
        REGISTRY.register("quantum_feedback_curve", () -> QUANTUM_FEEDBACK_CURVE);
        REGISTRY.register("miniature_universe_state", () -> MINIATURE_UNIVERSE_STATE);
    }

    private FluxOdDataComponents() {}
}

package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

public final class QuantumCoolingMedia {

    public static final long TARGET_273_K = 273_000L;
    public static final long TARGET_263_K = 263_000L;
    public static final long TARGET_253_K = 253_000L;

    public static final TagKey<Item> ITEMS_273_K = itemTag("quantum_coolants_273k");
    public static final TagKey<Item> ITEMS_263_K = itemTag("quantum_coolants_263k");
    public static final TagKey<Item> ITEMS_253_K = itemTag("quantum_coolants_253k");
    public static final TagKey<Fluid> FLUIDS_273_K = fluidTag("quantum_coolants_273k");
    public static final TagKey<Fluid> FLUIDS_263_K = fluidTag("quantum_coolants_263k");
    public static final TagKey<Fluid> FLUIDS_253_K = fluidTag("quantum_coolants_253k");

    public static long targetFor(ItemStack stack) {
        if (stack.is(ITEMS_253_K)) return TARGET_253_K;
        if (stack.is(ITEMS_263_K)) return TARGET_263_K;
        if (stack.is(ITEMS_273_K)) return TARGET_273_K;
        return QuantumBatteryCoolingModel.NO_TARGET;
    }

    public static long targetFor(FluidStack stack) {
        if (stack.is(FLUIDS_253_K)) return TARGET_253_K;
        if (stack.is(FLUIDS_263_K)) return TARGET_263_K;
        if (stack.is(FLUIDS_273_K)) return TARGET_273_K;
        return QuantumBatteryCoolingModel.NO_TARGET;
    }

    public static boolean isSolidCoolant(ItemStack stack) {
        return targetFor(stack) != QuantumBatteryCoolingModel.NO_TARGET;
    }

    public static boolean isFluidCoolant(FluidStack stack) {
        return targetFor(stack) != QuantumBatteryCoolingModel.NO_TARGET;
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, FluxOverdrive.location(path));
    }

    private static TagKey<Fluid> fluidTag(String path) {
        return TagKey.create(Registries.FLUID, FluxOverdrive.location(path));
    }

    private QuantumCoolingMedia() {}
}

package com.aquariverr.fluxod.integration.mekanism;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

public interface OptionalChemicalTank {

    boolean isAvailable();

    long getAmount();

    long getCapacity();

    long getTargetMilliKelvin();

    String getChemicalId();

    int getTint();

    long extract(long amount);

    boolean interactWithHeldItem(Player player, InteractionHand hand);

    CompoundTag serializeNBT(HolderLookup.Provider registries);

    void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag);

    Object getCapability();
}

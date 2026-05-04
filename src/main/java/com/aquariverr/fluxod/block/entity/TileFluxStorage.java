package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import sonar.fluxnetworks.common.device.FluxStorageHandler;

import javax.annotation.Nonnull;

public abstract class TileFluxStorage extends sonar.fluxnetworks.common.device.TileFluxStorage {

    protected TileFluxStorage(@Nonnull BlockEntityType<?> type, BlockPos pos, BlockState state,
                               FluxStorageHandler handler) {
        super(type, pos, state, handler);
    }

    public static class Ender extends TileFluxStorage {

        public Ender(@Nonnull BlockPos pos, @Nonnull BlockState state) {
            super(RegistryBlockEntityTypes.ENDER_FLUX_STORAGE.get(), pos, state,
                    new EnderHandler());
        }

        @Nonnull
        @Override
        public ItemStack getDisplayStack() {
            return writeToDisplayStack(new ItemStack(getBlockState().getBlock()));
        }
    }

    public static class Nether extends TileFluxStorage {

        public Nether(@Nonnull BlockPos pos, @Nonnull BlockState state) {
            super(RegistryBlockEntityTypes.NETHER_FLUX_STORAGE.get(), pos, state,
                    new NetherHandler());
        }

        @Nonnull
        @Override
        public ItemStack getDisplayStack() {
            return writeToDisplayStack(new ItemStack(getBlockState().getBlock()));
        }
    }

    private static class EnderHandler extends FluxStorageHandler {

        EnderHandler() {
            super(Config.enderTransfer);
        }

        @Override
        public long getMaxEnergyStorage() {
            return Config.enderCapacity;
        }
    }

    private static class NetherHandler extends FluxStorageHandler {

        NetherHandler() {
            super(Config.netherTransfer);
        }

        @Override
        public long getMaxEnergyStorage() {
            return Config.netherCapacity;
        }
    }
}

package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.block.entity.TileFluxStorage;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public abstract class FluxStorageBlock extends sonar.fluxnetworks.common.block.FluxStorageBlock {

    protected FluxStorageBlock(Properties props) {
        super(props);
    }

    public static class Ender extends FluxStorageBlock {

        public Ender(Properties props) {
            super(props);
        }

        @Override
        public long getEnergyCapacity() {
            return Config.enderCapacity;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileFluxStorage.Ender(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type == RegistryBlockEntityTypes.ENDER_FLUX_STORAGE.get()) {
                return TileFluxDevice.getTicker(level);
            }
            return null;
        }
    }

    public static class Nether extends FluxStorageBlock {

        public Nether(Properties props) {
            super(props);
        }

        @Override
        public long getEnergyCapacity() {
            return Config.netherCapacity;
        }

        @Nullable
        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new TileFluxStorage.Nether(pos, state);
        }

        @Nullable
        @Override
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type == RegistryBlockEntityTypes.NETHER_FLUX_STORAGE.get()) {
                return TileFluxDevice.getTicker(level);
            }
            return null;
        }
    }
}

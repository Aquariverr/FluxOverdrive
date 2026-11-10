package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.TileQuantumEnergyConstraintInterface;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class QuantumEnergyConstraintInterface3x3Block extends QuantumBatteryStructureBlock
        implements EntityBlock, QuantumBatteryPlugin {

    public QuantumEnergyConstraintInterface3x3Block(Properties properties) {
        super(properties);
    }

    @Override
    public int getPluginTier() {
        return 2;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuantumEnergyConstraintInterface(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                    BlockEntityType<T> type) {
        return null;
    }

}

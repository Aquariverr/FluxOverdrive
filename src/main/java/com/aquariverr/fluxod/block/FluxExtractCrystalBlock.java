package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.TileFluxExtractCrystal;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import sonar.fluxnetworks.common.block.FluxConnectorBlock;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class FluxExtractCrystalBlock extends FluxConnectorBlock {

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4.7, 2, 4.7, 11.3, 14, 11.3),
            Block.box(1.3, 5, 10, 4.7, 11, 12),
            Block.box(11.7, 6, 4, 14.3, 10, 6));

    public FluxExtractCrystalBlock(Properties props) {
        super(props);
    }

    @Nonnull
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFluxExtractCrystal(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (type == RegistryBlockEntityTypes.FLUX_EXTRACT_CRYSTAL.get()) {
            return TileFluxDevice.getTicker(level);
        }
        return null;
    }
}

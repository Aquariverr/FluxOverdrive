package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.TileFluxAutoExtractCrystal;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import sonar.fluxnetworks.common.block.FluxConnectorBlock;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public class FluxAutoExtractCrystalBlock extends FluxConnectorBlock {

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(5.4, 1, 5.4, 10.6, 14, 10.6),
            Block.box(2.2, 2, 2.2, 4.8, 7, 4.8),
            Block.box(11.2, 6, 2.2, 13.8, 11, 4.8),
            Block.box(2.2, 6, 11.2, 4.8, 11, 13.8),
            Block.box(11.2, 2, 11.2, 13.8, 7, 13.8));
    private static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public FluxAutoExtractCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Nonnull
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFluxAutoExtractCrystal(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (type == RegistryBlockEntityTypes.FLUX_AUTO_EXTRACT_CRYSTAL.get()) {
            return TileFluxDevice.getTicker(level);
        }
        return null;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (level.isClientSide) return;

        BlockState currentState = level.getBlockState(pos);
        if (!currentState.is(this)) return;
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == currentState.getValue(POWERED)) return;
        level.setBlock(pos, currentState.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (!powered) return;

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof TileFluxAutoExtractCrystal crystal) crystal.triggerScan();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(POWERED);
    }
}

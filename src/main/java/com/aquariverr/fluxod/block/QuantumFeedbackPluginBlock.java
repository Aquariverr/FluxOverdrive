package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.QuantumFeedbackMetric;
import com.aquariverr.fluxod.block.entity.TileQuantumFeedbackPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public final class QuantumFeedbackPluginBlock extends QuantumBatteryStructureBlock
        implements EntityBlock, QuantumBatteryPlugin {

    private final QuantumFeedbackMetric metric;

    public QuantumFeedbackPluginBlock(Properties properties, QuantumFeedbackMetric metric) {
        super(properties);
        this.metric = metric;
    }

    public QuantumFeedbackMetric metric() {
        return metric;
    }

    @Override
    public int getPluginTier() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuantumFeedbackPlugin(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (type == RegistryBlockEntityTypes.QUANTUM_FEEDBACK_PLUGIN.get()) {
            return (currentLevel, currentPos, currentState, blockEntity) ->
                    ((TileQuantumFeedbackPlugin) blockEntity).serverTick();
        }
        return null;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof TileQuantumFeedbackPlugin plugin
                ? plugin.getRedstoneSignal()
                : 0;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileQuantumFeedbackPlugin plugin) {
            plugin.openMenu(player);
        }
        return ItemInteractionResult.SUCCESS;
    }
}

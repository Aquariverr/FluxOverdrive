package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.TileQuantumSolidCoolingPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class QuantumSolidCoolingPluginBlock extends QuantumBatteryStructureBlock
        implements EntityBlock, QuantumBatteryPlugin {

    public QuantumSolidCoolingPluginBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getPluginTier() {
        return 1;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuantumSolidCoolingPlugin(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileQuantumSolidCoolingPlugin plugin) {
            plugin.openMenu(player);
        }
        return ItemInteractionResult.SUCCESS;
    }
}

package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.TileQuantumAdvancedCoolingPlugin;
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
import net.neoforged.neoforge.fluids.FluidUtil;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class QuantumAdvancedCoolingPluginBlock extends QuantumBatteryStructureBlock
        implements EntityBlock, QuantumBatteryPlugin {

    public QuantumAdvancedCoolingPluginBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getPluginTier() {
        return 2;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuantumAdvancedCoolingPlugin(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof TileQuantumAdvancedCoolingPlugin plugin)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, plugin.getFluidTank())) {
            return ItemInteractionResult.SUCCESS;
        }
        if (plugin.getChemicalTank().interactWithHeldItem(player, hand)) {
            player.getInventory().setChanged();
            return ItemInteractionResult.SUCCESS;
        }
        plugin.openMenu(player);
        return ItemInteractionResult.SUCCESS;
    }
}

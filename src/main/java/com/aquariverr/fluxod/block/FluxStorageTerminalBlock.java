package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.TileFluxStorageTerminal;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import sonar.fluxnetworks.api.FluxTranslate;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nullable;
import java.util.List;

public class FluxStorageTerminalBlock extends FluxStorageBlock {

    public FluxStorageTerminalBlock(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, Item.@NotNull TooltipContext context, List<Component> tooltip, @NotNull TooltipFlag flag) {
        tooltip.add(FluxTranslate.FLUX_STORAGE_TOOLTIP.getComponent());
    }

    @Override
    public long getEnergyCapacity() {
        return 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TileFluxStorageTerminal(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        if (type == RegistryBlockEntityTypes.FLUX_STORAGE_TERMINAL.get()) {
            return TileFluxDevice.getTicker(level);
        }
        return null;
    }
}

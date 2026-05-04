package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.block.entity.TileFluxFEStorage;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.ChatFormatting;
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
import sonar.fluxnetworks.api.FluxTranslate;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.common.block.FluxConnectorBlock;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class FluxFEStorageBlock extends FluxConnectorBlock {

    public FluxFEStorageBlock(Properties props) {
        super(props);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(FluxTranslate.FLUX_POINT_TOOLTIP.getComponent());
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_fe_storage"));
        Long capacity = stack.get(FluxOdDataComponents.TOTAL_CAPACITY);
        long displayCapacity = capacity != null && capacity > 0 ? capacity : Config.feStorageCapacity;
        tooltip.add(FluxTranslate.FLUX_STORAGE_TOOLTIP_2.makeComponent(EnergyType.FE.getStorage(displayCapacity)));
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_fe_storage.capacity")
                .withStyle(ChatFormatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileFluxFEStorage(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type == RegistryBlockEntityTypes.FLUX_FE_STORAGE.get()) {
            return TileFluxDevice.getTicker(level);
        }
        return null;
    }
}

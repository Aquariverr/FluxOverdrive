package com.aquariverr.fluxod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import sonar.fluxnetworks.common.item.FluxDeviceItem;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class FluxLinkCrystalItem extends FluxDeviceItem {

    public FluxLinkCrystalItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_link_crystal")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_link_crystal.redstone")
                .withStyle(ChatFormatting.GRAY));
    }
}

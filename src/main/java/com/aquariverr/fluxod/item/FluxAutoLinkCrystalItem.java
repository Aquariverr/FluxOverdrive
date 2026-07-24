package com.aquariverr.fluxod.item;

import com.aquariverr.fluxod.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import sonar.fluxnetworks.common.item.FluxDeviceItem;

import java.util.List;

public class FluxAutoLinkCrystalItem extends FluxDeviceItem {
    public FluxAutoLinkCrystalItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_auto_link_crystal.1",
                Config.autoLinkRadius).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_auto_link_crystal.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_auto_link_crystal.3")
                .withStyle(ChatFormatting.GRAY));
    }
}

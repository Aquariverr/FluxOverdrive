package com.aquariverr.fluxod.item;

import com.aquariverr.fluxod.Config;
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
public class FluxAutoExtractCrystalItem extends FluxDeviceItem {

    public FluxAutoExtractCrystalItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_auto_extract_crystal.1",
                Config.autoLinkRadius).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_auto_extract_crystal.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_auto_extract_crystal.3")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_auto_extract_crystal.4")
                .withStyle(ChatFormatting.GRAY));
    }
}

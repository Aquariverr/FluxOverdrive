package com.aquariverr.fluxod.item;

import com.aquariverr.fluxod.block.entity.TileFluxLinkCrystal;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class FluxLinkToolItem extends Item {

    public FluxLinkToolItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Direction side = context.getClickedFace();
        if (player == null) return InteractionResult.PASS;

        BlockEntity clickedEntity = level.getBlockEntity(clickedPos);

        if (player.isShiftKeyDown()) {
            if (clickedEntity instanceof TileFluxLinkCrystal) {
                stack.set(FluxOdDataComponents.FLUX_LINK_BINDER, GlobalPos.of(level.dimension(), clickedPos));
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("message.flux_overdrive.crystal_bound").withStyle(ChatFormatting.GREEN),
                            true);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }

        if (isBound(stack)) {
            GlobalPos boundPos = getBound(stack);
            if (boundPos.dimension() != level.dimension()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("message.flux_overdrive.wrong_dimension").withStyle(ChatFormatting.RED),
                            true);
                }
                return InteractionResult.FAIL;
            }

            BlockPos crystalPos = boundPos.pos();
            BlockEntity crystalEntity = level.getBlockEntity(crystalPos);
            if (crystalEntity instanceof TileFluxLinkCrystal crystal) {
                if (crystal.isLinked(clickedPos)) {
                    crystal.removeLink(clickedPos);
                    if (!level.isClientSide) {
                        player.displayClientMessage(
                                Component.translatable("message.flux_overdrive.link_removed").withStyle(ChatFormatting.YELLOW),
                                true);
                    }
                } else {
                    crystal.addLink(clickedPos, side);
                    if (!level.isClientSide) {
                        player.displayClientMessage(
                                Component.translatable("message.flux_overdrive.link_added").withStyle(ChatFormatting.GREEN),
                                true);
                    }
                }
                return InteractionResult.SUCCESS;
            } else {
                if (!level.isClientSide) {
                    player.displayClientMessage(
                            Component.translatable("message.flux_overdrive.crystal_missing").withStyle(ChatFormatting.RED),
                            true);
                }
                return InteractionResult.FAIL;
            }
        }

        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (isBound(stack)) {
            GlobalPos pos = getBound(stack);
            tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_link_tool.bound",
                    pos.pos().toShortString(),
                    pos.dimension().location().toString()
            ).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_link_tool.unbound")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    public static boolean isBound(ItemStack stack) {
        return stack.has(FluxOdDataComponents.FLUX_LINK_BINDER);
    }

    public static GlobalPos getBound(ItemStack stack) {
        return stack.get(FluxOdDataComponents.FLUX_LINK_BINDER);
    }
}
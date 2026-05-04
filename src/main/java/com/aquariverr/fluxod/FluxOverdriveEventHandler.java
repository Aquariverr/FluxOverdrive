package com.aquariverr.fluxod;

import com.aquariverr.fluxod.block.entity.TileFluxLinkCrystal;
import com.aquariverr.fluxod.item.FluxLinkToolItem;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = FluxOverdrive.MODID)
public class FluxOverdriveEventHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!(stack.getItem() instanceof FluxLinkToolItem)) return;

        if (player.isShiftKeyDown() && event.getLevel().getBlockEntity(event.getPos()) instanceof TileFluxLinkCrystal) {
            event.setUseBlock(TriState.FALSE);
            return;
        }

        if (FluxLinkToolItem.isBound(stack)) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("resource")
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (stack.getItem() instanceof FluxLinkToolItem && player.isShiftKeyDown() && FluxLinkToolItem.isBound(stack)) {
            stack.remove(FluxOdDataComponents.FLUX_LINK_BINDER);
            event.setCanceled(true);
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                        Component.translatable("message.flux_overdrive.crystal_unbound").withStyle(ChatFormatting.YELLOW),
                        true);
            }
        }
    }
}
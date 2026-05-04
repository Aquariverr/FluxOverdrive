package com.aquariverr.fluxod.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import org.jetbrains.annotations.NotNull;
import sonar.fluxnetworks.api.FluxDataComponents;
import sonar.fluxnetworks.api.FluxTranslate;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.client.ClientCache;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.data.FluxDeviceConfigComponent;
import sonar.fluxnetworks.common.item.FluxStorageItem;

import java.util.List;

public class FluxStorageTerminalItem extends FluxStorageItem {

    public FluxStorageTerminalItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public void appendHoverText(ItemStack stack, @NotNull TooltipContext context, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        FluxDeviceConfigComponent config = stack.get(FluxDataComponents.FLUX_CONFIG);
        Long stored = stack.get(FluxDataComponents.STORED_ENERGY);
        long energy = stored != null ? stored : 0;
        long capacity = getCapacity(stack);

        if (config != null) {
            FluxNetwork network = ClientCache.getNetwork(config.networkId());
            if (network.isValid()) {
                tooltip.add(Component.literal(
                        ChatFormatting.BLUE + FluxTranslate.NETWORK_FULL_NAME.get() + ": " +
                        ChatFormatting.RESET + network.getNetworkName()));
            }
            config.limit().ifPresent(limit -> tooltip.add(Component.literal(
                    ChatFormatting.BLUE + FluxTranslate.TRANSFER_LIMIT.get() + ": " +
                    ChatFormatting.RESET + EnergyType.FE.getStorage(limit))));
            config.priority().ifPresent(priority -> tooltip.add(Component.literal(
                    ChatFormatting.BLUE + FluxTranslate.PRIORITY.get() + ": " +
                    ChatFormatting.RESET + priority)));
        }

        double pct = capacity > 0 ? Math.min((double) energy / capacity, 1.0) : 0;
        tooltip.add(Component.literal(
                ChatFormatting.BLUE + FluxTranslate.ENERGY_STORED.get() + ": " +
                ChatFormatting.RESET + EnergyType.FE.getStorage(energy) +
                String.format(" (%.1f%%)", pct * 100)));

        tooltip.add(Component.translatable("tooltip.flux_overdrive.flux_storage_terminal")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(FluxTranslate.FLUX_STORAGE_TOOLTIP.getComponent());
        tooltip.add(FluxTranslate.FLUX_STORAGE_TOOLTIP_2.makeComponent(EnergyType.FE.getStorage(capacity)));
    }

    private static long getCapacity(ItemStack stack) {
        Long capacity = stack.get(FluxOdDataComponents.TOTAL_CAPACITY);
        return capacity != null ? capacity : 0;
    }
}

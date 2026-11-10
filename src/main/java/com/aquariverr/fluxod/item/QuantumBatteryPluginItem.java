package com.aquariverr.fluxod.item;

import com.aquariverr.fluxod.block.QuantumAdvancedCoolingPluginBlock;
import com.aquariverr.fluxod.block.QuantumSolidCoolingPluginBlock;
import com.aquariverr.fluxod.block.QuantumFeedbackPluginBlock;
import com.aquariverr.fluxod.block.QuantumQuenchFusePluginBlock;
import com.aquariverr.fluxod.block.MiniatureUniverseBlock;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackMetric;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStateNetwork;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

public final class QuantumBatteryPluginItem extends BlockItem {

    private final int pluginTier;

    public QuantumBatteryPluginItem(Block block, Properties properties) {
        super(block, properties);
        if (!(block instanceof QuantumBatteryPlugin plugin)) {
            throw new IllegalArgumentException("Quantum battery plugin item requires a plugin block");
        }
        pluginTier = plugin.getPluginTier();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.flux_overdrive.quantum_battery_plugin.tier", pluginTier)
                .withStyle(ChatFormatting.AQUA));
        if (getBlock() instanceof MiniatureUniverseBlock) {
            tooltip.add(Component.translatable("tooltip.flux_overdrive.miniature_universe.ready")
                    .withStyle(ChatFormatting.GRAY));
            var saved = stack.get(FluxOdDataComponents.MINIATURE_UNIVERSE_STATE);
            if (saved != null && saved.copyTag().getBoolean(FactoryStateNetwork.OMITTED_TAG)) {
                tooltip.add(Component.translatable(FactoryState.Status.INVALID_STATE.translationKey())
                        .withStyle(ChatFormatting.RED));
            } else if (context.registries() != null) {
                var state = saved == null ? FactoryState.fresh() : FactoryState.load(saved.copyTag(), context.registries());
                if (state.storage().isEmpty()) tooltip.add(Component.translatable(state.status().translationKey()).withStyle(ChatFormatting.RED));
                else {
                    var storage = state.storage().orElseThrow();
                    tooltip.add(Component.translatable("tooltip.flux_overdrive.miniature_universe.layout",
                            storage.layout().size(), storage.layout().size(), storage.layout().machineCount()).withStyle(ChatFormatting.AQUA));
                    tooltip.add(Component.translatable("tooltip.flux_overdrive.miniature_universe.inventories",
                            storage.inputs().amounts().size(), storage.outputs().amounts().size()).withStyle(ChatFormatting.GRAY));
                    if (storage.batch() != null) tooltip.add(Component.translatable("tooltip.flux_overdrive.miniature_universe.batch",
                            storage.batch().paidTicks(), storage.batch().parallelism()).withStyle(ChatFormatting.YELLOW));
                }
            }
        } else if (getBlock() instanceof QuantumSolidCoolingPluginBlock) {
            tooltip.add(Component.translatable("tooltip.flux_overdrive.quantum_battery_plugin.solid_cooling")
                    .withStyle(ChatFormatting.GRAY));
        } else if (getBlock() instanceof QuantumAdvancedCoolingPluginBlock) {
            tooltip.add(Component.translatable("tooltip.flux_overdrive.quantum_battery_plugin.advanced_cooling")
                    .withStyle(ChatFormatting.GRAY));
        } else if (getBlock() instanceof QuantumQuenchFusePluginBlock) {
            tooltip.add(Component.translatable("tooltip.flux_overdrive.quantum_battery_plugin.quench_fuse_tier_1")
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.flux_overdrive.quantum_battery_plugin.tier_1_ring_only")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (getBlock() instanceof QuantumFeedbackPluginBlock feedback) {
            tooltip.add(Component.translatable(feedback.metric() == QuantumFeedbackMetric.STABILITY
                            ? "tooltip.flux_overdrive.quantum_battery_plugin.stability_feedback"
                            : "tooltip.flux_overdrive.quantum_battery_plugin.temperature_feedback")
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}

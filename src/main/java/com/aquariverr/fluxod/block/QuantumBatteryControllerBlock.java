package com.aquariverr.fluxod.block;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import sonar.fluxnetworks.common.block.FluxDeviceBlock;
import sonar.fluxnetworks.common.device.TileFluxDevice;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class QuantumBatteryControllerBlock extends FluxDeviceBlock {

    public QuantumBatteryControllerBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TileQuantumBatteryController(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (type == RegistryBlockEntityTypes.QUANTUM_BATTERY_CONTROLLER.get()) {
            return TileFluxDevice.getTicker(level);
        }
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() && stack.isEmpty()) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TileQuantumBatteryController controller) {
                if (!controller.canPlayerManage(player)) {
                    player.displayClientMessage(Component.translatable("message.flux_overdrive.access_denied")
                            .withStyle(ChatFormatting.RED), true);
                    return ItemInteractionResult.FAIL;
                }
                controller.validateStructure();
                Component capacity = controller.getCapacity()
                        .<Component>map(value -> Component.literal(controller.getCapacityCompact()))
                        .orElseGet(() -> Component.translatable("message.flux_overdrive.quantum_battery.unlimited"));
                player.displayClientMessage(Component.translatable(
                                "message.flux_overdrive.quantum_battery.status",
                                controller.getStructureTier(), controller.getStoredEnergyCompact(), capacity,
                                controller.getStabilityPercent())
                        .withStyle(controller.isOperational() && controller.isConstraintPowered()
                                && controller.getStabilityBasisPoints() > 0
                                ? ChatFormatting.AQUA : ChatFormatting.YELLOW), false);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (stack.is(sonar.fluxnetworks.register.RegistryItems.FLUX_CONFIGURATOR.get())
                || stack.is(sonar.fluxnetworks.register.RegistryItems.ADMIN_CONFIGURATOR.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof TileQuantumBatteryController controller) {
            controller.openQuantumMenu(serverPlayer);
        }
        return ItemInteractionResult.SUCCESS;
    }
}

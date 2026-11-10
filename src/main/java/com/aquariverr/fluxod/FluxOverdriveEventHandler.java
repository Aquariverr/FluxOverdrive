package com.aquariverr.fluxod;

import com.aquariverr.fluxod.block.entity.TileFluxAutoCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxWirelessCrystal;
import com.aquariverr.fluxod.block.entity.QuantumBatteryQuenchManager;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.multiblock.QuantumBatteryCollision;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.item.FluxLinkToolItem;
import com.aquariverr.fluxod.register.FluxOdDataComponents;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Collection;

@EventBusSubscriber(modid = FluxOverdrive.MODID)
public class FluxOverdriveEventHandler {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        Player player = event.getEntity();
        if (!(stack.getItem() instanceof FluxLinkToolItem)) return;

        if (player.isShiftKeyDown() && (event.getLevel().getBlockEntity(event.getPos()) instanceof TileFluxWirelessCrystal
                || event.getLevel().getBlockEntity(event.getPos()) instanceof TileQuantumBatteryController)) {
            event.setUseBlock(TriState.FALSE);
            return;
        }

        if (FluxLinkToolItem.isBound(stack) || FluxLinkToolItem.isQuantumBatteryBound(stack)) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    @SubscribeEvent
    @SuppressWarnings("resource")
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();

        if (stack.getItem() instanceof FluxLinkToolItem && player.isShiftKeyDown()
                && (FluxLinkToolItem.isBound(stack) || FluxLinkToolItem.isQuantumBatteryBound(stack))) {
            if (!player.level().isClientSide) {
                stack.remove(FluxOdDataComponents.FLUX_LINK_BINDER);
                stack.remove(FluxOdDataComponents.QUANTUM_BATTERY_BINDER);
                player.displayClientMessage(
                        Component.translatable("message.flux_overdrive.link_unbound").withStyle(ChatFormatting.YELLOW),
                        true);
            }
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockPos placedPos = event.getPos();
        if (isQuantumStructureBlock(serverLevel.getBlockState(placedPos).getBlock())) {
            markQuantumControllerDirty(serverLevel, placedPos);
        }
        if (isEntanglementStructureBlock(serverLevel.getBlockState(placedPos).getBlock())) {
            markEntanglementControllerDirty(serverLevel, placedPos);
        }
        Collection<BlockPos> activeCrystals = TileFluxAutoCrystal.getActiveCrystalsNear(
                serverLevel.dimension(), placedPos, (int) Config.autoLinkRadius);

        for (BlockPos crystalPos : activeCrystals) {
            if (!serverLevel.isLoaded(crystalPos)) continue;
            if (serverLevel.getBlockEntity(crystalPos) instanceof TileFluxAutoCrystal crystal) {
                crystal.tryLinkNearbyBlock(placedPos);
            } else {
                TileFluxAutoCrystal.unregisterCrystal(serverLevel.dimension(), crystalPos);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (isQuantumStructureBlock(event.getState().getBlock())) {
            markQuantumControllerDirty(serverLevel, event.getPos());
        }
        if (isEntanglementStructureBlock(event.getState().getBlock())) {
            markEntanglementControllerDirty(serverLevel, event.getPos());
        }
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        for (BlockPos pos : event.getAffectedBlocks()) {
            if (isQuantumStructureBlock(serverLevel.getBlockState(pos).getBlock())) {
                markQuantumControllerDirty(serverLevel, pos);
            }
            if (isEntanglementStructureBlock(serverLevel.getBlockState(pos).getBlock())) {
                markEntanglementControllerDirty(serverLevel, pos);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            TileQuantumBatteryController.clearLevelControllerState(level);
            TileQuantumEntanglementController.clearLevelState(level);
        }
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            TileFluxAutoCrystal.clearActiveCrystals(serverLevel.dimension());
            QuantumBatteryQuenchManager.clear(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        QuantumBatteryQuenchManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        QuantumBatteryCollision.resolve(event.getEntity());
    }

    private static void markQuantumControllerDirty(ServerLevel level, BlockPos changedPos) {
        for (BlockPos controllerPos : TileQuantumBatteryController.getLoadedControllersNear(
                level, changedPos, 15)) {
            int x = changedPos.getX() - controllerPos.getX();
            int y = changedPos.getY() - controllerPos.getY();
            int z = changedPos.getZ() - controllerPos.getZ();
            if (QuantumBatteryStructure.tierAt(x, y, z) > 0
                    && level.getBlockEntity(controllerPos) instanceof TileQuantumBatteryController controller) {
                controller.markStructureDirty();
            }
        }
    }

    private static void markEntanglementControllerDirty(ServerLevel level, BlockPos changedPos) {
        for (BlockPos controllerPos : TileQuantumEntanglementController.getLoadedControllersNear(
                level, changedPos, 4)) {
            if (level.getBlockEntity(controllerPos) instanceof TileQuantumEntanglementController controller
                    && QuantumEntanglementStructure.contains(controllerPos, level.getBlockState(controllerPos), changedPos)) {
                controller.markStructureDirty();
            }
        }
    }

    private static boolean isQuantumStructureBlock(Block block) {
        return block == RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING.get()
                || block == RegistryBlocks.TRANSMON_QUBIT_CHAIN_CASING_GLASS.get()
                || block == RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING.get()
                || block == RegistryBlocks.WEAK_COUPLING_CAVITY_QED_CASING_GLASS.get()
                || block == RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING.get()
                || block == RegistryBlocks.STRONG_COUPLING_CAVITY_QED_CASING_GLASS.get()
                || block instanceof QuantumBatteryPlugin;
    }

    private static boolean isEntanglementStructureBlock(Block block) {
        return block == RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get()
                || block == RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING.get()
                || block == RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA.get()
                || block == RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get()
                || block == RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get()
                || block == RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get()
                || block == RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get();
    }
}
